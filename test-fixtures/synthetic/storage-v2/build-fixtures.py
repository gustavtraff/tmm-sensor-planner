#!/usr/bin/env python3
"""Reproduce fixed synthetic proposal fixtures only; never reads/writes a user store."""
import base64
import hashlib
import json
import struct
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OFFSETS = {}
STORE = uuid.UUID('00000000-0000-4000-8015-000000000100')
INBOX = uuid.UUID('00000000-0000-4000-8015-000000000101')

def uid(n):
    return uuid.UUID('00000000-0000-4000-8015-%012d' % n)

def integer(n): return struct.pack('>i', n)
def long(n): return struct.pack('>q', n)
def text(s):
    raw = s.encode('utf-8', errors='strict')
    assert len(raw) <= 65536
    return integer(len(raw)) + raw

def instant(t): return long(t[0]) + integer(t[1])
def optional(value, encoder): return b'\0' if value is None else b'\1' + encoder(value)
def number(x, canonical): return struct.pack('>d', 0.0 if canonical and x == 0 else x)
def body(m, canonical=False):
    return (b''.join(text(m[k]) for k in ('label', 'type', 'status', 'owner'))
            + instant(m['created']) + instant(m['updated']) + optional(m['deployed'], instant)
            + text(m['comment']) + number(m['lat'], canonical) + number(m['lon'], canonical)
            + optional(m['bearing'], lambda x: number(x, canonical)))
def mark_body(prefix, start, m):
    cursor = start
    for key in ('label', 'type', 'status', 'owner'):
        OFFSETS[prefix + '.' + key] = cursor + 4
        cursor += len(text(m[key]))
    OFFSETS[prefix + '.createdSeconds'] = cursor
    OFFSETS[prefix + '.updatedNanos'] = cursor + 20
    OFFSETS[prefix + '.deployedFlag'] = cursor + 24
    cursor += 24 + len(optional(m['deployed'], instant)) + len(text(m['comment']))
    OFFSETS[prefix + '.latitude'] = cursor
    OFFSETS[prefix + '.bearingFlag'] = cursor + 16

def metadata(m): return m['uid'].bytes + body(m)
def draft(m, image, revision, canonical=False):
    return body(m, canonical) + optional(image, text) + long(revision)

def state(target, version, tag, m=None, image=None, revision=None, store=STORE):
    return (b'TMMR' + integer(1) + store.bytes + target.bytes + bytes([tag]) + long(version)
            + (draft(m, image, revision, True) if tag == 1 else b''))
def fingerprint(*args, **kwargs): return hashlib.sha256(state(*args, **kwargs)).digest()
def seal(payload): return payload + hashlib.sha256(payload).digest()
def write_frame(name, payload):
    data = seal(payload)
    (ROOT / name).write_text(base64.b64encode(data).decode('ascii') + '\n')
    return {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}

def sample(target, **overrides):
    m = dict(uid=target, label='PIR V2 SYNTHETIC', type='PIR', status='ACTIVE', owner='SYNTHETIC ROLE',
             created=(0, 123), updated=(10, 456), deployed=(5, 6), comment='Local before choice',
             lat=-0.0, lon=1.25, bearing=45.25)
    m.update(overrides)
    return m

A, B, C, D, E = (uid(i) for i in range(201, 206))
BEFORE = sample(A)
INCOMING = sample(A, status='DEPLOYED', updated=(15, 987), deployed=None,
                  comment='Chosen remote åäö 🌲', lat=0.25, lon=1.5, bearing=90.0)
RESULT = sample(A, updated=(20, 123), comment=INCOMING['comment'])
ADD_C = sample(C, comment='Added then deleted', bearing=None)
ADD_D = sample(D, comment='Explicit re-add', bearing=-0.0)
KEEP = sample(A, comment='Other received variant', updated=(30, 999))
NOOP = sample(A, comment='Unselected remote comment', lat=0.0, updated=(40, 999))

def operation(index, action, incoming, before_tag, before_version, before, before_revision,
              image, mask, edit_time, outcome, after_version, after_revision, result):
    raw = bytearray()
    def field(name, value):
        OFFSETS[f'op{index}.{name}'] = OP_START + len(raw)
        raw.extend(value)
    field('id', uid(300 + index).bytes)
    field('action', bytes([action]))
    field('target', incoming['uid'].bytes)
    field('inbox', INBOX.bytes)
    field('receipt', uid(400 + index).bytes)
    field('context', text('SYNTHETIC-CONTEXT-A'))
    field('observed', instant((100 + index, index)))
    field('incoming', metadata(incoming))
    mark_body(f'op{index}.incoming', OFFSETS[f'op{index}.incoming'] + 16, incoming)
    field('beforeTag', bytes([before_tag]))
    field('beforeVersion', long(before_version))
    if before_tag == 1:
        field('beforeRevision', long(before_revision))
        field('before', metadata(before))
    field('fingerprint', fingerprint(incoming['uid'], before_version, before_tag, before, image, before_revision))
    field('mask', integer(mask))
    field('editTime', optional(edit_time, instant))
    field('outcome', bytes([outcome]))
    field('afterVersion', long(after_version))
    field('afterRevision', long(after_revision))
    field('result', metadata(result))
    return bytes(raw)

