#!/usr/bin/env python3
"""Test-only oracle for the proposed association registry bytes, not a production store."""
from dataclasses import dataclass, replace
from pathlib import Path
import hashlib
import struct
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'test-fixtures/synthetic/image-association-proposal'
STAGE = ROOT / 'test-fixtures/synthetic/image-stage-proposal/ready-two'
MAGIC, VERSION = 0x544d4d41, 1
MAX_RECORDS, MAX_CONTEXT, MAX_PNG, MAX_EDGE = 256, 4096, 16777216, 1920
EMPTY_SIZE, RECORD_FIXED = 76, 140
MAX_FILE = EMPTY_SIZE + MAX_RECORDS * (RECORD_FIXED + MAX_CONTEXT)
MIN_SECOND, MAX_SECOND = -31557014167219200, 31556889864403199
NIL = uuid.UUID(int=0)


def u(suffix, variant='8026'):
    return uuid.UUID(f'00000000-0000-4000-{variant}-{suffix:012x}')


@dataclass(frozen=True)
class Record:
    association: uuid.UUID
    delivery: uuid.UUID
    device: uuid.UUID
    receipt: uuid.UUID
    stage: uuid.UUID
    context: bytes
    length: int
    width: int
    height: int
    digest: bytes
    second: int
    nano: int


def require(ok):
    if not ok:
        raise ValueError('Invalid proposed association registry')


def encode_record(r):
    return (r.association.bytes + r.delivery.bytes + r.device.bytes + r.receipt.bytes + r.stage.bytes
            + struct.pack('>I', len(r.context)) + r.context
            + struct.pack('>III', r.length, r.width, r.height) + r.digest
            + struct.pack('>qi', r.second, r.nano))


def frame(registry, inbox, records, count=None):
    body = (struct.pack('>II', MAGIC, VERSION) + registry.bytes + inbox.bytes
            + struct.pack('>i', len(records) if count is None else count)
            + b''.join(encode_record(r) for r in records))
    return body + hashlib.sha256(body).digest()


def parse(data):
    require(EMPTY_SIZE <= len(data) <= MAX_FILE)
    body = data[:-32]
    require(hashlib.sha256(body).digest() == data[-32:])
    magic, version = struct.unpack_from('>II', body, 0)
    require(magic == MAGIC and version == VERSION)
    registry, inbox = uuid.UUID(bytes=body[8:24]), uuid.UUID(bytes=body[24:40])
    require(registry != NIL)
    count = struct.unpack_from('>i', body, 40)[0]
    require(0 <= count <= MAX_RECORDS)
    offset, records = 44, []

    def take(size):
        nonlocal offset
        require(offset + size <= len(body))
        chunk = body[offset:offset + size]
        offset += size
        return chunk

    for _ in range(count):
        ids = [uuid.UUID(bytes=take(16)) for _ in range(5)]
        require(ids[0] != NIL and ids[1] != NIL and ids[4] != NIL)
        size = struct.unpack('>i', take(4))[0]
        require(1 <= size <= MAX_CONTEXT)
        context = take(size)
        text = context.decode('utf-8', errors='strict')
        require(text[0] > ' ' and text[-1] > ' ')  # Java String.trim() canonical form.
        length, width, height = struct.unpack('>III', take(12))
        require(1 <= length <= MAX_PNG and 1 <= width <= MAX_EDGE and 1 <= height <= MAX_EDGE)
        digest = take(32)
        second, nano = struct.unpack('>qi', take(12))
        require(MIN_SECOND <= second <= MAX_SECOND and 0 <= nano <= 999999999)
        records.append(Record(*ids, context, length, width, height, digest, second, nano))
    require(offset == len(body))

    deliveries, stages, bindings = set(), set(), {}
    for r in records:
        delivery = (r.context, r.association, r.delivery)
        require(delivery not in deliveries and r.stage not in stages)
        deliveries.add(delivery)
        stages.add(r.stage)
        binding = (r.device, r.receipt, r.length, r.width, r.height, r.digest)
        require(bindings.setdefault((r.context, r.association), binding) == binding)
    return registry, inbox, records


CONTEXT = b'mission-api-v1|tak.example.invalid|00000000-0000-4000-8026-000000000401'
REGISTRY, INBOX = u(0x501), u(0x502)
DEVICE = uuid.UUID('00000000-0000-4000-8014-000000000003')
H1 = Record(u(0x101), u(0x201), DEVICE, u(0x601), u(0x301), CONTEXT, 210, 64, 64, bytes.fromhex(
    'd6a116beb75c4db929c79c3101f31fddee942fc7b1bbbb607e9cc49ecb8db5a5'), 1796731200, 123456789)
H2 = Record(u(0x102), u(0x202), DEVICE, u(0x601), u(0x302), CONTEXT, 220, 64, 64, bytes.fromhex(
    '8cc05bbabd5fa56beaf58ed6d0d2dbf4c6e497689d295ec8644eda5c4ebb645a'), 1796731260, 0)
GOLDEN = {
    'empty.bin': ([], 76, '1c74b42103c70e20d0e7d04745effa651e9ce459eb96745f61198c94c741ce01'),
    'one.bin': ([H1], 287, '69369fa22d994eeb6ced0235ec47d4ee301ea9524d92c5ffa29ab49e87dfb15e'),
    'two.bin': ([H1, H2], 498, 'af592d15dc83d92828b175d2cd3cc2d8c2fb597e50ae9b9b9364ca39c6ab13fd'),
}


