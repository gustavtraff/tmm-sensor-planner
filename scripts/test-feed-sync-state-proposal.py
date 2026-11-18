#!/usr/bin/env python3
"""Test-only oracle for the proposed feed sync state v1 bytes, not a production store."""
from dataclasses import dataclass, replace
from pathlib import Path
import hashlib
import struct
import sys
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'test-fixtures/synthetic/feed-sync-state-proposal'
MAGIC, VERSION = 0x544d4d53, 1
MAX_RECORDS, MAX_CONTEXT, MAX_LABEL = 256, 4096, 1024
HEADER_FIXED, RECORD_MIN, RECORD_MAX = 57, 36, 116
EMPTY_MIN = HEADER_FIXED + 3 + 32
MAX_FILE = HEADER_FIXED + MAX_CONTEXT + 2 * MAX_LABEL + MAX_RECORDS * RECORD_MAX + 32
CONTEXT_PREFIX = b'mission-api-v1|'
NIL = uuid.UUID(int=0)
IMAGE_UNKNOWN, IMAGE_NONE, IMAGE_DIGEST = 0, 1, 2


def u(suffix, variant='8026'):
    return uuid.UUID(f'00000000-0000-4000-{variant}-{suffix:012x}')


@dataclass(frozen=True)
class Record:
    device: uuid.UUID
    remote: uuid.UUID
    base: object = None          # UUID of the inbox receipt holding base M, or None
    image: object = None         # None = unknown, b'' = base has no image, 32 bytes = digest
    adopt_new: object = None     # UUID of an owned image prepared but not yet confirmed
    adopt_old: object = None     # UUID of the image that was current when adoption started


@dataclass(frozen=True)
class State:
    sync: uuid.UUID
    inbox: uuid.UUID
    following: bool
    context: bytes
    feed: bytes
    stream: bytes
    records: tuple


def require(ok):
    if not ok:
        raise ValueError('Invalid proposed feed sync state')


def text(value):
    return struct.pack('>i', len(value)) + value


def optional(value):
    return b'\0' if value is None else b'\1' + value.bytes


def encode_record(r):
    if r.image is None:
        image = bytes([IMAGE_UNKNOWN])
    elif r.image == b'':
        image = bytes([IMAGE_NONE])
    else:
        image = bytes([IMAGE_DIGEST]) + r.image
    return (r.device.bytes + r.remote.bytes + optional(r.base) + image
            + optional(r.adopt_new) + optional(r.adopt_old))


def frame(s, count=None):
    body = (struct.pack('>II', MAGIC, VERSION) + s.sync.bytes + s.inbox.bytes
            + bytes([1 if s.following else 0]) + text(s.context) + text(s.feed) + text(s.stream)
            + struct.pack('>i', len(s.records) if count is None else count)
            + b''.join(encode_record(r) for r in s.records))
    return body + hashlib.sha256(body).digest()


def parse(data):
    require(EMPTY_MIN <= len(data) <= MAX_FILE)
    body = data[:-32]
    require(hashlib.sha256(body).digest() == data[-32:])
    offset = 0

    def take(size):
        nonlocal offset
        require(offset + size <= len(body))
        chunk = body[offset:offset + size]
        offset += size
        return chunk

    def flag():
        value = take(1)[0]
        require(value in (0, 1))
        return value == 1

    def string(limit):
        size = struct.unpack('>i', take(4))[0]
        require(1 <= size <= limit)
        value = take(size)
        decoded = value.decode('utf-8', errors='strict')
        require(decoded[0] > ' ' and decoded[-1] > ' ')  # Java String.trim() canonical form.
        return value

    def some_uuid():
        value = uuid.UUID(bytes=take(16))
        require(value != NIL)
        return value

    magic, version = struct.unpack('>II', take(8))
    require(magic == MAGIC and version == VERSION)
    sync, inbox = some_uuid(), some_uuid()
    following = flag()
    context = string(MAX_CONTEXT)
    require(context.startswith(CONTEXT_PREFIX) and len(context) > len(CONTEXT_PREFIX))
    feed, stream = string(MAX_LABEL), string(MAX_LABEL)
    count = struct.unpack('>i', take(4))[0]
    require(0 <= count <= MAX_RECORDS)
    records = []
    for _ in range(count):
        device, remote = some_uuid(), some_uuid()
        base = some_uuid() if flag() else None
        kind = take(1)[0]
        require(kind in (IMAGE_UNKNOWN, IMAGE_NONE, IMAGE_DIGEST))
        image = None if kind == IMAGE_UNKNOWN else b'' if kind == IMAGE_NONE else take(32)
        adopt_new = some_uuid() if flag() else None
        adopt_old = some_uuid() if flag() else None
        require(adopt_old is None or (adopt_new is not None and adopt_new != adopt_old))
        records.append(Record(device, remote, base, image, adopt_new, adopt_old))
    require(offset == len(body))
    devices = [r.device.bytes for r in records]
    require(all(a < b for a, b in zip(devices, devices[1:])))  # Strictly ascending unsigned bytes.
    return State(sync, inbox, following, context, feed, stream, tuple(records))


