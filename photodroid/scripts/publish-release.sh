#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
: "${GITHUB_SHA:?The recipe commit is required}"
: "${GITHUB_REPOSITORY:?The destination repository is required}"
: "${GH_TOKEN:?A contents-write GitHub token is required}"
python3 "$PHOTODROID_RECIPE_ROOT/photodroid/scripts/check-release.py"
cd "$DIST_DIR"
sha256sum --check --strict SHA256SUMS
# Create-only: no upload --clobber, delete, edit, force-tag or replacement of an existing release.
# Keep this application's release separate from the repository's existing latest release.
gh release create "$RELEASE_TAG" \
  "$APK_NAME" "$SOURCE_NAME" SHA256SUMS BUILD-INFO.txt APK-VERIFICATION.txt \
  --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" \
  --title "포토드로이드 $APP_VERSION (한국어)" --prerelease --latest=false \
  --notes-file "$PHOTODROID_RECIPE_ROOT/photodroid/RELEASE-NOTES.ko.md"