class RegistryProposal(unittest.TestCase):
    def rejects(self, data):
        with self.assertRaises(ValueError):
            parse(data)

    def test_golden_bytes_are_pinned_and_parse(self):
        for name, (records, size, digest) in GOLDEN.items():
            data = (FIXTURES / name).read_bytes()
            self.assertEqual(len(data), size, name)
            self.assertEqual(hashlib.sha256(data).hexdigest(), digest, name)
            self.assertEqual(data, frame(REGISTRY, INBOX, records), name)
            self.assertEqual(parse(data), (REGISTRY, INBOX, records), name)

    def test_records_match_the_approved_offer_and_stage_fixtures(self):
        for record, name in ((H1, 'offer-h1.bin'), (H2, 'offer-h2.bin')):
            offer = (ROOT / 'test-fixtures/synthetic/image-offer-proposal' / name).read_bytes()
            self.assertEqual(uuid.UUID(bytes=offer[12:28]), record.association)
            self.assertEqual(uuid.UUID(bytes=offer[28:44]), record.delivery)
            self.assertEqual(uuid.UUID(bytes=offer[44:60]), record.device)
            self.assertEqual(struct.unpack_from('>III', offer, 60), (record.length, record.width, record.height))
            self.assertEqual(offer[72:104], record.digest)
            staged = STAGE / f'ready-{record.stage}'
            self.assertEqual((staged / 'offer.bin').read_bytes(), offer)
            self.assertEqual(hashlib.sha256((staged / 'image.png').read_bytes()).digest(), record.digest)

    def test_framing_rejections(self):
        good = frame(REGISTRY, INBOX, [H1])
        self.rejects(b'')
        self.rejects(good[:-1])
        self.rejects(good + b'\0')
        flipped = bytearray(good)
        flipped[50] ^= 1
        self.rejects(bytes(flipped))
        for magic, version in ((MAGIC + 1, VERSION), (MAGIC, 2), (MAGIC, 0)):
            body = struct.pack('>II', magic, version) + good[8:-32]
            self.rejects(body + hashlib.sha256(body).digest())
        self.rejects(frame(NIL, INBOX, []))
        self.rejects(frame(REGISTRY, INBOX, [H1], count=2))
        self.rejects(frame(REGISTRY, INBOX, [H1], count=0))
        self.rejects(frame(REGISTRY, INBOX, [], count=-1))

    def test_field_rejections(self):
        for change in ({'association': NIL}, {'delivery': NIL}, {'stage': NIL}, {'length': 0},
                       {'length': MAX_PNG + 1}, {'width': 0}, {'width': MAX_EDGE + 1}, {'height': 0},
                       {'height': MAX_EDGE + 1}, {'nano': -1}, {'nano': 1000000000},
                       {'second': MAX_SECOND + 1}, {'second': MIN_SECOND - 1}, {'context': b''},
                       {'context': b' padded'}, {'context': b'padded\n'}, {'context': b'\xc3\x28'},
                       {'context': b'x' * (MAX_CONTEXT + 1)}):
            self.rejects(frame(REGISTRY, INBOX, [replace(H1, **change)]))

    def test_bounds_accept_their_exact_limits(self):
        edge = replace(H1, context=b'x' * MAX_CONTEXT, length=MAX_PNG, width=MAX_EDGE, height=MAX_EDGE, nano=999999999)
        self.assertEqual(parse(frame(REGISTRY, INBOX, [edge]))[2], [edge])
        full = [replace(edge, association=u(i + 1), delivery=u(i + 1), stage=u(i + 1)) for i in range(MAX_RECORDS)]
        data = frame(REGISTRY, INBOX, full)
        self.assertEqual(len(data), MAX_FILE)
        self.assertEqual(len(parse(data)[2]), MAX_RECORDS)
        self.rejects(frame(REGISTRY, INBOX, full + [replace(edge, association=u(999), delivery=u(999), stage=u(999))]))

    def test_identity_rules(self):
        second_delivery = replace(H1, delivery=u(0x203), stage=u(0x303))
        self.assertEqual(len(parse(frame(REGISTRY, INBOX, [H1, second_delivery]))[2]), 2)
        self.rejects(frame(REGISTRY, INBOX, [H1, replace(H1, stage=u(0x303))]))  # Same delivery recorded twice.
        self.rejects(frame(REGISTRY, INBOX, [H1, replace(H2, stage=H1.stage)]))  # One stage entry backs one record.
        for change in ({'device': u(0x700, '8014')}, {'receipt': u(0x602)}, {'length': 211}, {'width': 65},
                       {'height': 65}, {'digest': bytes(32)}):
            self.rejects(frame(REGISTRY, INBOX, [H1, replace(second_delivery, **change)]))

    def test_same_association_in_another_context_is_separate_provenance(self):
        other = replace(H1, context=CONTEXT + b'-other', receipt=u(0x602), stage=u(0x303), digest=H2.digest, length=220)
        self.assertEqual(len(parse(frame(REGISTRY, INBOX, [H1, other]))[2]), 2)


if __name__ == '__main__':
    unittest.main(verbosity=1)
