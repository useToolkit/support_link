# XodaFace public APK release

This new branch contains a standalone Android build of the user's independent XodaFace app (face size and midface controls), based on their earlier private project. The SODA commercial app, proprietary models, shaders, and assets are not distributed here. The previously selected Xoda icon was preserved.

The `xoda-public-release.yml` GitHub Actions workflow publishes an installable APK on the **public releases page**, including an SHA256 checksum file. GitHub automatically signs it with a new test certificate, so previous Xoda installs with a different signature must be uninstalled first. The signature may differ again on future rebuilds.

No claim is made of exact SODA rendering parity or verification on a real Galaxy S26.
