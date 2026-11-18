# Proposed feed sync state v1 — synthetic fixtures

These pin the bytes proposed in `docs/FEED_SYNC_STATE_FORMAT.md` (#236). The owner
approved this format (S1–S4) on 2026-11-18. They contain no real positions, images, accounts or servers; the
context uses the reserved `example.invalid` domain.

| File | Bytes | SHA-256 | Content |
|---|---:|---|---|
| empty.bin | 202 | f84141041718b7ba0dda745a8dfba3c9d9b8ec50f395557d73980d99b29bc0c2 | Following one feed, no devices yet |
| current.bin | 286 | 7b39f3a363d84bf87f42d9ab053020a33f2dad4a5bd3c5c900baa8c8e5e4012b | One device in step with the feed, image H1 |
| changed-adopting.bin | 370 | 64696f806bfbfbf47a048a7abea01150d93f285a9c1b252463080403b9f38760 | The same device changed on the feed since its base, and a second device in the middle of an image adoption |

Context, inbox ID, device ending 0003, receipt ending 601 and digest H1 match
`image-association-proposal/one.bin`. Do not rewrite these files merely to make a
future implementation pass. `scripts/test-feed-sync-state-proposal.py` holds the
independent Python encoder and the strict inspector.
