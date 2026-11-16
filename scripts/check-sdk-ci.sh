#!/usr/bin/env sh
# Raw SDK diagnostics must never reach public Actions logs or artifacts.
set -eu
umask 077
sdk_ci_log=$(mktemp "${TMPDIR:-/tmp}/tmm-sdk-ci.XXXXXX")
trap 'rm -f "$sdk_ci_log"' EXIT
trap 'exit 1' HUP INT TERM
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if sh "$repo_dir/scripts/check-sdk.sh" >"$sdk_ci_log" 2>&1; then
    echo 'Whole-plugin SDK build, lint, unit tests and APK check passed.'
else
    echo 'SDK check failed. Reproduce in the approved private environment; raw diagnostics are not published.' >&2
    exit 1
fi
