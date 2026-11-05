# Selected-feed receive prototype — proposed, #164 / #25

**Owner decision, 2026-11-05:** the bounded GET-only isolated-lab prototype is
approved. [Candidate v24](MISSION_READ_LAB.md) implements that scope. A production
HTTP receive adapter, source/inbox writer and new image format are not approved
or activated by this decision.
The owner asked to continue after the native source/image experiments. This
concretizes the next decision and checks actual SDK signatures. Merging this
preparation does not accept a new architecture or source/storage format.

## Owner decision: snapshot-first product semantics — 2026-11-05

After clarification, the owner explicitly approved delivering current snapshots
first. An explicit Fetch reads the selected feed's current data. Preserve each
observed metadata variant in the review inbox; differing metadata never silently
overwrites local edits. Absence from a response never deletes local records.
Changes made and superseded between fetches may be absent from local history.
Complete server history remains a separate investigation, not a promised feature.

This accepts the first receive workflow's semantics, not an unseen storage format
or unverified source mapping. Establish configured-server identity plus stable
mission GUID, permission behavior and coherent metadata before wiring InboxStore.
Keep existing inbox, decision and local-v2 contracts. The bounded lab reader has no
writer; [candidate v25](MISSION_METADATA_LAB.md) verifies actual metadata next.

## Recommended next bounded implementation

1. Implement an explicit, GET-only **isolated-lab prototype**, using the configured
   host TLS client and one explicitly selected server/feed. Publication continues
   through Data Sync's native workflow. No background fetch, subscription mutation,
   POST/PUT/DELETE, new publisher, operational profile or contact substitute.
2. Verify CA/hostname validation, the selected profile's client certificate and
   authorization. Never export credentials into TMM storage/logs or accept a
   payload-provided URL. Bound reads, decompression, redirects, cancellation and
   connection/read timeouts before any production caller is considered.
3. Test stable mission GUID/server scope and actual complete metadata inputs,
   including two feeds with the same UUID, denied access, concurrent updates,
   history/replay and reconnect. Native directory names and callback hints are not
   source authority. Existing authenticated diagnostic GETs are starting evidence,
   not proof of a secure Android adapter.
4. If the server exposes only current snapshots, return for an explicit decision
   on snapshot-import semantics and unseen-history/concurrent-change limits.
   Do not claim complete original-variant ingress or silently adopt latest-wins.
   The first prototype cannot register source contexts or write the inbox/drafts.

The alternative is to continue requesting a version-applicable supported native
Data Sync developer contract. The [native observer findings](SIGNED_V23_SOURCE_VERIFICATION.md)
do not establish one. The [existing investigation](DATA_SYNC_INTEGRATION.md)
identifies pinned official Mission API source, permission constraints and the
separate architecture gate. #164 remains open for runtime evidence and any later
product/snapshot-semantics decision.

Image container/association/receipt formats and their writers remain separately
gated. A later image association must match trusted source plus exact retained
metadata and verified bytes; native image delivery does not attach a local image.
Current metadata/inbox/local-v2/image formats and adoption rules stay unchanged.

## Exact SDK preflight actually run

With the pinned JDK17/toolchain and supplied ATAK-CIV **5.6.0.24** SDK:

```sh
sh scripts/test-ingress-sdk.sh
```

The compiler/reflection check passes the existing ingress/fixture checks plus:

- public static `NetConnectString.fromString(String)` and
  `com.atakmap.comms.http.TakHttpClient.GetHttpClient(String, NetConnectString)`;
- `execute(HttpRequestBase)`, `getUrl(String)` and `shutdown()`;
- response status, `getBody(): InputStream`, length and `close()`;
- the URI constructor of `HttpGet`, without constructing a request or client.

The initial compile fails without Apache HTTP types. Adding the **already installed
Android36 optional `org.apache.http.legacy.jar`** to this private check's compile /
reflection classpath resolves it. No dependency is downloaded and no app Gradle
library configuration changes. A future adapter must separately establish its
actual Android/host classloader configuration; this stub jar is not runtime proof.
The supplied Javadoc describes stored certificates for the factory; no credential
selection, TLS handshake, redirect behavior or network operation is exercised.

The checker invokes no factory, request, response read, host construction, callback
registration or store. It runs no emulator/server and changes no app code/version.
No SDK assembly, domain/image suite, phone, signing upload or operational test is
claimed. Patch whitespace and document links are checked separately. Public core
CI does not have this private SDK and cannot replace this optional local preflight.
