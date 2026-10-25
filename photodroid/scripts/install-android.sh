#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
: "${RUNNER_TEMP:?GitHub runner temporary directory is required}"
: "${GITHUB_ENV:?GitHub Actions environment file is required}"
photodroid_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
sdkmanager_bin="$photodroid_sdk/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager_bin" ]]; then
  echo 'The ubuntu-24.04 runner is missing its Android command-line tools.'
  exit 1
fi
# yes exits with SIGPIPE after sdkmanager is done; inspect sdkmanager's status, not yes's.
set +e
yes | "$sdkmanager_bin" --sdk_root="$photodroid_sdk" --licenses > "$RUNNER_TEMP/photodroid-sdk-licenses.log" 2>&1
sdk_license_status=${PIPESTATUS[1]}
set -e
test "$sdk_license_status" -eq 0 || { echo 'Android SDK license acceptance failed.'; exit 1; }
packages=("platforms;android-$SDK_VERSION" "build-tools;$BUILD_TOOLS_VERSION")
if [[ "${1:-}" != '--sdk-only' ]]; then packages+=("ndk;$NDK_VERSION"); fi
"$sdkmanager_bin" --sdk_root="$photodroid_sdk" "${packages[@]}"
{
  printf 'ANDROID_HOME=%s\n' "$photodroid_sdk"
  printf 'ANDROID_SDK_ROOT=%s\n' "$photodroid_sdk"
} >> "$GITHUB_ENV"
