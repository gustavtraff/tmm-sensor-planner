# Development

## Local GitHub token

Fill in `GH_TOKEN=` in `.env.github` without quotation marks. Git ignores the file.
`.env.example` is an empty template that may be versioned. The token file provides local
authentication for the GitHub API; Git push continues to use SSH.

Check authentication without printing the token or changing GitHub data:

```sh
python3 scripts/check-github-auth.py
```

On macOS, the script can use Homebrew's existing CA certificates if Python lacks
its own certificate file; TLS verification remains enabled.

This verifies the token's identity, not all repository/Issue/PR permissions.
Do not put the token in documentation, command-line arguments or chat.

## Run the core

Install JDK 17 and run sh scripts/test.sh. Alternatively, set JAVA_HOME to a
local JDK. The script compiles Java 8-compatible code and runs tests without libraries.
This JDK is only the core's test environment, not a decision about the ATAK toolchain.

## Pin the SDK before building the Android plugin

Obtain the official ATAK-CIV 5.5.0 SDK, developer app and guide through an approved channel:
https://tak.gov/pages/sdks. Check license/distribution terms. Keep the SDK
outside Git and record its SHA-256 and exact tool versions here after testing.

| Component | Status |
|---|---|
| ATAK-CIV SDK 5.5.0/hash | Missing; not verified |
| Developer app 5.5.0 | Missing; not verified |
| JDK for Android | To be determined from the SDK |
| Gradle / Android Gradle Plugin | To be determined from the SDK |
| Android SDK / build tools | To be determined from the SDK |
| Phone / HV Reports / Data Sync | Actual versions and testing pending |

Do not create Gradle/plugin configuration from guessed APIs. The first Android deliverable
is the SDK's minimal plugin, built and installed in the correct developer app. Compatibility
with the standard ATAK app must not be assumed from a developer build.
Do not put sensitive local settings or test-phone data in Git/debug logs.
