# Synthetic data

Core tests use mathematical coordinates near the equator and the date line;
they do not represent sensor locations. Fixtures include Java test values, the fixed
storage-v1 sample and the
[inbox format 1 fixture](inbox/README.md). The latter was independently encoded
and explicitly approved with A–C on 2026-10-25; production codec tests retain its
exact bytes. Applying inbox metadata to local drafts remains deferred. Never put
real positions or images here.


[Local v2 proposal fixtures](storage-v2/README.md) independently pin the proposed
private frame, canonical reviewed-state fingerprint and expected v1 migration
results. They include active/deleted UUIDs and decision history; no production
migration, writer or reset is implemented or implied.
