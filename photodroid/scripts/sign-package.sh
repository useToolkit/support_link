#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
: "${RUNNER_TEMP:?GitHub runner temporary directory is required}"
: "${ANDROID_HOME:?Android SDK is required}"
signing_dir="$(mktemp -d "$RUNNER_TEMP/photodroid-signing.XXXXXX")"
cleanup() {
  rm -rf -- "$signing_dir" "$RUNNER_TEMP/signing-import"
  unset STORE_PASSWORD KEY_PASSWORD ANDROID_KEYSTORE_BASE64 ANDROID_STORE_PASSWORD ANDROID_KEY_PASSWORD
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mask_value() {
  local value="$1"
  value="${value//'%'/'%25'}"
  value="${value//$'\r'/'%0D'}"
  value="${value//$'\n'/'%0A'}"
  printf '::add-mask::%s\n' "$value"
}
export KEYSTORE_PATH="$signing_dir/release.jks"
provided=0
for secret_name in ANDROID_KEYSTORE_BASE64 ANDROID_STORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -n "${!secret_name:-}" ]]; then provided=$((provided + 1)); fi
done
if [[ "$provided" -eq 4 ]]; then
  printf '%s' "$ANDROID_KEYSTORE_BASE64" | tr -d '[:space:]' | base64 --decode > "$KEYSTORE_PATH"
  export STORE_PASSWORD="$ANDROID_STORE_PASSWORD"
  export KEY_ALIAS="$ANDROID_KEY_ALIAS"
  export KEY_PASSWORD="$ANDROID_KEY_PASSWORD"
  signing_origin='Existing GitHub signing secrets; original signer fingerprint required'
elif [[ "$provided" -eq 0 ]]; then
  python3 "$PHOTODROID_RECIPE_ROOT/photodroid/scripts/import-signing.py" "$signing_dir"
  export STORE_PASSWORD="$(< "$signing_dir/store-password.txt")"
  export KEY_PASSWORD="$(< "$signing_dir/key-password.txt")"
  export KEY_ALIAS="$(< "$signing_dir/key-alias.txt")"
  signing_origin='Original release key reused through an authenticated encrypted run-specific import'
else
  echo 'All four ANDROID_* signing secrets must be supplied together. No new release key will be generated.'
  exit 1
fi
mask_value "$STORE_PASSWORD"
mask_value "$KEY_PASSWORD"
test -s "$KEYSTORE_PATH"
if ! keytool -exportcert -keystore "$KEYSTORE_PATH" -storepass:env STORE_PASSWORD -alias "$KEY_ALIAS" \
  -file "$signing_dir/signer.der" > "$signing_dir/keytool.log" 2>&1; then
  echo 'The supplied keystore certificate could not be read; verify the original credentials and alias.'
  exit 1
fi
actual_signer="$(sha256sum "$signing_dir/signer.der" | cut -d ' ' -f 1)"
if [[ "$actual_signer" != "$REQUIRED_SIGNER_SHA256" ]]; then
  printf 'Signing certificate mismatch. Expected %s; received %s.\n' "$REQUIRED_SIGNER_SHA256" "$actual_signer"
  exit 1
fi
printf 'Original signing certificate verified: %s\n' "$actual_signer"
# Never generate a release key here. Both the supplied keystore and final APK must match.
gradle -p "$SOURCE_DIR/android" :app:assembleRelease --no-daemon --console=plain \
  "-PappVersionName=$APP_VERSION" "-PappVersionCode=$APP_VERSION_CODE"
apk="$SOURCE_DIR/android/app/build/outputs/apk/release/app-release.apk"
test -s "$apk" || { echo 'A signed release APK was not produced.'; exit 1; }
mkdir -p "$DIST_DIR"
install -m 644 "$apk" "$DIST_DIR/$APK_NAME"
python3 "$PHOTODROID_RECIPE_ROOT/photodroid/scripts/verify-apk.py" "$DIST_DIR/$APK_NAME" > "$DIST_DIR/APK-VERIFICATION.txt"
printf '\nSigning origin: %s\nSigning certificate SHA-256: %s\n' "$signing_origin" "$actual_signer" >> "$DIST_DIR/BUILD-INFO.txt"
cd "$DIST_DIR"
sha256sum "$APK_NAME" "$SOURCE_NAME" BUILD-INFO.txt APK-VERIFICATION.txt > SHA256SUMS
cat APK-VERIFICATION.txt
printf 'Verified signed APK: %s\n' "$APK_NAME"
