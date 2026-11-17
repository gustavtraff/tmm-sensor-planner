# Signed v20 native storage activation/error gates — #7

Date: 2026-10-29, Europe/Stockholm. This follows the
[v20 upgrade trial](EMULATOR_RECOVERY_V20.md) and
[connected-private trial](CONNECTED_SIGNED20_VERIFICATION.md).
The same service-signed versionCode 20 APK was used in standard ATAK-CIV 5.6.0.25
on one API 35 arm64 test guest. Exact hashes are in the linked artifact records.
The later source fix #121/version 21 candidate is **not** covered by these results.

## Isolation and test setup

Only the retained synthetic recipient guest ran, using emulator 37.2.12.0,
`-gpu swangle`, 2 GiB RAM/two cores and the documented EGL marker. No Docker/server
was started for this slice. Airplane mode was enabled, Wi-Fi/data disabled, routes
settled empty and all task reverses were removed. Its stored profiles point only
to the stopped loopback lab. No phone or operational data was used.

The planner directory initially had no committed snapshot. UI choices used the
normal TMM toolbar entry; no product initialization/migration API was invoked by
the harness. ADB injected only the later controlled invalid fixtures while ATAK
was stopped; all native reads and error choices were real app actions.

## Missing storage and explicit initialization

- Opening TMM shows **Starta utan lokal historik?** with the warning that absence
  cannot distinguish a new install from lost data. No empty snapshot is created
  before consent.
- **Avbryt**, then reopening and Android Back, each leave `drafts.bin` absent.
- A new opening and explicit **Starta utan historik** opens a zero-record list.
  The actual 64-byte frame decodes through the existing codec to zero active/deleted
  slots and zero decisions, with a generated store identity.
- A cold host restart reopens that empty collection without a repeated
  initialization prompt. Its exact bytes/identity remain unchanged.

The valid initialized frame SHA-256 is
`91f95f9b65ee0ea72c896f5c04e12b1d37a8cfb1d3b2664454a83f3eff866609`.
This is a synthetic empty-dataset test, not recovery of lost user history.

## Native rejection controls

| Controlled fixture | Actual observed result |
|---|---|
| Flip one checksum byte of the valid 64-byte frame | Generic storage-unavailable error; no initialize/upgrade choice. Exact invalid bytes remain unchanged after opening and repeated opening. |
| Change version 2→3 in the header and recompute a valid SHA-256 | Same safe error; unsupported-version bytes remain exact. This does not define or implement a version 3 format. |
| Valid frame, correct app ownership, mode 000 | Same safe error. Bytes and mode 000 remain unchanged; no chmod/empty fallback is performed by the product. |
| Snapshot path replaced by a relative symlink to the valid owned test frame | Same safe error. The symlink is retained and its target remains exact, rather than being followed or replaced. |

The native message states that data could not be read and that nothing was
restored or initialized. It exposes no draft, filesystem path or fixture contents.
These checks supplement existing JVM/Robolectric path/format tests; they are actual
API 35 runtime observations, not proof of every filesystem/power-loss behavior.

For fixture cleanup only, the stopped-host harness removes its known symlink,
restores the same original empty file with mode 600 and relaunches the host.
The native zero-record list opens and the file again matches its initial 64 bytes.
This is manual test-fixture restoration, **not** an automatic product repair,
backup restore recommendation, downgrade or user-data recovery feature.

The captured storage-slice crash buffer contains zero Java FATAL EXCEPTION entries.
The same guest was subsequently used for the separately recorded
[offline companion comparison](SIGNED_V20_COMPANION_REGRESSION.md), then shut down.
This does not turn the fixture edits into a supported user-recovery procedure.

## Scope

No production source, schema, golden fixture, Android permission declaration or signing configuration
changed. Raw generated frames, UI XML and guest diagnostics remain ignored in
private storage. No core/image suite, SDK/build/signing upload, network transport
or physical test ran for this evidence slice. Documentation links/whitespace are
checked separately for the PR.

Still open: physical/OEM behavior, nonempty corruption handling, full disk or
power loss, capacity/performance, arbitrary lifecycle timing, and a returned signed
version 21 regression for #121. This does not close the complete compatibility gate
or authorize operational upgrades/sharing.
