#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
patch_file="$PHOTODROID_RECIPE_ROOT/photodroid/ko.patch"
test -s "$patch_file" || { echo 'Missing photodroid/ko.patch'; exit 1; }
test ! -e "$SOURCE_DIR" || { echo 'source/ already exists; use a fresh checkout.'; exit 1; }
git -c init.defaultBranch=upstream init --quiet "$SOURCE_DIR"
git -C "$SOURCE_DIR" remote add origin "$UPSTREAM_URL"
git -C "$SOURCE_DIR" -c core.hooksPath=/dev/null fetch --quiet --no-tags --depth=1 origin "$UPSTREAM_COMMIT"
git -C "$SOURCE_DIR" -c core.hooksPath=/dev/null checkout --quiet --detach FETCH_HEAD
test "$(git -C "$SOURCE_DIR" rev-parse HEAD)" = "$UPSTREAM_COMMIT"
git -C "$SOURCE_DIR" apply --check "$patch_file"
git -C "$SOURCE_DIR" apply "$patch_file"
git -C "$SOURCE_DIR" diff --check

font_tmp="$(mktemp -d "${RUNNER_TEMP:-/tmp}/photodroid-font.XXXXXX")"
trap 'rm -rf -- "$font_tmp"' EXIT
font_base="https://raw.githubusercontent.com/google/fonts/$FONT_COMMIT/ofl/nanumgothic"
curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 --retry 3 "$font_base/NanumGothic-Regular.ttf" -o "$font_tmp/NanumGothic-Regular.ttf"
curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 --retry 3 "$font_base/OFL.txt" -o "$font_tmp/OFL-NanumGothic.txt"
printf '%s  %s\n' "$FONT_SHA256" "$font_tmp/NanumGothic-Regular.ttf" "$FONT_LICENSE_SHA256" "$font_tmp/OFL-NanumGothic.txt" | sha256sum --check --strict
mkdir -p "$SOURCE_DIR/assets/fonts"
install -m 644 "$font_tmp/NanumGothic-Regular.ttf" "$SOURCE_DIR/assets/fonts/NanumGothic-Regular.ttf"
install -m 644 "$font_tmp/OFL-NanumGothic.txt" "$SOURCE_DIR/assets/fonts/OFL-NanumGothic.txt"
# Keep the original notices with the installed binary as well as in the source ZIP.
license_dest="$SOURCE_DIR/android/app/src/main/assets/licenses"
mkdir -p "$license_dest"
for notice in LICENSE-APACHE LICENSE-MIT NOTICE ATTRIBUTION.md \
  assets/fonts/OFL-NanumGothic.txt assets/fonts/OFL-Inter.txt assets/fonts/OFL-JetBrainsMono.txt \
  assets/icons/LICENSE-lucide.txt assets/icons/LICENSE-symmetry-axes.txt assets/icons/LICENSE-noun-magnetic-lasso.txt \
  assets/dict/LICENSE-SCOWL.txt crates/ui-egui/src/LICENSE-solarized.txt crates/ui-egui/src/i18n/LICENSE-translations.txt; do
  install -m 644 "$SOURCE_DIR/$notice" "$license_dest/$(basename -- "$notice")"
done
install -m 644 "$SOURCE_DIR/assets/app-icon/LICENSE.txt" "$license_dest/LICENSE-app-icon.txt"
python3 - <<'PY'
import os, pathlib, re
root = pathlib.Path(os.environ['SOURCE_DIR'])
gradle = (root / 'android/app/build.gradle').read_text()
expected = os.environ['APP_ID']
assert re.search(r"applicationId\s+['\"]" + re.escape(expected) + r"['\"]", gradle), 'The patch must set the Korean applicationId.'
for name in ['LICENSE-APACHE', 'LICENSE-MIT', 'NOTICE', 'ATTRIBUTION.md', 'assets/fonts/OFL-NanumGothic.txt']:
    assert (root / name).is_file(), f'Missing license/notice: {name}'
print('Pinned source, Korean patch, font hashes and notices verified.')
PY
# The upstream .github directory stays inside source/. It is never copied to the builder root.