# Reuses the context, inbox, device and receipt identities of the association registry fixtures.
CONTEXT = b'mission-api-v1|tak.example.invalid|00000000-0000-4000-8026-000000000401'
SYNC, INBOX = u(0x801), u(0x502)
DEVICE = uuid.UUID('00000000-0000-4000-8014-000000000003')
DEVICE2 = uuid.UUID('00000000-0000-4000-8014-000000000004')
H1 = bytes.fromhex('d6a116beb75c4db929c79c3101f31fddee942fc7b1bbbb607e9cc49ecb8db5a5')
BASE = State(SYNC, INBOX, True, CONTEXT, b'SYNTHETIC FEED', b'tak.example.invalid:8089:ssl', ())
CURRENT = Record(DEVICE, u(0x601), u(0x601), H1)
CHANGED = Record(DEVICE, u(0x602), u(0x601), H1)
ADOPTING = Record(DEVICE2, u(0x603), u(0x603), b'', u(0x901), u(0x902))
GOLDEN = {
    'empty.bin': BASE,
    'current.bin': replace(BASE, records=(CURRENT,)),
    'changed-adopting.bin': replace(BASE, records=(CHANGED, ADOPTING)),
}
PINNED = {
    'empty.bin': (202, 'f84141041718b7ba0dda745a8dfba3c9d9b8ec50f395557d73980d99b29bc0c2'),
    'current.bin': (286, '7b39f3a363d84bf87f42d9ab053020a33f2dad4a5bd3c5c900baa8c8e5e4012b'),
    'changed-adopting.bin': (370, '64696f806bfbfbf47a048a7abea01150d93f285a9c1b252463080403b9f38760'),
}


