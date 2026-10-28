# Signed v20 offline companion-panel regression — #7

Date: 2026-10-29, Europe/Stockholm. This extends the earlier
[standard-client companion smoke test](STANDARD_CLIENT_VERIFICATION.md#companion-baseline-and-tmm-comparison)
to the exact service-signed v20 APK from the
[v20 artifact record](SIGNED_V20_UPGRADE_VERIFICATION.md#returned-artifact).
It is not the newer source fix/version 21 candidate and does not complete A08.

## Actual environment and inputs

One retained synthetic API 35 arm64 guest ran standard ATAK-CIV 5.6.0.25 with the
known EGL fallback. Server/database/relay containers remained stopped. Airplane
mode/Wi-Fi/data state and absence of task reverses were checked; the disconnected
socket backend provides no normal NAT route. Existing stored profiles refer only
to the stopped loopback lab. No phone, operational profile or real data was used.

| Plugin | Inspected target |
|---|---|
| TMM | versionCode 20, `0.1.0-versioned-storage.1`; same service-signed APK already recorded |
| Data Sync | 3.7.4 (`58746181`) [5.6.0], already installed |
| HV Reports | 1.1.9 [5.5.0], package `com.atakmap.android.hvreports.plugin` |
| TAK Voice | 2.1.0 (`20251122`) [5.6.0], supplied XAPK base plus 19 configuration splits |

The HV APK and Voice base hashes match the earlier
[intake record](SIGNING_AND_COEXISTENCE.md#companion-package-intake-follow-up).
Current HV signature verification and all 20 Voice APK verifications pass; the Voice
splits share one signing certificate. This does not independently establish the
APKPure-sourced package's publisher provenance. No APKPure installer, package
relabeling, re-signing, host/API patch or validation bypass was used.

Normal native package Sync refreshed the local inventory from 9 to 11 products.
TMM was explicitly unloaded and a cold host process established the inactive-TMM
baseline before the other two companions were loaded. Data Sync was already
loaded. Both HV and Voice subsequently showed actual **Loaded** through their
ordinary confirmations; a compatibility dialog alone was not counted as loading.
The historical SDK-host missing-class failures were not reproduced by these
standard-host loading observations.

Voice's optional Android overlay screen showed **Not allowed** and was left so.
Its accessibility-service activation was cancelled. Background/widget/volume-key
PTT and audio were not enabled or tested; the emulator has no host audio backend.

## Bounded native comparison

The TMM dataset was the valid empty v2 frame created in the
[storage-activation/error trial](SIGNED_V20_STORAGE_FAILURES.md). It is intentionally
small; this does not test a populated operational collection.

| Observation | Cold process, TMM inactive | TMM active | After normal TMM unload |
|---|---|---|---|
| Plugin inventory | Data Sync/HV/Voice Loaded; TMM Not loaded | All four Loaded | Other three Loaded; TMM Not loaded |
| HV Reports | No reports in current feed; filter/sort controls visible | Same empty report panel and six filter states | Same empty panel and six states |
| TAK Voice | Missions, No missions to display | Same normal Missions view | Same normal Missions view |
| Data Sync | Disconnected cached synthetic feed, 8 items | Same cached feed/count; no live synchronization claim | Same cached view/count |
| TMM | No active TMM tool workflow | Existing initialized zero-record collection opens without reinitialization | Both TMM tool entries absent |

Normal close/reopen navigation was used between panels. A previous version 7
stacked-panel anomaly is not claimed fixed or exhaustively reproduced by this run.
The TMM frame remains exactly its original 64 bytes after companion loading/panel
use. No report was created/saved, no explicit send/share/PTT selected and no voice
session or network delivery trial occurred. Queueing/network attempts were not
forensically audited; disconnected panels cannot establish transmission privacy.

The active Tools menu contains one visible entry each for Data Sync, HV Reports,
TAK Voice and both TMM tools. After normal TMM unload, the three companion entries
remain once each and both TMM entries are absent. This is observed menu inventory,
not exhaustive hidden component enumeration.

The final TMM file remains byte-identical to the original 64-byte initialized
frame. The captured final crash buffer has zero Java FATAL EXCEPTION entries;
caught errors and unobserved native failures are not excluded. With the host
stopped, the task-installed HV/Voice packages were removed. The guest was shut
down and its temporary deadline guard cancelled. Data Sync, the valid synthetic
TMM collection and private evidence were retained. No test guest or server stack
is left running by this slice.

## Scope

No production code, format, permission declaration or signing input changed.
Raw UI, hashes, package/static results and storage evidence stay outside Git.
No domain/image suite, SDK build, signing submission, release or physical test ran
for this panel regression. Required documentation checks are separate.

Still open: actual synthetic report fields/delivery, Voice session/audio/background
PTT, connected mixed-companion privacy, full setting audits, populated-data stress,
physical 5.6.0.12 and returned-v21 verification. The separate
[connected v20/Data Sync trial](CONNECTED_SIGNED20_VERIFICATION.md) does not cover
those Voice/HV workflows or authorize operational sharing.
