#!/usr/bin/env python3
"""Independent oracle for the proposed image container; no production reader/writer."""
import hashlib
from pathlib import Path
import struct
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[1]
MAGIC = b'TMMIPKG\0'
MAX_DESCRIPTOR = 262256
MAX_PNG = 16777216


def inspect(frame):
    if len(frame) < 20 or len(frame) > 20 + MAX_DESCRIPTOR + MAX_PNG:
        raise ValueError('length')
    magic, version, d, p = struct.unpack_from('>8sIII', frame)
    if magic != MAGIC or version != 1 or not 113 <= d <= MAX_DESCRIPTOR or not 1 <= p <= MAX_PNG:
        raise ValueError('header')
    if len(frame) != 20 + d + p:
        raise ValueError('framing')
    descriptor, png = frame[20:20+d], frame[20+d:]
    # Component coherence only; the production PNG/metadata validators remain required.
    if descriptor[:8] != b'TMMIOFR\0' or struct.unpack_from('>I', descriptor, 8)[0] != 1:
        raise ValueError('descriptor')
    if struct.unpack_from('>I', descriptor, len(descriptor)-4)[0] != zlib.crc32(descriptor[:-4]):
        raise ValueError('descriptor CRC')
    length, width, height = struct.unpack_from('>III', descriptor, 60)
    metadata_length = struct.unpack_from('>I', descriptor, 104)[0]
    if len(descriptor) != 112 + metadata_length or p != length or hashlib.sha256(png).digest() != descriptor[72:104]:
        raise ValueError('pair')
    if png[:8] != b'\x89PNG\r\n\x1a\n' or len(png) < 33 or struct.unpack_from('>II', png, 16) != (width, height):
        raise ValueError('PNG claims')
    return descriptor, png


class ProposalTest(unittest.TestCase):
    def frame(self, n=1):
        return (ROOT / f'test-fixtures/synthetic/image-container-proposal/h{n}.tmmi').read_bytes()

    def test_independent_components(self):
        expected = ((899, '40770c87922632b7362be9cf7129bd48691601f8afa48ed7d067a20c5daf774f'),
                    (909, 'ac276d7371178a8a99a3ac73e832c2f7380201217da032b9fa32ba5938eec5d0'))
        for n in (1, 2):
            self.assertEqual((len(self.frame(n)), hashlib.sha256(self.frame(n)).hexdigest()), expected[n-1])
            descriptor, png = inspect(self.frame(n))
            self.assertEqual(descriptor, (ROOT / f'test-fixtures/synthetic/image-offer-proposal/offer-h{n}.bin').read_bytes())
            self.assertEqual(png, (ROOT / f'images/src/test/resources/image-transfer/h{n}.png').read_bytes())

    def test_header_and_version(self):
        for offset in (0, 7, 8, 11):
            bad = bytearray(self.frame()); bad[offset] ^= 1
            with self.assertRaises(ValueError): inspect(bad)

    def test_lengths_at_and_past_bounds_without_large_allocations(self):
        for offset, values in ((12, (0, 112, 113, MAX_DESCRIPTOR, MAX_DESCRIPTOR+1, 0xffffffff)),
                               (16, (0, 1, MAX_PNG, MAX_PNG+1, 0xffffffff))):
            for value in values:
                bad = bytearray(self.frame()); struct.pack_into('>I', bad, offset, value)
                with self.assertRaises(ValueError): inspect(bad)

    def test_every_truncation_and_trailing_byte(self):
        frame = self.frame()
        for size in range(len(frame)):
            with self.assertRaises(ValueError): inspect(frame[:size])
        with self.assertRaises(ValueError): inspect(frame + b'\0')

    def test_changed_component_and_mixed_pair(self):
        frame = self.frame()
        for offset in (20, 20+200, len(frame)-1):
            bad = bytearray(frame); bad[offset] ^= 1
            with self.assertRaises(ValueError): inspect(bad)
        descriptor, _ = inspect(frame); _, png = inspect(self.frame(2))
        bad = struct.pack('>8sIII', MAGIC, 1, len(descriptor), len(png)) + descriptor + png
        with self.assertRaises(ValueError): inspect(bad)


if __name__ == '__main__':
    unittest.main()
