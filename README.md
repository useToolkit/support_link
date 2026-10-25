# 포토드로이드 한국어 Android판

PhotoDroid를 기반으로 한국어 메뉴·내장 한글 글꼴·Android 문자 입력창을 적용한 사진 및 PSD 편집기입니다.

## 설치 파일

[한국어판 APK와 전체 소스 받기](https://github.com/useToolkit/support_link/releases/tag/photodroid-ko-v0.3.0-ko.2) · [GitHub 빌드 결과](https://github.com/useToolkit/support_link/actions/workflows/photodroid-ko.yml)

**Android 10 이상 / ARM64 전용**입니다. 앱 이름은 **포토드로이드**, 버전은 **0.3.0-ko.2**, 패키지 ID는 `kr.usetoolkit.photodroid`입니다. 원본 PhotoDroid와 별도로 설치할 수 있습니다.

APK를 받은 뒤 파일을 열어 설치합니다. Android가 요청하면 해당 브라우저 또는 파일 앱의 **이 출처 허용**을 켭니다. 사진은 **메뉴 → 파일 → 열기**, 저장 결과는 **내 파일 → 다운로드 → PhotoDroid**에서 찾습니다. 문자 도구로 캔버스를 누른 후 입력창에 한글을 쓰고 **삽입**을 누릅니다.

## 이 브랜치의 구성

| 항목 | 내용 |
|---|---|
| [빌드·서명 안내](photodroid/README.ko.md) | 재현 방법, 환경, 서명 키 보관 |
| [변경 사항](photodroid/RELEASE-NOTES.ko.md) | 한국어판 수정과 사용 범위 |
| [한국어판 패치](photodroid/ko.patch) | 고정 upstream에 적용할 전체 코드 변경 |
| [빌드 스크립트](photodroid/scripts) | 소스 복원, 테스트, APK 서명과 검증 |

원본은 [Idea-atmosphere/PhotoDroid](https://github.com/Idea-atmosphere/PhotoDroid)의 `99606e841af9d4cfb338b8c93a3694dad14f8583`입니다. GitHub Actions가 원본을 가져와 패치와 검증된 한글 글꼴을 적용한 뒤 APK와 수정된 전체 소스 ZIP을 만듭니다. 편집 코드의 테스트 결과와 최종 APK 검증을 확인한 뒤 릴리스를 게시합니다. `0.3.0-ko.2`는 77개 테스트를 통과한 편집기 코드와 동일한지 파일별로 대조한 다음, 라이선스 고지와 버전 정보만 갱신해 패키징합니다.

레이어·마스크·브러시·문자·선택·변형과 이미지 형식 처리 코드는 원본을 사용합니다. 복잡한 Photoshop 문서는 완전한 호환을 보장하지 않으며, Adobe Firefly 등의 온라인 생성형 AI 서비스는 포함하지 않습니다. 실제 기기의 그래픽 드라이버·메모리에 따른 호환성은 별도 확인이 필요합니다.

원본 MIT/Apache-2.0 라이선스와 제3자 고지는 전체 소스에 포함합니다. 나눔고딕은 SIL OFL 1.1로 배포합니다. Adobe의 공식 제품이 아닙니다.
