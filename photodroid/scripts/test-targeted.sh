#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
test_log="${RUNNER_TEMP:-/tmp}/photodroid-tests.log"
cd "$SOURCE_DIR"
{
  # Rust 1.99.1 can mis-link optimized host proc macros with multiple codegen units.
  # Scope the compatibility setting to build scripts/proc macros in the test profile.
  test_config='profile.test.build-override.codegen-units=1'
  cargo "+$RUST_VERSION" test --locked --config "$test_config" -p photocraft-android --lib
  cargo "+$RUST_VERSION" test --locked --config "$test_config" -p photocraft-text --lib -- bundled_korean_covers_modern_hangul_without_system_fonts bundled_fonts_inflate_to_their_files
  cargo "+$RUST_VERSION" test --locked --config "$test_config" -p photocraft-ui-egui --lib -- bundled_korean_renders_without_system_fonts mobile:: virtual_input:: a_tap_into_text_asks_for_the_soft_keyboard every_tl_literal_is_translated
} 2>&1 | tee "$test_log"
