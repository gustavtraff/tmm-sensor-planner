# Development loading probe

This independently written plugin registers one ATAK toolbar item. Selecting it
shows the Swedish message **Lokal prototyp**. Stopping it removes the item.
It creates no sensor objects, map markers, storage or sharing transport.

Target plugin API: ATAK-CIV 5.6.0.CIV. Build against the inspected local 5.6.0.24 SDK
using the instructions in ../docs/DEVELOPMENT.md. No SDK binaries or signing files
are versioned. The APK has no Android permissions.

The debug APK and Android lint have passed locally. Actual loading, toolbar behavior
and unloading in the SDK developer app remain untested. The Android Debug signature
is not a production signing/distribution result.
