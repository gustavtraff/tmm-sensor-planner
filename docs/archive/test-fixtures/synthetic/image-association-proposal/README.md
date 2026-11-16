# Proposed image association registry v1 — synthetic fixtures

These pin the bytes proposed in `docs/IMAGE_ASSOCIATION_REGISTRY_PROPOSAL.md`.
The owner approved this format on 2026-11-08. They contain no real positions, images, accounts
or servers; the context uses the reserved `example.invalid` domain.

| File | Bytes | SHA-256 | Content |
|---|---:|---|---|
| empty.bin | 76 | 1c74b42103c70e20d0e7d04745effa651e9ce459eb96745f61198c94c741ce01 | Initialized, no records |
| one.bin | 287 | 69369fa22d994eeb6ced0235ec47d4ee301ea9524d92c5ffa29ab49e87dfb15e | H1 |
| two.bin | 498 | af592d15dc83d92828b175d2cd3cc2d8c2fb597e50ae9b9b9364ca39c6ab13fd | H1 and H2, two alternatives for one device |

Records match `image-offer-proposal/offer-h1.bin` and `offer-h2.bin`, and the entries
ending 301 and 302 in `image-stage-proposal/ready-two`. Do not rewrite these files
merely to make a future implementation pass. `scripts/test-image-association-proposal.py`
holds the independent Python encoder and the strict inspector.
