#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
: "${RUNNER_TEMP:?GitHub runner temporary directory is required}"
: "${ANDROID_HOME:?Android SDK is required}"
signing_dir="$(mktemp -d "$RUNNER_TEMP/photodroid-signing.XXXXXX")"
cleanup() {
  rm -rf -- "$signing_dir"
  unset STORE_PASSWORD KEY_PASSWORD ANDROID_KEYSTORE_BASE64 ANDROID_STORE_PASSWORD ANDROID_KEY_PASSWORD
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
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
  signing_origin='Existing GitHub signing secrets'
elif [[ "$provided" -eq 0 ]]; then
  certificate="$PHOTODROID_RECIPE_ROOT/photodroid/backup-public.pem"
  test -s "$certificate" || { echo 'Initial signing requires photodroid/backup-public.pem.'; exit 1; }
  openssl x509 -in "$certificate" -noout > /dev/null
  openssl x509 -in "$certificate" -pubkey -noout | openssl rsa -pubin -noout > /dev/null 2>&1
  export STORE_PASSWORD="$(openssl rand -hex 32)"
  export KEY_PASSWORD="$STORE_PASSWORD"
  export KEY_ALIAS='photodroid-release'
  # GitHub masks generated passwords too. No environment dump, shell trace or plaintext artifact.
  printf '::add-mask::%s\n' "$STORE_PASSWORD"
  keytool -genkeypair -noprompt -storetype JKS -keystore "$KEYSTORE_PATH" \
    -storepass:env STORE_PASSWORD -keypass:env KEY_PASSWORD -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 \
    -dname 'CN=PhotoDroid Korean' > "$signing_dir/keytool.log" 2>&1
  printf '%s' "$STORE_PASSWORD" > "$signing_dir/store-password.txt"
  printf '%s' "$KEY_PASSWORD" > "$signing_dir/key-password.txt"
  printf '%s' "$KEY_ALIAS" > "$signing_dir/key-alias.txt"
  tar -C "$signing_dir" -cf "$signing_dir/signing-backup.tar" release.jks store-password.txt key-password.txt key-alias.txt
  backup_dir="$RUNNER_TEMP/photodroid-signing-backup"
  mkdir -p "$backup_dir"
  openssl cms -encrypt -binary -aes256 -outform DER \
    -in "$signing_dir/signing-backup.tar" -out "$backup_dir/PhotoDroid-signing-backup.cms" \
    -recip "$certificate" -keyopt rsa_padding_mode:oaep -keyopt rsa_oaep_md:sha256
  signing_origin='Initial release key; encrypted backup saved as a separate Actions artifact'
  {
    printf 'PhotoDroid initial signing-key backup\nVersion: %s\n' "$APP_VERSION"
    printf 'Encryption: CMS DER, AES-256-CBC, RSA-OAEP/SHA-256.\n'
    printf 'Download this artifact before it expires and keep it privately with the matching backup private key.\n'
    printf 'Decrypt and import the SAME release.jks and passwords as the four ANDROID_* GitHub secrets before any update.\n'
    printf 'Changing the signing key prevents an installed application from receiving an in-place update.\n'
    printf 'Recipient certificate SHA-256: '
    openssl x509 -in "$certificate" -outform DER | sha256sum | cut -d ' ' -f 1
  } > "$backup_dir/BACKUP-INFO.txt"
else
  echo 'All four ANDROID_* signing secrets must be supplied together. Refusing to create a different key from partial secrets.'
  exit 1
fi
test -s "$KEYSTORE_PATH"
# Only this step receives signing secrets. Native compilation and tests ran without them.
gradle -p "$SOURCE_DIR/android" :app:assembleRelease --no-daemon --console=plain \
  "-PappVersionName=$APP_VERSION" "-PappVersionCode=$APP_VERSION_CODE"
apk="$SOURCE_DIR/android/app/build/outputs/apk/release/app-release.apk"
test -s "$apk" || { echo 'A signed release APK was not produced.'; exit 1; }
mkdir -p "$DIST_DIR"
install -m 644 "$apk" "$DIST_DIR/$APK_NAME"
"$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION/apksigner" verify --verbose --print-certs "$DIST_DIR/$APK_NAME" > "$DIST_DIR/APK-VERIFICATION.txt"
python3 "$PHOTODROID_RECIPE_ROOT/photodroid/scripts/verify-apk.py" "$DIST_DIR/$APK_NAME" >> "$DIST_DIR/APK-VERIFICATION.txt"
printf '\nSigning origin: %s\n' "$signing_origin" >> "$DIST_DIR/BUILD-INFO.txt"
cd "$DIST_DIR"
sha256sum "$APK_NAME" "$SOURCE_NAME" BUILD-INFO.txt APK-VERIFICATION.txt > SHA256SUMS
cat APK-VERIFICATION.txt
printf 'Verified signed APK: %s\n' "$APK_NAME"
