# iCalc S26 — iOS-style native Android calculator

A clean-room calculator for Android/Galaxy S26 inspired by the overall visual conventions of Apple's iOS 26 Calculator. **Not an official Apple app, and not a byte-for-byte copy.** No Apple icons, fonts, code, or bundled screenshots are used.

## Features included in the Android project

- Basic keypad: digits, `+ − × ÷`, percentage with additive-percent semantics (`100 + 15% = 115`), decimal input, backspace, `C`, `AC`, sign switch, precedence and parentheses.
- Scientific keypad: parentheses, memory (mc/m+/m-/mr), powers, roots, ln/log, trigonometry and inverse functions (2nd), hyperbolic functions, pi/e, factorial, and Deg/Rad toggle.
- Calculation history: up to 200 records locally, tap to reload, individual deletion, clear all.
- Offline unit conversions: length, mass, temperature, area, volume, time, speed, data, pressure, energy.
- Currency conversion: **Hana Bank API On–compatible** read-only HTTPS adapter, explicit link setup, dated cached rates, separate mid-market / cash / remittance transaction directions, and fail-closed no-quote behavior. **Live Hana rates require bank authorization and a privately hosted adapter; the app does not provide unsanctioned live quotes.**
- Math Notes **subset**: typed expressions, variables, live results, and a basic two-dimensional graph `y=f(x)`.
- Haptic key feedback, landscape scientific layout, dark UI, local data storage.
- Android unit tests for arithmetic correctness.

## Hana Bank exchange rate integration

Hana API On advertises 58-currency rate service, subject to signup and approval. The app includes a currency tab and an adapter client, but no Hana API key is available, so **real-time bank data is not enabled out of the box**. No estimates, ECB fallback, scraped Hana pages, or fabricated rates are presented as bank rates. Configure your own authorized HTTPS adapter in `환율 → ⚙ 연결 설정`. See [adapter schema and guidance](docs/hana-adapter.md).

## Not yet at iOS 26 parity

- Handwriting recognition with stylus/finger **not implemented**.
- Interactive 3D Math Notes graphing **not implemented**.
- Device-to-device pixel matching, actual Galaxy S26 QA and benchmark testing **not verified**.
- Some advanced input edge cases (multi-operation repeated equals, locale-specific math symbols and numeric systems) may differ from Apple's current implementation.

## Design dashboard

Open [dashboard/index.html](dashboard/index.html) in a browser. It includes UI tokens, a standalone **visual prototype**, feature inventory, implementation roadmap, and QA checklist. The browser simulator is separate from the native Android implementation.

## Build

Requirements: JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.11.1, Kotlin 2.2.21, Gradle 8.13.

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions `.github/workflows/build-apk.yml` automatically builds the test + APK and exposes `iCalcS26-APK` as a downloadable workflow artifact.

Install: Download and extract the APK from GitHub Actions; send to Galaxy S26; allow APK installs for your file manager (only for sources you trust); tap and install. A local debug APK is test-signed and is **not** a Play Store production release.

## Basic test cases

| Input | Expected |
| --- | --- |
| `2+3×4` | `14` |
| `(2+3)×4` | `20` |
| `100+15%` | `115` |
| `200×10%` | `20` |
| `sin(30)` in Deg | `0.5` |
| `1÷0` | error |

## Reference

Apple iPhone User Guide iOS 26: Basic / Scientific / Math Notes / History / Conversion:

- https://support.apple.com/guide/iphone/iph1ac0b5cc/26/ios/26
- https://support.apple.com/guide/iphone/iphb977c4c0e/26/ios/26
- https://support.apple.com/guide/iphone/iph14c31d991/26/ios/26
- https://support.apple.com/guide/iphone/iph69d274dde/26/ios/26

Android UI components licensed under their respective open-source licenses. Original app code in this repository is supplied for personal iteration and testing.
