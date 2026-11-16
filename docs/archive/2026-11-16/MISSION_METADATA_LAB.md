# Full metadata snapshot lab reader v25 — #169 / #164

Candidate **25 / `0.1.0-metadata-read-lab.1`** retains the package/API and all v24
loopback/offline/default-provider restrictions. It adds a fixed-GUID GET to the
existing synthetic metadata mission, selected in **TMM CoT-labb → Mission API ·
läs labböversikt → Metadata · fasta fyra TMM-enheter**. Review and choose **Läs
metadata i labb**. Cancel creates no request. This is a lab experiment, not a
production Fetch action or a source/inbox/draft writer.

## Endpoint and actual data validation

The only new URI is the fixed lab
`/Marti/api/missions/guid/93ad6bb9-e00e-4d08-bb2f-887867f3950d/cot` on the existing
strict configured-host TLS client. The independently read overview binds that GUID
to `tmm-ds25-selected-20261023` for this setup; no dynamic name/GUID registration or
arbitrary URL is accepted. The UI label is not an authenticated response field.

Pinned official [MissionApi](https://github.com/TAK-Product-Center/Server/blob/5187abd46d827d37cfc5708805eced197a837e49/src/takserver-core/takserver-war/src/main/java/com/bbn/marti/sync/api/MissionApi.java)
defines both name and GUID CoT GETs as **latest** events with MISSION_READ and group
checks. The GUID route validates the requested mission before retrieving cached
CoT. This source inspection is not a native role-denial or cross-feed variant test.
It does not prove complete original history or an atomic mission snapshot.

The supplied **5.8-RELEASE-92 server binary** was also inspected locally. Its
MissionServiceDefaultImpl name/GUID getCachedCot overloads pass the membership UID
set, optional path and caller group vector to getCachedCotImpl; neither forwards
the mission name/GUID into the cache lookup. That implementation obtains latest
accessible CoT by UID. Therefore a mission GUID establishes the selected membership
lookup, **not a feed-exclusive historical payload version**. Do not infer that
identical UIDs in two missions have independent current payloads. A controlled
cross-feed runtime experiment and explicit source semantics remain necessary
before InboxStore wiring. Raw vendor bytecode remains private.

The shared input boundary reads through EOF with the existing 64 KiB cap, strict
UTF-8, active/deadline checks and caller-owned stream lifetime. The XML reader
requires an events root and direct event children, at most 64 events, unique
nonempty UIDs, depth 16, 64 attributes per element and 4096-character attribute
values. This deliberately narrow lab limit is not a new production wire profile.
XML 1.0/UTF-8 declarations only; DTD/entities, namespaces, comments, CDATA and extra
processing instructions fail. No external resource resolution is allowed.

Only the four existing PUB18 canaries are reconstructed in bounded memory. Each
passes actual SDK CotEvent parsing and the existing PublicationCot/core codec:
all 15 metadata attributes, outer/inner UUID, type and rounded point agreement.
The result counts validated canaries and exact matches to the independent base
fixtures. Ordinary events are counted, not imported. Valid changed metadata can
validate while no longer matching the base; the count is not a conflict policy.
A successful control requires all four distinct fixed canaries. No identifier,
metadata, body, raw provider error or credential is logged or saved by the probe.

TLS, HTTP and header/body failures use fixed stage codes for diagnosis; no provider
exception text/cause is exposed. All v24 cancellation/deadline/cleanup guards remain.
No retry, subscription change, publication, source registration, inbox, local draft,
image, receipt or new persistent format is introduced.

## Runtime and checks — 2026-11-05

The supplied SDK developer host is ATAK-CIV **5.6.0.24** on one isolated API35
headless emulator; it is not the standard signed client. Existing 8089/8443
loopback tunnels, guest non-loopback IPv4/IPv6 drops, radios-off/airplane mode and
the private synthetic client profile are retained. No operational phone or feed
is accessed. Raw responses, UI and test certificates remain private outside Git.

The earlier metadata-overview failure is **not reproduced**: two explicit reads
return 9 UIDs / 0 resources. Replaying its captured response also passes the old
parser on API26/35. No root cause or correction of that historical failure is
claimed. Fixed-stage errors improve diagnosis if it recurs.

The final v25 APK reads **9 CoT events / 4 validated TMM canaries / 4 exact base
matches** through actual host-managed TLS and SDK parsing. Independent strict-TLS
name and GUID endpoint reads return identical 11,631-byte XML, and independent
overview counts agree. This establishes actual complete canary field validation,
not production source registration or two-client inbox application.

Focused public reader tests cover extraction, framing, duplicates, limits,
declarations, entity/namespace rejection, cancellation, input ownership and private
error suppression on API26/35. Private replay checks use ignored synthetic server
captures and are not included in the source archive or public tests.

The installed development APK SHA-256 is
`196c544fc58b2c14f5209436a948cebd9a6ab2f236fce9ebde248dcf44658c86`.
Full helper/image tests report **389 cases, zero failures/errors, one skip**; app
checks pass three unit cases, debug/release assembly and lint. SDK ingress
compile/reflection and standalone fixture APK boundaries pass. These are distinct
from the native controls below.

A separate private loopback TLS fixture uses only synthetic lab certificates and
requires the existing client certificate. Its matching-host certificate first
passes an independent strict-TLS control. Initial fixture setup errors (startup
availability and wrong hostname/CN) are excluded, not recorded as plugin failures.
On the final installed APK:

| Fixture control | Observed native outcome |
|---|---|
| HTTP 403 | Fixed HEADERS rejection; one fixture request |
| HTTP 302 | Fixed HEADERS rejection; one request, no redirect follow |
| Declared 65,537-byte body | Fixed HEADERS rejection |
| Malformed UTF-8 body | Fixed BODY rejection |
| Trusted CA, wrong hostname | HTTP-stage refusal; TLS completed at fixture but zero HTTP requests |
| Untrusted CA | HTTP-stage refusal; rejected handshake and zero HTTP requests |
| Continuous slow body | 12-second deadline result; fixture observes client close |
| Explicit Android Cancel during slow body | Dialog closes; no result reappears after waiting beyond the deadline; fixture observes client close |

API tunnel removal produces a fixed HTTP-stage failure. Restoring only that tunnel
and performing a new explicit read again returns 9 events / 4 valid / 4 exact
canaries. This is API-route loss/recovery with the CoT stream still connected,
not a complete native Data Sync subscription reconnect or automatic retry test.

The fixture also compares the presented client-certificate fingerprint against
known private lab certificates and records only a fixed label. The SDK presents
**client-a**, proving selected-profile credential use in this setup without copying
certificate/key bytes into plugin state.

Independent real-server permission controls use both client-a and client-b: each
reads the existing permitted mission overview/CoT with HTTP200, then receives HTTP403
from a newly created invite-only mission's GUID CoT route. This is an actual server
permission refusal with positive controls, not a native Android role-switch test.
The creation parameter creatorUid does not itself prove authenticated ownership;
even the creating client was refused and no owner-role grant is inferred.

A separate attempted two-mission same-UUID fixture does **not** pass its setup:
API membership calls return 200, but the observed missions contain no UIDs and
CoT is empty. Both a new stream-injected UUID and an existing cached canary fail
the setup's positive condition. API status alone cannot diagnose this.

Inspection of retained messaging logs after shutdown then identifies two distinct
controls: new-UID API brokering is dropped because its empty creator/client has no
mission subscription; the existing PUB18 canary's attempts to enter the two new
missions are explicitly rejected by StrictUidMissionMemebershipFilter. Both logged
refusals match the exact tested canary and newly created mission GUIDs. Inspection
of the supplied filter bytecode confirms it refuses an existing mission UID when
the requested mission is outside that UID's current membership. Thus the lab's
strict-membership policy prevents these cross-mission additions. This is evidence
of that policy's refusal, **not** successful independent payload variants in two
feeds, a correctly subscribed new-UID control, or proof of another server's policy. Only the three newly created synthetic
missions are soft-deleted using the lab administrator; subsequent GETs return410.
This does not erase event history or claim deletion of every synthetic copy.
Existing selected/image feeds and their payloads are not edited.

Client-side fixture rejection and independent permission controls do not establish
native permission switching, negative header enforcement or cross-feed source scope.
The configured profile's credential bytes are never copied into plugin code/state.

Standard
service-signed v25 loading, native mission-role switching, cross-feed same-UUID/source
binding and production Fetch-to-inbox integration remain separate gates. The
[snapshot-first decision](MISSION_READ_PROPOSAL.md#owner-decision-snapshot-first-product-semantics--2026-11-05)
does not convert these test counts into source authority or a delivery receipt.

## Handoff and remaining delivery

All four actual returned canary envelopes also pass the independent existing raw-
XML publication checker, separately from SDK matching. On the final installed APK,
the original image overview again returns 0 UIDs / 2 resources, and metadata overview
returns 9 UIDs / 0 resources after the negative tests. No Java fatal entry is found.
The complete before/after private TMM file inventory and hashes are identical.
That fresh profile contains only the empty draft-lock file; it is not acceptance
of migration or preservation of a populated signed-v24 dataset.

The task-owned SDK emulator, dedicated ADB and TLS fixture are stopped. Java lab
roles receive TERM; PostgreSQL reports a clean fast shutdown before the container
wrappers stop. Other containers and retained standard-client profiles are untouched.
Private watchdogs are cancelled only after owned-resource shutdown is verified.

Next delivery is [#171](https://github.com/gustavtraff/tmm-sensor-planner/issues/171):
connect an evidenced selected-feed snapshot source to the existing inbox/review/
application components, then prove the two-client edit-and-publish-back workflow.
Complete the correctly subscribed new-UID membership control, record the strict-
membership policy and resolve exact source meaning first; do not mistake this count-only lab UI for the product Fetch workflow.
Keep image sharing #26 behind that first useful metadata delivery.
