# 포토드로이드 한국어 APK 빌드 레시피

이 폴더와 `.github/workflows/photodroid-ko.yml`을 빌드 저장소의 **photodroid-ko 브랜치**에 둡니다. 해당 브랜치 push 또는 Actions의 수동 실행으로 빌드합니다. 기존 main 브랜치와 기존 릴리스는 수정하지 않습니다.

## 빌드 입력

다음 두 파일은 작업 담당자가 제공해야 합니다. 레시피가 임의로 대체하거나 자동 생성하지 않습니다.

- `photodroid/ko.patch`: 아래 upstream commit 기준의 한국어 변경 패치. 새로운 텍스트 파일과 갱신된 `Cargo.lock`도 포함해야 합니다. 폰트 바이너리는 포함하지 않아도 됩니다.
- `photodroid/backup-public.pem`: 최초 서명 키 백업 암호화를 위한 **X.509 RSA 공개 인증서**. 대응하는 복호화 개인 키는 저장소·Actions에 넣지 않습니다.

upstream은 `https://github.com/Idea-atmosphere/PhotoDroid.git`의 commit `99606e841af9d4cfb338b8c93a3694dad14f8583`으로 고정됩니다. 빌드 때 `source/`에 해당 commit을 가져와 패치를 적용합니다. upstream의 `.github`는 `source/.github`에만 존재하며 이 저장소의 workflow로 활성화하지 않습니다.

