# 포토드로이드 한국어 개인용 빌드

설치 파일은 **PhotoDroid-KO-0.3.0-ko.2-arm64.apk**입니다. Android 10 이상, arm64 기기용이며 앱 ID는 `kr.usetoolkit.photodroid`입니다.

한국어 메뉴와 시작 안내, 내장 한국어 글꼴, 한글·문자 입력창, 모바일 도구 메뉴를 적용했습니다. 사진과 PSD를 Android 파일 선택기에서 불러오고, 편집본은 **내 파일 → 다운로드 → PhotoDroid**에서 확인할 수 있습니다.

- 문자 도구로 캔버스를 누른 뒤 휴대폰 기본 키보드로 작성하고 **삽입**을 누르세요.
- `PhotoDroid-KO-0.3.0-ko.2-source.zip`에는 수정된 전체 소스, 라이선스, 폰트 원문 라이선스, 빌드 레시피가 들어 있습니다.
- `SHA256SUMS`로 파일 무결성을, `APK-VERIFICATION.txt`로 서명 인증서 및 네이티브 라이브러리 정렬 검사 결과를 확인할 수 있습니다.
- 편집기 코드는 [77개 테스트가 통과한 빌드](https://github.com/useToolkit/support_link/actions/runs/40178271831)와 바이트 단위로 대조합니다. 문서·정확한 버전 변경·라이선스 자산 추가만 허용한 뒤 검증된 native library를 사용하고, 새 APK의 서명·버전·라이선스·16 KB 정렬을 다시 검사합니다. 실제 휴대폰의 GPU, 저장소 제공자, 키보드별 동작은 사용 기기에서 확인해야 합니다.

이 버전은 APK 내부 `assets/licenses`에도 프로젝트·폰트·동봉 자산의 원문 라이선스 14개를 포함합니다. 이전 `0.3.0-ko.1`과 같은 인증서로 서명하며 versionCode를 `30002`로 올렸습니다. 설치된 초기 버전 위에 업데이트할 수 있도록 서명 인증서 SHA-256을 빌드 전후 모두 확인합니다.

```text
58375034ad927bf239163cd83efbc65c3a32568f4c8854d9c38adc117231037f
```

기반 소스: [Idea-atmosphere/PhotoDroid](https://github.com/Idea-atmosphere/PhotoDroid), commit `99606e841af9d4cfb338b8c93a3694dad14f8583`. Adobe의 공식 제품이 아니며 Photoshop의 모든 기능·AI 서비스를 동일하게 제공하는 제품은 아닙니다.
