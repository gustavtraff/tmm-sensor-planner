# Proposed one-file image container — independent synthetic goldens

These are reference bytes for owner review, not an approved production format.
See [the exact proposal](../../../docs/IMAGE_CONTAINER_FORMAT_PROPOSAL.md).

| File | Bytes | SHA-256 |
|---|---:|---|
| h1.tmmi | 899 | `40770c87922632b7362be9cf7129bd48691601f8afa48ed7d067a20c5daf774f` |
| h2.tmmi | 909 | `ac276d7371178a8a99a3ac73e832c2f7380201217da032b9fa32ba5938eec5d0` |

Each file is the 20-byte proposed header, the unchanged corresponding approved
`image-offer-proposal/offer-hN.bin` and the exact generated
`images/src/test/resources/image-transfer/hN.png`. The metadata is synthetic;
no image handle, source context, credentials or operational content is added.

Independent reproduction (reference only; do not rewrite goldens to pass a codec):

```python
import struct
frame = struct.pack('>8sIII', b'TMMIPKG\0', 1, len(descriptor), len(png)) + descriptor + png
```

The stdlib-only `python3 scripts/test-image-container-proposal.py` pins the files,
cross-checks exact original components and exercises bounds, magic/version,
every truncation, trailing data, changed components and a mixed pair. Its component
coherence check is not the production metadata or Android PNG decoder.
