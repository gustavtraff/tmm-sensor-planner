# Owner-approved image-offer v1 descriptors — synthetic fixtures

These pin the exact descriptor version approved on 2026-10-30 in
`docs/IMAGE_OFFER_FORMAT_PROPOSAL.md`. They are not a production codec or an
approved container/receipt/registry format. The existing PIR metadata is embedded unchanged:
`test-fixtures/synthetic/publication/pir-v1.xml` (557 bytes). It contains only synthetic
positions/text and no image handle, operational account or source authority.

| File | Bytes | SHA-256 |
|---|---:|---|
| offer-h1.bin | 669 | 7d6c72fed2cf76a5405bec599ec5fea220d03de880999f7051850c9a6918601c |
| offer-h2.bin | 669 | 0c88b527eba372175139150359411ee14e9fa5beba275c20e8d5133964ea9908 |

PNG bytes are **not embedded**. H1/H2 refer to the independent synthetic
`images/src/test/resources/image-transfer/h1.png` (210 bytes, red/cyan) and `h2.png`
(220 bytes, green/magenta), both 64×64. See that directory for pinned PNG hashes.
The two descriptors use associations ending 101/102 and deliveries 201/202 for the
same metadata UID. This does not choose a replacement automatically.

Independent generation from repository root, shown for reproducibility only; do
not rewrite goldens merely to make a future codec pass:

```python
from pathlib import Path
import hashlib, struct, uuid, zlib
m = Path('test-fixtures/synthetic/publication/pir-v1.xml').read_bytes()
u = uuid.UUID('00000000-0000-4000-8014-000000000003').bytes
for n in (1, 2):
    png = Path(f'images/src/test/resources/image-transfer/h{n}.png').read_bytes()
    body = (b'TMMIOFR\0' + struct.pack('>I', 1)
        + uuid.UUID(f'00000000-0000-4000-8026-{100+n:012d}').bytes
        + uuid.UUID(f'00000000-0000-4000-8026-{200+n:012d}').bytes + u
        + struct.pack('>III', len(png), 64, 64) + hashlib.sha256(png).digest()
        + struct.pack('>I', len(m)) + m)
    frame = body + struct.pack('>I', zlib.crc32(body))
    print(n, len(frame), hashlib.sha256(frame).hexdigest())
```

`ImageOfferFormatProposalTest` is a Java test-only inspector in the core suite.
It validates the envelope independently of this Python generator and compares
embedded metadata/content measurements. It is not a production codec, trusted
source mapper, PNG decoder, storage writer or receipt/association implementation.
