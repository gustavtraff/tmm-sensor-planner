#!/usr/bin/env python3
"""Test-only filesystem-layout/integrity oracle, not a production stage store or full metadata decoder."""
from pathlib import Path
import base64
import hashlib
import json
import os
import shutil
import stat
import struct
import tempfile
import unittest
import uuid
import xml.etree.ElementTree as ET
import zlib

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'test-fixtures/synthetic/image-stage-proposal'
MAX_PNG = 16777216
MAX_OFFER = 262256
BUDGET = 256 * 1024 * 1024
FIELDS = ('schemaVersion deviceUid displayIdUtf8 type status ownerUtf8 createdAt updatedAt '
          'deployedAt commentUtf8 latitude longitude bearingDeg bearingReference imageIncluded').split()


def require(ok):
    if not ok:
        raise ValueError('Invalid proposed staging layout or integrity')


def entries(path, limit):
    result = []
    with os.scandir(path) as items:
        for item in items:
            result.append(Path(item.path))
            require(len(result) <= limit)
    return result


def regular(path, cap):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_size <= cap)
    return info.st_size


def pair_integrity(offer, png):
    require(113 <= len(offer) <= MAX_OFFER and offer[:8] == b'TMMIOFR\0')
    require(struct.unpack_from('>I', offer, 8)[0] == 1)
    require(uuid.UUID(bytes=offer[12:28]).int and uuid.UUID(bytes=offer[28:44]).int)
    length, width, height = struct.unpack_from('>III', offer, 60)
    n = struct.unpack_from('>I', offer, 104)[0]
    require(1 <= n <= 262144 and len(offer) == 112 + n)
    require(zlib.crc32(offer[:-4]) == struct.unpack('>I', offer[-4:])[0])
    raw = offer[108:-4]
    require(b'<!DOCTYPE' not in raw and b'<!ENTITY' not in raw)
    metadata = ET.fromstring(raw.decode('utf-8', errors='strict'))
    require(metadata.tag == 'tmmPublication' and set(metadata.attrib) == set(FIELDS) and not list(metadata))
    canonical = ("<?xml version='1.0' encoding='utf-8'?>\n<tmmPublication"
                 + ''.join(' '+k+'="'+metadata.attrib[k]+'"' for k in FIELDS) + ' />\n').encode('utf-8')
    require(canonical == raw and metadata.attrib['schemaVersion'] == '1')
    require(metadata.attrib['imageIncluded'] == 'false' and metadata.attrib['bearingReference'] == 'true-north')
    require(uuid.UUID(bytes=offer[44:60]) == uuid.UUID(metadata.attrib['deviceUid']))
    for name in ['displayIdUtf8', 'ownerUtf8', 'commentUtf8']:
        text = base64.b64decode(metadata.attrib[name], validate=True)
        require(len(text) <= 65536 and base64.b64encode(text).decode() == metadata.attrib[name])
        text.decode('utf-8', errors='strict')
    require(1 <= length <= MAX_PNG and len(png) == length)
    require(1 <= width <= 1920 and 1 <= height <= 1920 and hashlib.sha256(png).digest() == offer[72:104])
    require(png[:8] == b'\x89PNG\r\n\x1a\n')
    offset, data, seen_header, ended = 8, bytearray(), False, False
    channels = None
    while offset + 12 <= len(png):
        size = struct.unpack_from('>I', png, offset)[0]
        require(size <= len(png)-offset-12)
        kind, payload = png[offset+4:offset+8], png[offset+8:offset+8+size]
        require(zlib.crc32(kind+payload) == struct.unpack_from('>I', png, offset+8+size)[0])
        if not seen_header:
            require(kind == b'IHDR' and size == 13)
            w, h, depth, color, compression, filters, interlace = struct.unpack('>IIBBBBB', payload)
            require((w,h) == (width,height) and depth == 8 and color in (2,6))
            require((compression,filters,interlace) == (0,0,0)); channels = 3 if color == 2 else 4; seen_header = True
        elif kind == b'IDAT':
            data.extend(payload)
        elif kind == b'IEND':
            require(size == 0 and data and offset+12 == len(png)); ended = True; break
        else:
            raise ValueError('Unsupported PNG chunk in synthetic staging oracle')
        offset += size+12
    require(ended)
    stride = 1+width*channels
    decoder = zlib.decompressobj()
    decoded = decoder.decompress(bytes(data), stride*height+1)
    require(len(decoded) == stride*height and decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail)
    require(all(decoded[i] <= 4 for i in range(0,len(decoded),stride)))
    # Full scalar/domain metadata and actual Android decode remain required in the production boundary.