class SyncStateProposal(unittest.TestCase):
    def rejects(self, data):
        with self.assertRaises(ValueError):
            parse(data)

    def test_golden_bytes_are_pinned_and_parse(self):
        for name, state in GOLDEN.items():
            data = (FIXTURES / name).read_bytes()
            size, digest = PINNED[name]
            self.assertEqual(len(data), size, name)
            self.assertEqual(hashlib.sha256(data).hexdigest(), digest, name)
            self.assertEqual(data, frame(state), name)
            self.assertEqual(parse(data), state, name)

    def test_shared_identities_match_the_registry_fixtures(self):
        registry = (ROOT / 'test-fixtures/synthetic/image-association-proposal/one.bin').read_bytes()
        self.assertEqual(uuid.UUID(bytes=registry[24:40]), INBOX)
        self.assertEqual(uuid.UUID(bytes=registry[76:92]), DEVICE)
        self.assertEqual(uuid.UUID(bytes=registry[92:108]), CURRENT.base)
        self.assertEqual(registry[128:128 + len(CONTEXT)], CONTEXT)
        self.assertIn(H1, registry)

    def test_framing_rejections(self):
        good = frame(GOLDEN['current.bin'])
        self.rejects(b'')
        self.rejects(good[:-1])
        self.rejects(good + b'\0')
        flipped = bytearray(good)
        flipped[60] ^= 1
        self.rejects(bytes(flipped))
        for magic, version in ((MAGIC + 1, VERSION), (MAGIC, 2), (MAGIC, 0)):
            body = struct.pack('>II', magic, version) + good[8:-32]
            self.rejects(body + hashlib.sha256(body).digest())
        state = GOLDEN['current.bin']
        self.rejects(frame(state, count=2))
        self.rejects(frame(state, count=0))
        self.rejects(frame(BASE, count=-1))
        body = bytearray(good[:-32])
        body[40] = 2  # following flag must be 0 or 1
        self.rejects(bytes(body) + hashlib.sha256(bytes(body)).digest())

    def test_header_rejections(self):
        for change in ({'sync': NIL}, {'inbox': NIL}, {'context': b''}, {'context': b'mission-api-v1|'},
                       {'context': b'other|tak.example.invalid|x'}, {'context': CONTEXT + b' '},
                       {'context': CONTEXT + b'x' * (MAX_CONTEXT - len(CONTEXT) + 1)},
                       {'feed': b''}, {'feed': b' FEED'}, {'feed': b'\xc3\x28'}, {'feed': b'x' * (MAX_LABEL + 1)},
                       {'stream': b''}, {'stream': b'x' * (MAX_LABEL + 1)}):
            self.rejects(frame(replace(BASE, **change)))
        self.assertFalse(parse(frame(replace(BASE, following=False))).following)

    def test_record_rejections(self):
        for change in ({'device': NIL}, {'remote': NIL}, {'base': NIL}, {'adopt_new': NIL},
                       {'adopt_old': u(0x902)},                      # old without new
                       {'adopt_new': u(0x902), 'adopt_old': u(0x902)}):  # new equals old
            self.rejects(frame(replace(BASE, records=(replace(CURRENT, **change),))))
        record = encode_record(CURRENT)
        prefix = frame(BASE)[:-36] + struct.pack('>i', 1)
        for offset, value in ((32, 2), (49, 3), (82, 2), (83, 2)):  # bad flag or image kind bytes
            body = prefix + record[:offset] + bytes([value]) + record[offset + 1:]
            self.rejects(body + hashlib.sha256(body).digest())

    def test_records_are_unique_and_ascending(self):
        self.rejects(frame(replace(BASE, records=(CURRENT, CURRENT))))
        self.rejects(frame(replace(BASE, records=(ADOPTING, CHANGED))))
        self.assertEqual(len(parse(frame(replace(BASE, records=(CHANGED, ADOPTING)))).records), 2)

    def test_bounds_accept_their_exact_limits(self):
        widest = Record(DEVICE, u(0x601), u(0x601), H1, u(0x901), u(0x902))
        self.assertEqual(len(encode_record(widest)), RECORD_MAX)
        self.assertEqual(len(encode_record(Record(DEVICE, u(0x601)))), RECORD_MIN)
        full = tuple(replace(widest, device=uuid.UUID(int=i + 1)) for i in range(MAX_RECORDS))
        edge = replace(BASE, context=CONTEXT + b'x' * (MAX_CONTEXT - len(CONTEXT)),
                       feed=b'x' * MAX_LABEL, stream=b'x' * MAX_LABEL, records=full)
        data = frame(edge)
        self.assertEqual(len(data), MAX_FILE)
        self.assertEqual(len(parse(data).records), MAX_RECORDS)
        self.rejects(frame(replace(edge, records=full + (replace(widest, device=uuid.UUID(int=999)),))))


if __name__ == '__main__':
    if sys.argv[1:] == ['--write']:  # One-time fixture generation; never rewrite to make code pass.
        FIXTURES.mkdir(parents=True, exist_ok=True)
        for name, state in GOLDEN.items():
            data = frame(state)
            (FIXTURES / name).write_bytes(data)
            print(name, len(data), hashlib.sha256(data).hexdigest())
        sys.exit(0)
    unittest.main(verbosity=1)
