# Private SDK runner and required merge status

The owner selected the self-hosted option for #221 on 2026-11-16. The proposed
machine is a Windows Dell with 32 GB RAM. Use a dedicated x64 Linux VM for the
existing shell-based toolchain; installation on that machine has not been done.
This procedure is incomplete until the activation checklist passes.

## Trust and execution model

The repository is public. Do not automatically run arbitrary PR code on a home
computer containing private inputs. GitHub describes the risks of [untrusted
workflow code](https://docs.github.com/en/actions/reference/security/secure-use)
and [self-hosted machines](https://docs.github.com/en/actions/concepts/runners/self-hosted-runners).

`Private SDK build` is manually dispatched **from main** for an open, same-repo PR.
A GitHub-hosted job resolves its exact head and posts pending `sdk` status on that
commit. The owner reviews that SHA, including workflow/build-script changes, before
approving the protected `sdk-build` environment. Fork PRs are refused; a reviewed
internal branch is needed. A new PR head needs a new run and approval. No
`pull_request_target`, implicit fork checkout or automatic self-hosted PR trigger
is used.

The isolated worker receives a read-only token with nonpersistent checkout.
Status-write permission is confined to the GitHub-hosted coordinator jobs; the
worker does not need a personal access token. A separate result job posts failure
unless the build succeeded; a cancelled/skipped/failed worker is never a pass.
Cancellation before result publication may leave pending, which still blocks.

Manual dispatch is a scheduling/approval step, not a local-build attestation:
GitHub's required `sdk` status must block merge until the exact commit passed.
Without the required rule this workflow alone does not enforce anything.

## Provision the Windows host

1. Enable virtualization and create a dedicated supported Ubuntu x64 VM, initially
   with 4 vCPU, 8–12 GB RAM and enough disk for Android/Gradle caches. Use a dedicated
   account, no shared Windows folders/clipboard and no personal files, signing
   keys, TAK accounts, server profiles or operational data in the guest.
2. Install JDK 17, Git, Python 3 and the public Android command-line tools. Install
   platform `android-36`, build-tools `35.0.0` and the SDK-required tools from
   [DEVELOPMENT.md](DEVELOPMENT.md). Obtain the approved ATAK 5.6.0.24 SDK separately;
   verify the documented fingerprints and keep it outside runner workspaces/Git.
3. Set the runner service environment's `JAVA_HOME`, `ANDROID_HOME` and
   `TMM_ATAK_SDK_DIR` to those external installations. Verify permissions with the
   service account; never paste its environment or SDK contents into a public log.
4. Create environment `sdk-build` with the owner as required reviewer; turn off
   self-review prevention for this owner-dispatched flow. Restrict deployment
   branches to `main`. Verify these protections **before registering/starting the
   runner**; an environment name in YAML does not create reviewer protection.
5. In repository Settings → Actions → Runners, follow GitHub's current Linux x64
   registration instructions. Register only for this repository with the extra
   label `atak-sdk-5-6`; the workflow also requires `linux` and `x64`.
   Use `config.sh --ephemeral` when registering and a clean VM snapshot per approved job. Never commit or
   quote the registration token; do not store GitHub/SSH credentials in the VM.
6. Restrict the VM's network to the runner service and approved build dependency
   endpoints, excluding the home/operational LAN. Preload permitted dependencies
   and restore the clean snapshot after each job. Guest isolation protects the
   Windows host; PR approval remains necessary to protect SDK inputs from code
   executed inside the guest. A persistent general-purpose home runner is not the
   intended setup.

Raw SDK check output is redirected into a mode-600 temporary file and deleted by
`scripts/check-sdk-ci.sh`. The public result contains fixed pass/fail text only.
There are no SDK/APK/log uploads or shared caches. Reproduce failures privately
with `sh scripts/check-sdk.sh`; diagnostics are deliberately absent from Actions.
Reviewing all executed code is still required; the wrapper is not a sandbox.

## Activation and enforcement checklist

- [ ] Review and merge the workflow/procedure PR; it cannot dispatch from main
  before it exists there. Existing `core` and `images` protections stay enabled.
- [ ] Configure environment review, isolated VM, external SDK and ephemeral runner.
- [ ] Dispatch from main for a harmless same-repo test PR; approve only the
  displayed SHA, verify actual `app` assembly/lint/unit tests/APK check succeed,
  and inspect public logs for private content.
- [ ] Add required status `sdk`, bound to GitHub Actions, to the main ruleset,
  keeping strict up-to-date checks, `core`, `images` and no bypass. This repository
  setting needs explicit owner approval under AGENTS.md.
- [ ] Open a disposable PR with a deliberate Java compilation error in an `app/`
  source outside the images module. Verify `sdk` fails and GitHub refuses merge.
- [ ] Push a fixed new head; verify the previous success cannot satisfy it, run
  the exact new head, and confirm merge is allowed only after all checks pass.
- [ ] Check rejected/cancelled approval, unavailable runner and fork PR behavior:
  none may obtain a successful exact-head `sdk` result. Close the test PRs and
  record actual results in #221 before marking it complete.

This compiles the CIV debug plugin against the private SDK; it does not establish
signing, physical-device behavior or operational/server compatibility.
