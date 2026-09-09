# 출장비 보내기

증빙 사진만 첨부하는 개인용 Gmail 경비 정산 앱입니다. 웹앱과 가벼운 네이티브 Android APK를 함께 제공하며, 사진 선택 → 정산 월/받는 사람 확인 → 제출 확인 → Gmail 자동 발송 또는 테스트 초안 저장을 지원합니다.

## 현재 상태

웹앱과 Android APK 소스, Gmail 연동 코드가 구현되어 있습니다. GitHub Actions의 Android unit test와 installable debug APK 빌드는 `main` 커밋에서 검증되었습니다. Google 계정 로그인, Gmail OAuth 승인, 실제 Gmail 발송은 아직 검증하지 않았습니다. 로컬 미리보기와 테스트 초안 경로는 이메일을 보내지 않습니다.

## 실행

Node.js 24에서 별도 패키지 설치 없이 실행합니다.

```powershell
npm test
npm run check
npm run preview
```

미리보기: http://127.0.0.1:49187 (같은 PC에서만 접근)

복사 버튼이 포함된 설치 안내: http://127.0.0.1:49187/setup

Android 휴대폰에서 사용하려면 설치 안내대로 Apps Script 웹앱을 배포한 뒤 생성된 웹앱 URL을 Android Chrome에서 엽니다. 로컬 `127.0.0.1` 주소는 휴대폰에서 열 수 없습니다.

## Android APK와 GitHub Actions

`android/`에 네이티브 APK 프로젝트가 있습니다. 저장소를 GitHub에 올린 뒤 **Actions → Build Android APK → Run workflow**를 실행하면 `expense-mail-debug-apk` 아티팩트에서 설치 가능한 debug APK를 내려받을 수 있습니다. Android 설정에서 해당 APK 설치를 허용한 뒤 열어 주세요.

APK는 등록된 이메일이 있으면 제출 확인 뒤 Gmail로 자동 발송하고, 이메일이 비어 있으면 Gmail 임시보관함에만 저장합니다. 사진 선택만으로 발송하지 않습니다.

### GitHub에 올리기

새 저장소를 만든 뒤 이 폴더의 파일을 그대로 올리고, 저장소의 **Actions → Build Android APK → Run workflow**를 실행합니다. 로컬 Git을 쓸 경우에는 저장소 루트에서 다음처럼 올릴 수 있습니다.

```powershell
git init -b main
git add .
git commit -m "Build Android expense mail app with Gmail automation"
git remote add origin https://github.com/<계정>/<저장소>.git
git push -u origin main
```

`google-services.json`, 액세스 토큰, 키스토어와 APK는 `.gitignore`로 제외되어 있습니다. 새 저장소는 개인용으로 시작하는 것을 권장합니다.

## Android Gmail OAuth 설정

1. Google Cloud에서 Gmail API를 활성화하고 OAuth 동의 화면에 사용할 본인 계정을 테스트 사용자로 추가합니다.
2. Android OAuth 클라이언트를 패키지명 `com.expensemail.android`와 APK 서명 SHA-1으로 등록합니다.
3. APK를 처음 실행해 Google 계정과 Gmail 발송·초안 작성 권한을 승인합니다.
4. GitHub에는 OAuth 토큰, 키스토어, `google-services.json`을 올리지 않습니다. 현재 workflow는 개인 설치용 debug APK를 빌드합니다.

## Gmail 연결

1. https://script.google.com/home 에 발송할 계정으로 로그인하고 새 프로젝트를 만듭니다.
2. `app/Code.gs`를 기본 Code.gs에 붙여 넣습니다.
3. HTML 파일 **Index**를 만들고 `app/Index.html`을 붙여 넣습니다.
4. 프로젝트 설정에서 매니페스트 파일 표시를 켜고 `app/appsscript.json`을 붙여 넣습니다.
5. 저장 후 **배포 → 새 배포 → 웹 앱**, **실행 사용자: 나**, **액세스 권한: 나만**으로 배포합니다.
6. Google에서 이메일 발송, 초안 작성, 사용자 이메일 주소 확인 권한을 승인합니다. 이 단계는 본인 계정에서 진행합니다.
7. 생성된 웹앱 URL을 Android Chrome에서 열고 수신 주소를 저장한 뒤 사용합니다. **카메라로 촬영** 또는 **갤러리에서 선택**으로 사진을 올릴 수 있습니다. 발송하지 않고 확인하려면 사진과 내용을 입력한 뒤 **테스트 초안 저장**을 누르세요. Chrome 메뉴에서 **홈 화면에 추가**를 선택하면 앱처럼 바로 열 수 있습니다.

Google 기본 프로젝트는 매니페스트에 선언된 Gmail 고급 서비스를 사용합니다. 별도의 표준 Cloud 프로젝트를 연결했다면 그 프로젝트에서 Gmail API를 활성화해야 합니다. 회사 계정은 조직 정책 때문에 관리자 승인이 필요할 수 있습니다.

## 데이터와 동작

- JPG/PNG 최대 30장, 합계 18,000,000바이트. 원본을 재압축하지 않습니다.
- 같은 내용의 사진은 파일명이 달라도 브라우저에서 중복 추가를 막습니다.
- 정산 월을 바꾸면 제목/본문이 해당 월의 기본 문구로 바뀝니다. 이후 편집할 수 있습니다.
- 수신 주소는 사용자가 주소 저장을 눌렀을 때 Google UserProperties에 저장됩니다. 로컬 미리보기에서는 브라우저 localStorage에만 저장합니다.
- **테스트 초안 저장**은 `Gmail.Users.Drafts.create`로 Gmail 임시보관함에 MIME 메일을 저장합니다. 수신자 없이도 초안을 만들 수 있고, 이 동작은 메일을 발송하지 않습니다.
- Android APK는 `GmailClient`가 Gmail REST API의 `users.messages.send` 또는 `users.drafts.create`를 호출합니다. 받는 이메일이 비어 있는지에 따라 두 경로가 자동으로 나뉩니다.
- 사진은 브라우저 메모리에 있고 발송 시 Google로 전달됩니다. 별도 Drive 파일로 저장하지 않습니다. 발송 후 Gmail 보낸편지함에 첨부와 메일이 남습니다.
- 발송 요청 ID, 해시, 성공 결과를 Google UserProperties에 저장합니다. 원본 사진 데이터는 이 설정에 저장하지 않습니다.
- 동일 요청 ID의 재시도를 차단하거나 성공 결과를 반환합니다. 사용자가 새 요청으로 다시 작성하는 것까지 중복 검사하지는 않습니다.
- 네트워크 오류 등 발송 또는 초안 저장 여부가 불확실하면 자동 재시도하지 않습니다. Gmail 임시보관함/보낸편지함에서 상태를 확인한 뒤 새 요청으로 다시 작성하세요.
- 사진 선택 후 새로고침하면 선택 사진이 사라집니다. 로컬 미리보기/앱에서 페이지 이탈 안내를 제공합니다.
- 서버 테스트는 Apps Script 외부 API를 대체한 로컬 테스트입니다. Google OAuth, 배포 설정, 실제 첨부 수신을 검증한 것은 아닙니다.

## 공식 문서

- [Apps Script 웹앱과 실행 계정](https://developers.google.com/apps-script/guides/web)
- [Apps Script Gmail 고급 서비스](https://developers.google.com/apps-script/advanced/gmail)
- [Gmail API MIME 메일 발송](https://developers.google.com/workspace/gmail/api/guides/sending)
- [Android Google AuthorizationClient](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)
- [Android AuthorizationResult](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationResult)
- [Gmail API 초안 생성](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.drafts/create)