frame = bytearray(b'TMMD' + integer(2) + STORE.bytes + integer(4))
for i, (target, version, tag, m, image, rev) in enumerate([
    (A, 6, 1, RESULT, 'owned-new', 9),
    (B, 3, 2, None, None, None),
    (C, 2, 2, None, None, None),
    (D, 9, 1, ADD_D, None, 1),
]):
    OFFSETS[f'slot{i}.uid'] = len(frame); frame.extend(target.bytes)
    OFFSETS[f'slot{i}.version'] = len(frame); frame.extend(long(version))
    OFFSETS[f'slot{i}.tag'] = len(frame); frame.append(tag)
    if tag == 1:
        OFFSETS[f'slot{i}.body'] = len(frame)
        mark_body(f'slot{i}', len(frame), m)
        frame.extend(draft(m, image, rev))
OFFSETS['operationCount'] = len(frame)
frame.extend(integer(5))
cases = [
    (2, INCOMING, 1, 4, BEFORE, 7, 'owned-old', 16, (20, 123), 2, 5, 8, RESULT),
    (1, ADD_C, 0, 0, None, None, None, 0, None, 1, 1, 1, ADD_C),
    (1, ADD_D, 2, 8, None, None, None, 0, None, 1, 9, 1, ADD_D),
    (3, KEEP, 1, 6, RESULT, 9, 'owned-new', 0, None, 3, 6, 9, RESULT),
    (2, NOOP, 1, 6, RESULT, 9, 'owned-new', 64, None, 3, 6, 9, RESULT),
]
for i, case in enumerate(cases):
    OP_START = len(frame)
    frame.extend(operation(i, *case))
manifest = {'history-v2.base64': write_frame('history-v2.base64', bytes(frame))}
manifest['empty-v2.base64'] = write_frame('empty-v2.base64', b'TMMD' + integer(2) + STORE.bytes + integer(0) + integer(0))

# Fixed one-record v1 golden input: mechanically retain its record body, not a general migrator.
v1 = base64.b64decode((ROOT.parent / 'storage/v1.base64').read_text().strip(), validate=True)
assert hashlib.sha256(v1).hexdigest() == 'f980a436617a2616fc93b33e5b1491b8718259515a50c3ab77b6c10007f55b14'
assert v1[:12] == b'TMMD' + integer(1) + integer(1)
assert hashlib.sha256(v1[:-32]).digest() == v1[-32:]
migration = b'TMMD' + integer(2) + STORE.bytes + integer(1) + v1[12:28] + long(1) + b'\1' + v1[28:-32] + integer(0)
manifest['migrated-v1-v2.base64'] = write_frame('migrated-v1-v2.base64', migration)
manifest['empty-v1.base64'] = write_frame('empty-v1.base64', b'TMMD' + integer(1) + integer(0))
manifest['source-v1'] = {'bytes': len(v1), 'sha256': hashlib.sha256(v1).hexdigest()}

vectors = [
    ('active', A, 4, 1, BEFORE, 'owned-old', 7, STORE),
    ('positive-zero', A, 4, 1, sample(A, lat=0.0), 'owned-old', 7, STORE),
    ('all-negative-zero', A, 4, 1, sample(A, lat=-0.0, lon=-0.0, bearing=-0.0), 'owned-old', 7, STORE),
    ('all-positive-zero', A, 4, 1, sample(A, lat=0.0, lon=0.0, bearing=0.0), 'owned-old', 7, STORE),
    ('bearing-absent', A, 4, 1, sample(A, bearing=None), 'owned-old', 7, STORE),
    ('image-replaced', A, 4, 1, BEFORE, 'owned-new', 7, STORE),
    ('image-absent', A, 4, 1, BEFORE, None, 7, STORE),
    ('revision', A, 4, 1, BEFORE, 'owned-old', 8, STORE),
    ('state-version', A, 5, 1, BEFORE, 'owned-old', 7, STORE),
    ('store-identity', A, 4, 1, BEFORE, 'owned-old', 7, uid(999)),
    ('device-identity', E, 4, 1, sample(E), 'owned-old', 7, STORE),
    ('nanoseconds', A, 4, 1, sample(A, updated=(10, 457)), 'owned-old', 7, STORE),
    ('text-case', A, 4, 1, sample(A, owner='Synthetic ROLE'), 'owned-old', 7, STORE),
    ('exhausted', A, 2**63-1, 1, BEFORE, 'owned-old', 2**63-1, STORE),
    ('never-seen', E, 0, 0, None, None, None, STORE),
    ('never-other-uid', B, 0, 0, None, None, None, STORE),
    ('deleted', B, 3, 2, None, None, None, STORE),
    ('deleted-version', B, 4, 2, None, None, None, STORE),
]
lines = []
for name, target, version, tag, m, image, rev, store in vectors:
    preimage = state(target, version, tag, m, image, rev, store)
    lines.append(name + '\t' + base64.b64encode(preimage).decode('ascii') + '\t' + hashlib.sha256(preimage).hexdigest())
(ROOT / 'fingerprints.tsv').write_text('\n'.join(lines) + '\n')
(ROOT / 'offsets.properties').write_text('\n'.join(k+'='+str(v) for k,v in OFFSETS.items())+'\n')
(ROOT / 'manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True)+'\n')
print(json.dumps(manifest, indent=2, sort_keys=True))