def inspect(root):
    require(stat.S_ISDIR(root.lstat().st_mode))
    items = entries(root, 18)
    by_name = {p.name:p for p in items}
    require('format' in by_name and 'stage.lock' in by_name)
    require(regular(by_name['format'],8) == 8 and by_name['format'].read_bytes() == b'TMMISTG1')
    require(regular(by_name['stage.lock'],0) == 0)
    total, ready, pending, ids = 8, 0, 0, set()
    for name, path in by_name.items():
        if name in ('format','stage.lock'):
            continue
        prefix = 'ready-' if name.startswith('ready-') else 'pending-' if name.startswith('pending-') else None
        require(prefix is not None and stat.S_ISDIR(path.lstat().st_mode))
        local_id = name[len(prefix):]
        parsed = uuid.UUID(local_id)
        require(str(parsed) == local_id and parsed.int and local_id not in ids); ids.add(local_id)
        files = {p.name:p for p in entries(path,2)}
        require(set(files) <= {'offer.bin','image.png'})
        for filename, p in files.items():
            total += regular(p,MAX_OFFER if filename == 'offer.bin' else MAX_PNG)
            require(total <= BUDGET)
        if prefix == 'ready-':
            require(set(files) == {'offer.bin','image.png'})
            pair_integrity(files['offer.bin'].read_bytes(),files['image.png'].read_bytes()); ready += 1
        else:
            pending += 1
    require(ready+pending <= 16)
    return {'ready':ready,'pending':pending,'logical_bytes':total,
            'recovery_required':bool(pending),'history_unknown':True}


class StageLayoutProposalTest(unittest.TestCase):
    def copy(self, name):
        tmp = tempfile.TemporaryDirectory(); self.addCleanup(tmp.cleanup)
        target = Path(tmp.name)/'stage'; shutil.copytree(FIXTURES/name,target)
        return target

    def test_independent_layout_cases_and_no_mutation(self):
        for name, counts in {'empty':(0,0,8),'ready-one':(1,0,887),'ready-two':(2,0,1776),
                             'pending-partial':(1,1,997),'pending-complete':(0,1,897)}.items():
            root = FIXTURES/name
            before = {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
            result = inspect(root)
            self.assertEqual((result['ready'],result['pending'],result['logical_bytes']),counts)
            self.assertEqual(result['recovery_required'],bool(counts[1])); self.assertTrue(result['history_unknown'])
            self.assertEqual(before,{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()})

    def test_missing_bad_marker_or_unknown_content_fail_without_reset(self):
        for mutation in ('missing','version','extra','badlock'):
            root = self.copy('empty')
            if mutation == 'missing': (root/'format').unlink()
            if mutation == 'version': (root/'format').write_bytes(b'TMMISTG2')
            if mutation == 'extra': (root/'foreign').write_bytes(b'synthetic')
            if mutation == 'badlock': (root/'stage.lock').write_bytes(b'x')
            with self.assertRaises((ValueError,FileNotFoundError)): inspect(root)
            self.assertTrue(root.exists())

    def test_ready_corruption_mismatch_and_extras_are_not_ignored(self):
        for mutation in ('missing','crc','different-png','extra-file','partial'):
            root = self.copy('ready-one'); record = next(root.glob('ready-*'))
            if mutation == 'missing': (record/'image.png').unlink()
            if mutation == 'crc':
                b = bytearray((record/'offer.bin').read_bytes()); b[-1] ^= 1; (record/'offer.bin').write_bytes(b)
            if mutation == 'different-png': shutil.copyfile(ROOT/'images/src/test/resources/image-transfer/h2.png',record/'image.png')
            if mutation == 'extra-file': (record/'unexpected').write_bytes(b'x')
            if mutation == 'partial': (record/'image.png').write_bytes(b'partial')
            with self.assertRaises(ValueError): inspect(root)

    def test_links_and_duplicate_local_identity_fail_closed(self):
        root = self.copy('ready-one'); record = next(root.glob('ready-*'))
        (record/'image.png').unlink(); (record/'image.png').symlink_to(ROOT/'images/src/test/resources/image-transfer/h1.png')
        with self.assertRaises(ValueError): inspect(root)
        root = self.copy('ready-one'); record = next(root.glob('ready-*'))
        (root/('pending-'+record.name[6:])).mkdir()
        with self.assertRaises(ValueError): inspect(root)
        root = self.copy('empty'); outside = root.parent/'outside'; outside.mkdir(); (root/'ready-00000000-0000-4000-8026-000000000399').symlink_to(outside,target_is_directory=True)
        with self.assertRaises(ValueError): inspect(root)

    def test_actual_logical_quota_boundary_includes_partial_files(self):
        root = self.copy('empty')
        for i in range(16):
            record = root/f'pending-00000000-0000-4000-8026-{400+i:012d}';record.mkdir()
            with (record/'image.png').open('wb') as f: f.truncate(MAX_PNG-(8 if i == 15 else 0))
        result = inspect(root); self.assertEqual(result['logical_bytes'],BUDGET); self.assertTrue(result['recovery_required'])
        last = record/'image.png'
        with last.open('r+b') as f:f.truncate(MAX_PNG-7)
        with self.assertRaises(ValueError): inspect(root)

    def test_pending_count_cap_and_whole_record_loss_limit(self):
        root = self.copy('empty')
        for i in range(17):(root/f'pending-00000000-0000-4000-8026-{500+i:012d}').mkdir()
        with self.assertRaises(ValueError):inspect(root)
        root = self.copy('ready-one');shutil.rmtree(next(root.glob('ready-*')))
        result = inspect(root);self.assertEqual(result['ready'],0);self.assertTrue(result['history_unknown'])
        # Enumeration cannot prove a whole removed record never existed; no receipt/history claim.


if __name__ == '__main__':
    unittest.main()