NanumGothic은 [Google Fonts 공식 저장소](https://github.com/google/fonts/tree/main/ofl/nanumgothic)에서 받아 다음 SHA-256을 검증합니다. URL의 내용이 바뀌면 검증에 실패하며 다른 폰트를 조용히 사용하지 않습니다.

| 파일 | SHA-256 |
|---|---|
| NanumGothic-Regular.ttf | `76f45ef4a6bcff344c837c95a7dcc26e017e38b5846d5ae0cdcb5b86be2e2d31` |
| OFL.txt → OFL-NanumGothic.txt | `eeacf16032901d0ed0456876ec77b8f0fda6b3fecec7d972f8543eb602e6c30f` |

## 고정 빌드 환경과 결과

Ubuntu 24.04, Rust **1.99.1**, JDK **17**, Gradle **8.10.2**, Android SDK **35**, build-tools **35.0.0**, NDK **27.2.12479018**을 사용합니다. 패키지는 `kr.usetoolkit.photodroid`, versionName은 `0.3.0-ko.1`, versionCode는 `30001`입니다. 버전 변경 시 `scripts/config.sh`, workflow의 결과 파일 경로, 릴리스 안내 문구를 함께 변경합니다.

APK 빌드와 표적 테스트는 병렬로 실행합니다. Android host 테스트 전체, 한글 문자·줄바꿈 보존, 내장 폰트의 한글 범위/렌더링, 모바일 메뉴와 키보드 및 AndroidLabel 문구, `every_tl_literal_is_translated` 번역 게이트를 실행합니다. 테스트를 실패 처리 없이 넘기거나 `Cargo.lock`을 자동 재작성하지 않습니다.

Rust 1.99.1에서 확인된 host proc-macro 링크 문제를 피하도록 테스트에만 `--config 'profile.test.build-override.codegen-units=1'`을 적용합니다. 앱 소스나 lockfile을 바꾸는 우회는 아니며 Android 프로필에는 적용하지 않습니다.

NDK r27의 arm64 링크에 `max-page-size=16384`와 `common-page-size=16384`를 적용하고 `llvm-strip`으로 불필요한 심볼을 제거합니다. 패키징 후 APK 서명, 앱 ID·버전·한국어 라벨·Activity, 모든 `.so`의 ELF LOAD 정렬, `zipalign -c -P 16`을 확인합니다. 이 정적 검증은 실기기 실행 검증을 대신하지 않습니다. [Android의 16 KB 페이지 안내](https://developer.android.com/guide/practices/page-sizes)를 따릅니다.

두 작업이 성공해야 릴리스 작업이 `photodroid-ko-v0.3.0-ko.1`을 게시합니다. 공개 파일은 서명된 APK, 전체 소스 ZIP, `SHA256SUMS`, 빌드 출처와 검증 기록입니다. 소스 ZIP에는 upstream LICENSE-APACHE·LICENSE-MIT·NOTICE·ATTRIBUTION 및 폰트 원문 라이선스를 보존합니다. 관련 없는 저장소 릴리스의 latest 표시는 변경하지 않습니다.

## 최초 서명과 업데이트 키 보관

다음 네 Actions secrets가 모두 설정되어 있으면 그 키로 서명합니다.

| Secret | 값 |
|---|---|
| ANDROID_KEYSTORE_BASE64 | 기존 release.jks의 base64 |
| ANDROID_STORE_PASSWORD | keystore 비밀번호 |
| ANDROID_KEY_ALIAS | 해당 키 alias |
| ANDROID_KEY_PASSWORD | 해당 키 비밀번호 |

아무 secret도 없으면 최초용 RSA-4096 release 키와 무작위 비밀번호를 runner 임시 폴더에서 생성합니다. 일부 secret만 있으면 다른 키를 만드는 대신 실패합니다. 생성한 키와 비밀번호는 tar로 묶어 공개 인증서로 **CMS AES-256-CBC / RSA-OAEP-SHA256 암호화**하고, **PhotoDroid-signing-backup-실행번호-시도번호** Actions artifact에 암호문만 올립니다. 평문 keystore·비밀번호·개인 키는 로그, 소스 ZIP, APK artifact, 릴리스에 넣지 않으며 해당 단계 종료 시 삭제합니다.

**최초 성공 빌드의 암호화 백업을 만료 전에 내려받아 개인 보관하고, 이후 업데이트에서는 반드시 같은 서명 키를 재사용해야 합니다.** artifact 보관 기간은 90일입니다. 복호화 개인 키도 별도로 안전하게 보관하세요. 키가 바뀌면 설치된 앱 위로 업데이트할 수 없습니다.

안전한 로컬 폴더에서 복구하는 예시는 다음과 같습니다. 아래 명령은 CI에서 실행하지 않습니다.

```bash
umask 077
mkdir recovered-photodroid-signing
openssl cms -decrypt -binary -inform DER \
  -in PhotoDroid-signing-backup.cms \
  -recip backup-public.pem -inkey /secure/location/backup-private.pem \
  -out recovered-photodroid-signing/signing-backup.tar
tar -xf recovered-photodroid-signing/signing-backup.tar -C recovered-photodroid-signing
```

복원된 `release.jks`, `store-password.txt`, `key-password.txt`, `key-alias.txt`를 위 네 secrets로 등록한 뒤 버전을 올립니다. 비밀번호를 터미널에 출력할 필요 없이 `gh secret set SECRET_NAME < 파일`과 base64 파이프를 사용할 수 있습니다.

이미 같은 tag의 릴리스가 있으면 workflow는 실패합니다. 재실행으로 다른 키의 APK를 기존 릴리스에 덮어쓰지 않습니다. 처음 성공해 배포한 APK가 생겼다면 릴리스를 삭제해 키를 새로 만드는 방법으로 업데이트하지 마세요. 초기 빌드가 실패했지만 암호화 백업이 만들어진 경우에도 해당 키를 복구·등록하면 다음 시도에서 재사용할 수 있습니다.

## 권한과 검증 범위

APK·테스트 job은 `contents: read`, 게시 job만 `contents: write`를 사용합니다. secrets는 서명 단계에만 주입합니다. 릴리스는 GitHub CLI로 신규 생성하며 기존 릴리스/태그를 삭제하거나 강제로 교체하지 않습니다. PR 이벤트에서는 실행하지 않습니다.

실제 입력 파일 두 개가 없으면 빌드를 실행할 수 없습니다. 이 레시피의 문법 검증과 암호화 방식 검증만으로 APK 빌드 성공을 주장하지 않습니다. 실제 완료 여부는 해당 GitHub Actions run과 릴리스의 검증 결과로 확인합니다.
