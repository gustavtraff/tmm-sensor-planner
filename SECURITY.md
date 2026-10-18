# Security and information handling

## Reporting sensitive findings

Do not disclose vulnerabilities, secrets or operational information in public
Issues, PRs, screenshots, Actions logs or attachments. Report to repository owner
[@gustavtraff](https://github.com/gustavtraff) through an agreed private channel.
GitHub private vulnerability reporting was **disabled** at the 2026-10-18 audit;
a formal reporting channel is still pending. Do not assume a private-report button
or support mailbox exists. A public request for a private contact must omit details.
Enabling a private route is tracked in [#55](https://github.com/gustavtraff/tmm-sensor-planner/issues/55).
No response-time or supported-release commitment has been established.

## Data boundary

The public repository, history, fixtures, Issues/PRs, CI logs and release artifacts
may contain only synthetic examples. Never include real sensor positions,
operational images/sketches, account/server details, SDK/server archives, tokens,
certificates or keys. Do not log draft fields, images, provider URIs or raw sensitive
exceptions. Keep test artifacts and SDK inputs outside Git.

The current planner is an offline prototype. A guarded synthetic CoT lab and an
explicit saved-metadata sharing lab exist; the old statement that the entire
application has no transport is no longer accurate. Do not use lab actions on an
operational network. Drafts must not be transmitted before explicit review and
confirmation; production connected acceptance remains open.

Local images are separate owned copies; `imageRef` is not a sharing path. The
implemented [image contract](docs/LOCAL_IMAGES.md) removes embedded metadata,
applies orientation, bounds input/normalized sizes and preserves original files.
This does not erase sensitive information visible in the pixels. Image sharing
and recipient acknowledgment remain separate work.

## CI and private inputs

Public PR CI runs without the private ATAK SDK or signing credentials, using
read-only workflow tokens. Keep arbitrary fork code away from persistent private
SDK runners, local credentials and operational files. Approval to run public CI
does not authorize signing, source upload, a release or data sharing.

Secret scanning and push protection were enabled at the 2026-10-18 audit. These
checks do not establish that every sensitive item in history/artifacts was found.
Full SDK/phone/network/security evidence and release ownership remain gates in
[repository governance](docs/REPOSITORY_GOVERNANCE.md).

If a leak occurs: stop further publication, revoke affected secrets, identify the
exposed history/artifacts and let the responsible human decide the cleanup.
Do not copy the exposed values into a public incident report.
