# Synthetic exact-byte PNG content

These independent golden files implement the H1/H2 examples from
`docs/IMAGE_SHARING_CONTRACT_PROPOSAL.md`. Both are 64×64 eight-bit RGB checkerboards
with only IHDR, IDAT and IEND chunks. They contain no operational image or metadata.

| File | Pixels | Bytes | SHA-256 of complete file |
|---|---|---:|---|
| h1.png | Red/cyan 8-pixel squares; top left cyan | 210 | d6a116beb75c4db929c79c3101f31fddee942fc7b1bbbb607e9cc49ecb8db5a5 |
| h2.png | Green/magenta 8-pixel squares; top left magenta | 220 | 8cc05bbabd5fa56beaf58ed6d0d2dbf4c6e497689d295ec8644eda5c4ebb645a |

Generated independently of the production validator/Android encoder using the
existing synthetic fixture script's PNG chunk helper and Python struct/zlib. The
proposal records the generation algorithm. Tests pin these actual file hashes and
lengths, validate exact received bytes, and check independently expected pixels via
the native Android decoder. Do not regenerate goldens to accommodate a validator
failure. Another compressor may encode the same pixels differently; that is a new
content identity, not permission to accept different bytes under these hashes.

These are content fixtures, not an approved offer/container/registry wire format.
