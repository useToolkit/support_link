# 포토드로이드 한국어 개인용 빌드

설치 파일은 **PhotoDroid-KO-0.3.0-ko.1-arm64.apk**입니다. Android 10 이상, arm64 기기용이며 앱 ID는 `kr.usetoolkit.photodroid`입니다.

한국어 메뉴와 시작 안내, 내장 한국어 글꼴, 한글·문자 입력창, 모바일 도구 메뉴를 적용했습니다. 사진과 PSD를 Android 파일 선택기에서 불러오고, 편집본은 **내 파일 → 다운로드 → PhotoDroid**에서 확인할 수 있습니다.

- 문자 도구로 캔버스를 누른 뒤 휴대폰 기본 키보드로 작성하고 **삽입**을 누르세요.
- `PhotoDroid-KO-0.3.0-ko.1-source.zip`에는 수정된 전체 소스, 라이선스, 폰트 원문 라이선스, 빌드 레시피가 들어 있습니다.
- `SHA256SUMS`로 파일 무결성을, `APK-VERIFICATION.txt`로 서명 인증서 및 네이티브 라이브러리 정렬 검사 결과를 확인할 수 있습니다.
- GitHub Actions의 프로토콜·한국어 폰트·모바일 UI 테스트와 APK 검증을 통과한 뒤 게시됩니다. 실제 휴대폰의 GPU, 저장소 제공자, 키보드별 동작은 사용 기기에서 확인해야 합니다.

기반 소스: [Idea-atmosphere/PhotoDroid](https://github.com/Idea-atmosphere/PhotoDroid), commit `99606e841af9d4cfb338b8c93a3694dad14f8583`. Adobe의 공식 제품이 아니며 Photoshop의 모든 기능·AI 서비스를 동일하게 제공하는 제품은 아닙니다.
