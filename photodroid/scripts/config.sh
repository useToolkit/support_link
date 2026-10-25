#!/usr/bin/env bash
# Shared constants. This file contains no credentials.
PHOTODROID_RECIPE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
export PHOTODROID_RECIPE_ROOT
export SOURCE_DIR="$PHOTODROID_RECIPE_ROOT/source"
export DIST_DIR="$PHOTODROID_RECIPE_ROOT/dist"
export UPSTREAM_URL='https://github.com/Idea-atmosphere/PhotoDroid.git'
export UPSTREAM_COMMIT='99606e841af9d4cfb338b8c93a3694dad14f8583'
export APP_ID='kr.usetoolkit.photodroid'
export APP_VERSION='0.3.0-ko.1'
export APP_VERSION_CODE='30001'
export RELEASE_TAG="photodroid-ko-v$APP_VERSION"
export APK_NAME="PhotoDroid-KO-$APP_VERSION-arm64.apk"
export SOURCE_NAME="PhotoDroid-KO-$APP_VERSION-source.zip"
export RUST_VERSION='1.99.1'
export NDK_VERSION='27.2.12479018'
export SDK_VERSION='35'
export BUILD_TOOLS_VERSION='35.0.0'
export GRADLE_VERSION='8.10.2'
export FONT_SHA256='76f45ef4a6bcff344c837c95a7dcc26e017e38b5846d5ae0cdcb5b86be2e2d31'
export FONT_LICENSE_SHA256='eeacf16032901d0ed0456876ec77b8f0fda6b3fecec7d972f8543eb602e6c30f'
