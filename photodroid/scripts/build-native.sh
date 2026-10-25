#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
: "${ANDROID_HOME:?Android SDK is required}"
ndk_bin="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$ndk_bin/aarch64-linux-android29-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ndk_bin/aarch64-linux-android29-clang"
export CC_aarch64_linux_android="$ndk_bin/aarch64-linux-android29-clang"
export CXX_aarch64_linux_android="$ndk_bin/aarch64-linux-android29-clang++"
export AR_aarch64_linux_android="$ndk_bin/llvm-ar"
# Target-only flags leave host build scripts unaffected. NDK r27 needs explicit 16 KB flags.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
cd "$SOURCE_DIR"
cargo "+$RUST_VERSION" build --locked -p photocraft-android --lib --target aarch64-linux-android --profile android
native_dest="$SOURCE_DIR/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$native_dest"
install -m 644 target/aarch64-linux-android/android/libphotocraft_android.so "$native_dest/libphotocraft_android.so"
"$ndk_bin/llvm-strip" --strip-unneeded "$native_dest/libphotocraft_android.so"
