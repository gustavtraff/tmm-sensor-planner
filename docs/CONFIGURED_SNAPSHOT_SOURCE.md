# Configured TLS snapshot receive trial — #175

Candidate **28 / `0.1.0-configured-receive-trial.1`** extends the explicit
[snapshot receiver](SNAPSHOT_INTAKE.md), not the publisher. It uses an already
configured, enabled and currently connected SSL stream from ATAK's public server
registry. The existing connected-local-trial consent is still required. No server,
credential, subscription or background poll is created by opening the planner.

## Operator flow and prerequisites

1. Obtain the server owner's permission for synthetic testing, the server software
   and version, the HTTPS Mission API port and an accessible dedicated test feed.
   A working CoT stream alone does not prove Mission API permission/compatibility.
2. Connect that SSL profile normally in ATAK. In TMM's local list select **Hämta
   metadata · test**, then explicitly choose that connected profile. Select only
   a server you are authorized to test; the development agent does not contact an
   operational server on your behalf.
3. Confirm the separate Mission API HTTPS port (default **8443**). ATAK's stream
   port, commonly 8089, is not this port. The API must be on the same configured
   DNS/IPv4 host; reverse-proxy paths, IPv6 literals, separate API hosts and Basic
   authentication are not supported by this trial.
4. **Läs tillgängliga feeds** performs a bounded authenticated GET of accessible
   feeds. Choose the intended feed, inspect the displayed canonical service, stream,
   name and mission GUID, then separately select **Hämta till inkorgen**. A missing
   inbox has its existing independent initialization confirmation.
5. Inspect **Mottagna metadata** and separately preview/Save any local application.
   Fetch never overwrites a draft, deletes absent devices, uploads drafts/images,
   creates a subscription or publishes a change. Pictures remain local.

Cancel at either server dialog starts no read. Changing the selected source
revokes the old application grant before selection begins. Invalid ports, a missing
or disconnected profile, a non-default communications provider, or a provider
advertising its own Mission API refuse rather than choosing another source.
The profile is rechecked before accepting the choice and throughout network,
intake and application preparation. Observed loss cancels pending reads and revokes
session application access. A quick disconnect/reconnect between samples or remote
permission changes after a completed GET are not continuously detected. A local
file transaction already begun may finish after UI cancellation; no rollback is
claimed. Restart and Unload still require a fresh Fetch before application.

## SDK and source boundary

The supplied ATAK-CIV **5.6.0.24** SDK was inspected/compiled directly. Public
`TAKServerListener.getInstance().getServers()` and `TAKServer` status/connect-string
getters enumerate at most 32 registry entries; only unique eligible SSL strings
are shown. No username/password getter, profile Bundle dump, certificate export or
private Data Sync action is used. `TAKServer.getURL(true)` uses the stream port in
this SDK; it is deliberately not guessed to be the Mission API endpoint.

Each GET obtains the actual SDK's fresh
`CertificateManager.getSockFactory(false, NetConnectString.fromString(stream), false)`
and passes it to `TakHttpClient` with the selected HTTPS API base. Strict hostname
checking, existing client-certificate selection, no redirect/Basic fallback, fixed
routes, limits, cancellation and cleanup retain the earlier receiver policy.
No certificate/key/password is copied into TMM state or logs. A profile's successful
stream connection does not itself prove authorization to list/read missions.

The caller constructs the canonical lowercase `https://host:explicit-port` base.
Payloads cannot override it. The existing opaque inbox context is
`mission-api-v1|<canonical-HTTPS-service>|<mission-guid>`. The legacy loopback
context remains byte-identical. A renamed feed retains identity; a different
host/port/GUID is a separate source context. Raw selected stream spelling is used
for SDK credential lookup; it is not inserted into the context or persisted as a
credential identity. Same logical endpoint/GUID does not identify an author,
certificate incarnation or complete historical feed. Certificate rollover and
aliases are not a new source-registry protocol.

Membership overview, complete CoT and second overview still must agree. The
returned snapshot must match the selected source context and feed name. The
existing strict parser, 64 KiB response / 64 feed and member caps, 12-second attempt
deadline, all-or-nothing inbox batch and explicit conflict decisions are reused.
No existing metadata codec, inbox frame, local-v2 data contract or image handle
changes. No migration or new stored source/permission registry is introduced.

## Remaining gates

The [reviewed host-update pilot](REVIEWED_HOST_UPDATES.md) and initial host-copy
publication remain restricted to the single isolated loopback lab, airplane-mode
and affected-feed confirmation. **This candidate does not enable operational
publish-back.** No server-side TMM installation is introduced: clients need TMM,
while the server needs a compatible accessible Mission API. That compatibility is
not inferred for the owner's currently unidentified server.

Service-signed standard-host update/receive testing, independent two-client
Data Sync edit/republish delivery, server permissions/reconnection on the actual
combination and owner-run two-phone acceptance remain #173/#171/#164/#25 gates.
A signing success or passing core/UI tests alone is not online product acceptance.
