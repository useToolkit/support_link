# 포토드로이드 한국어 APK 빌드 레시피

`photodroid-ko` 브랜치 push 또는 workflow_dispatch로 실행합니다. 기존 main과 기존 릴리스는 변경하지 않습니다. 현재 버전은 **0.3.0-ko.2 / versionCode 30002**, 앱 ID는 `kr.usetoolkit.photodroid`입니다.

## 원본과 재현 가능한 입력

upstream `https://github.com/Idea-atmosphere/PhotoDroid.git`의 commit `99606e841af9d4cfb338b8c93a3694dad14f8583`을 `source/`로 가져와 `photodroid/ko.patch`를 적용합니다. 패치는 새 텍스트 파일과 Cargo.lock 변경을 포함합니다. upstream `.github`는 `source/.github`에만 남으며 빌드 저장소의 workflow로 활성화하지 않습니다.

NanumGothic과 OFL 원문은 [Google Fonts 고정 commit](https://github.com/google/fonts/tree/16680f8688ffcd467d2eb2146a9ce0343404581d/ofl/nanumgothic) `16680f8688ffcd467d2eb2146a9ce0343404581d`에서 가져오고 SHA-256도 검사합니다.

| 파일 | SHA-256 |
|---|---|
| NanumGothic-Regular.ttf | `76f45ef4a6bcff344c837c95a7dcc26e017e38b5846d5ae0cdcb5b86be2e2d31` |
| OFL.txt → OFL-NanumGothic.txt | `eeacf16032901d0ed0456876ec77b8f0fda6b3fecec7d972f8543eb602e6c30f` |

## 이번 패키징 업데이트의 검증

편집기 코드가 [기존 성공 run 40178271831](https://github.com/useToolkit/support_link/actions/runs/40178271831)의 체크섬으로 고정한 전체 소스와 일치하는지 두 job에서 검사합니다. 기존 run의 성공 상태도 확인합니다. 문서, 정확한 버전 값 치환, 원본과 일치하는 라이선스 자산 14개 추가 외의 차이는 거부합니다. 이 검증을 통과한 경우에만 체크섬으로 검증한 이전 APK의 arm64 native library를 재사용합니다.

기존 코드에 대해 통과한 테스트는 Android host 27개, 폰트 2개, 모바일 UI·입력·번역 48개로 **총 77개**입니다. 이번 run이 이 테스트를 새로 실행했다고 표시하지 않습니다. `BUILD-INFO.txt`에 원본 run·소스 비교·native 재사용 출처를 기록합니다. 소스가 바뀌면 재사용 검사가 실패하며, 유지한 `build-native.sh`와 `test-targeted.sh`로 전체 빌드·테스트 경로를 사용해야 합니다.

원본 native 빌드 환경은 Rust 1.99.1, NDK 27.2.12479018, arm64, 16 KB max/common page-size입니다. 이번 APK는 JDK 17, Gradle 8.10.2, Android SDK 35/build-tools 35.0.0으로 새로 패키징합니다. APK의 서명·앱 ID·버전·한국어 라벨·Activity·ELF LOAD 정렬·ZIP 16 KB 정렬을 새로 확인합니다. `assets/licenses`에 프로젝트·세 내장 폰트·아이콘·번역 등 원문 14개를 넣고 원본 bytes와 비교합니다. 실기기 실행 검증을 대신하는 검사는 아닙니다.

소스 검증 job과 APK job이 모두 성공해야 `photodroid-ko-v0.3.0-ko.2` 릴리스를 만듭니다. APK, 전체 소스 ZIP, SHA256SUMS, 출처·검증 기록을 공개합니다. 이미 같은 tag의 릴리스가 있으면 실패하며 덮어쓰거나 삭제하지 않습니다.

## 기존 서명 키를 그대로 사용

필수 서명 인증서 SHA-256은 다음과 같습니다. keystore를 읽은 직후와 최종 APK 검증에서 모두 비교합니다. **새 release 키는 생성하지 않습니다.**

```text
58375034ad927bf239163cd83efbc65c3a32568f4c8854d9c38adc117231037f
```

아래 네 Actions secrets가 모두 있으면 우선 사용합니다. 일부만 있으면 실패합니다.

| Secret | 값 |
|---|---|
| ANDROID_KEYSTORE_BASE64 | 기존 release.jks의 base64 |
| ANDROID_STORE_PASSWORD | keystore 비밀번호 |
| ANDROID_KEY_ALIAS | 기존 alias |
| ANDROID_KEY_PASSWORD | 기존 key 비밀번호 |

## Secrets 없이 암호화된 키 전달

네 secret이 모두 없으면 다음 절차를 사용합니다. 계정 로그인이나 Secrets API가 필요하지 않습니다.

1. runner가 임시 RSA-3072 수신자 키와 X.509 인증서를 만듭니다. 개인 키는 runner 임시 폴더에만 두고 **PhotoDroid-signing-recipient-RUN_ID-RUN_ATTEMPT** artifact에 공개 인증서와 식별 정보만 올립니다.
2. 담당자는 인증된 GitHub artifact에서 인증서를 받습니다. `recipient-info.json`의 저장소, run ID, attempt, workflow commit, 인증서 SHA-256, 필수 APK signer 지문을 작업 중인 실행과 대조합니다.
3. 담당자가 개인 보관한 최초 signing backup을 로컬에서 복호화합니다. tar에는 `release.jks`, `store-password.txt`, `key-password.txt`, `key-alias.txt` 네 일반 파일만 있어야 합니다. 비밀번호·alias 파일은 줄바꿈 없이 원래 값을 보존합니다.
4. tar를 해당 실행의 공개 인증서로 CMS 암호화하고 암호문을 base64로 변환합니다. **암호문만** `photodroid/signing-inbox/RUN_ID-RUN_ATTEMPT.cms.b64`에 `[skip ci]` 커밋으로 올립니다. 평문·개인 키·비밀번호를 저장소에 올리지 않습니다.
5. runner는 `photodroid-ko` 최신 branch의 해당 경로를 15초 간격, 최대 20분 동안 읽습니다. 크기 제한과 base64 형식을 검사하고 로컬에서 복호화합니다. tar는 네 파일의 정확한 이름·개수·일반 파일 여부·크기를 확인하며 링크·중복·상위 경로·추가 파일을 거부합니다. `extractall`을 사용하지 않습니다.
6. 비밀번호를 GitHub 로그 마스킹에 등록한 뒤 원래 서명 지문을 검증하고 APK를 서명합니다. 단계 종료·실패 시 평문 자격 정보와 임시 수신자 개인 키를 삭제합니다.

로컬 암호화 명령은 다음과 같습니다. 출력 파일은 암호문이며 개인 키나 비밀번호를 인수로 넘기지 않습니다.

```bash
openssl cms -encrypt -binary -aes256 -outform DER \
  -in signing-backup.tar -out signing-import.cms \
  -recip recipient-public.pem \
  -keyopt rsa_padding_mode:oaep -keyopt rsa_oaep_md:sha256
base64 -w0 signing-import.cms > RUN_ID-RUN_ATTEMPT.cms.b64
```

재실행하면 새 run attempt의 인증서를 받아 새로 암호화합니다. 이전 수신자용 암호문은 재사용하지 않습니다. 최초의 **PhotoDroid-signing-backup-40178271831-1** 암호화 백업과 대응하는 개인 복호화 키는 별도로 보관해야 이후 업데이트에도 같은 서명 키를 사용할 수 있습니다.

## 권한과 아티팩트 범위

소스·APK job은 `contents: read`, `actions: read`를 사용하고 게시 job만 `contents: write`입니다. 공개 수신자 artifact 경로는 두 공개 파일만 명시합니다. 소스 ZIP의 빌드 레시피도 공개 파일 allowlist를 사용하므로 `signing-inbox`나 임시 자격 정보가 들어가지 않습니다. 개인 키·평문 keystore·비밀번호를 업로드하는 artifact 단계는 없습니다.

`source/`, `dist/`, runner의 signing 임시 폴더는 GitHub에 커밋하지 않습니다. SHA256SUMS와 APK-VERIFICATION.txt는 실제 생성된 새 APK에 대해 작성됩니다. 빌드 레시피의 문법·암호화 경로 검사와 실제 GitHub 실행 완료를 구분해 기록합니다.
