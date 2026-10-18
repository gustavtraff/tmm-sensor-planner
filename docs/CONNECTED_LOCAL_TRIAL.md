# Connected local privacy trial (#4 / #7)

This procedure tests private drafts while ATAK is connected to an isolated test
server. It does not enable production synchronization or authorize operational
data sharing. Use synthetic records and a separate test environment only.

## Candidate behavior

Offline operation remains the default. When connected, open **TMM Sensor Planner**
and choose **Starta lokalt test** after reviewing the warning. **Avbryt** leaves
connected local planning locked. The list identifies the test mode. Consent lasts
only until plugin **Unload** or ATAK process restart; it is not stored in settings.
Connected startup does not restore private markers until consent is given again.
Saved drafts and images remain local and retain their existing format.

TMM's metadata-sharing action is hidden and its CoT lab is blocked throughout the
trial, even after returning offline. This restriction concerns TMM's tools; ordinary
ATAK self-location, contacts and other plugins can still send their own traffic.
There is no automatic publication of TMM drafts when connectivity changes.

## Controlled test procedure

1. Record candidate hash, signing certificate, host full builds, Android version and
   server build. A developer-host test is distinct from a service-signed standard
   client test. Never replace a phone's standard ATAK with the developer host.
2. Use two empty synthetic clients and a server with no operational connections.
   Keep private profiles, certificates, raw captures and screenshots outside Git.
   Verify Android reports an actually connected network, rather than merely clearing
   the airplane-mode flag. Preserve any additional guest/host egress restrictions.
3. Connect both clients and an independent decrypted CoT observer. Send an ordinary
   synthetic positive control; verify it reaches the server and recipient before
   interpreting an absence of private drafts as meaningful.
4. Cancel connected-trial consent; verify no local list/layer is loaded. Then opt in.
   Create synthetic drafts with unique ID, owner and comment canaries. Edit and move
   one, attach a synthetic image, reopen it, and delete another. Verify no sharing
   action is available and the separate CoT lab refuses to start.
5. Check the recipient's map/object list, server records and decrypted traffic for
   draft UUIDs, labels, comments, positions and image names/handles. Do not rely on
   encrypted packet contents or an empty database alone. Bracket observations with
   positive controls before and after; record the actual observation interval.
6. Restart ATAK and Unload/reload TMM. Verify consent resets, connected startup does
   not render drafts, opting in restores saved data, and no duplicate layer appears.
   Check that returning offline within an opted-in session does not re-enable labs.
7. Stop only the task's lab resources. Keep #4/#7 acceptance outstanding for any
   missing runtime, standard-binary, physical-client or coexistence checks.

## Verification status

Candidate: `0.1.0-connected-local-trial.1`, versionCode 9. Automated session-policy,
image/coordinator and SDK build/lint checks have been run during development.
The final combined-artifact results and connected runtime evidence must be recorded
before this candidate is recommended for a phone trial. The older signed versionCode
7 artifact does not contain this change. No operational-network trial is approved.
