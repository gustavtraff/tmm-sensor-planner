# Release procedure

[Current candidate, compatibility evidence and remaining gates](NEXT_STEPS.md)
are maintained in one place. [Earlier version matrices and signing/phone records](archive/2026-11-16/RELEASES.md)
retain the exact historical combinations.

Target ATAK-CIV API 5.6.0; record full SDK/host/companion/Android/server builds
independently. API alignment is not installation or coexistence evidence.
Only tag reviewed commits on `main`; use semantic plugin versions independently
of schema versions. Include a changelog, installation steps and known limitations.

Before a release, verify approved SDK/distribution terms, exact source/signing
inputs, APK integrity, clean install/upgrade, rollback without draft loss,
information review and relevant [A01–A10 gates](TESTING.md). The owner decides
release and operational sharing. Signing a trial candidate does not approve either.

Follow [the signing procedure](SIGNING_SOURCE.md). Never publish SDK inputs,
certificates, operational data or private raw evidence. Local deletion must not be
presented as remote withdrawal or secure erasure.
