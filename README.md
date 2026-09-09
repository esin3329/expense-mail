# 출장비 보내기

증빙 사진만 첨부하는 개인용 Gmail 경비 정산 앱입니다. 웹앱과 가벼운 네이티브 Android APK를 함께 제공하며, 사진 선택 → 정산 월/받는 사람 확인 → 제출 확인 → Gmail 자동 발송 또는 테스트 초안 저장을 지원합니다.

## 현재 상태

웹앱과 네이티브 Android 앱, Gmail 연동 코드가 구현되어 있습니다. GitHub Actions는 개발용 `debug APK`와 직접 배포용 서명 `release APK`를 각각 artifact로 만듭니다. `release APK`를 만들려면 아래 안내대로 한 번만 keystore를 만들고 GitHub Secrets를 설정해야 합니다. Google 계정 로그인, Gmail OAuth 승인, 실제 Gmail 발송은 아직 자동 검증하지 않았습니다. 로컬 미리보기와 테스트 초안 경로는 이메일을 보내지 않습니다.

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

## Android APK 배포

`android/`에 네이티브 Android 앱이 있습니다. 개발 중에는 debug APK를 사용할 수 있고, 다른 사람에게 전달할 때는 서명된 release APK를 사용합니다. release APK는 GitHub Actions가 빌드하며, 결과는 `expense-mail-release-apk` artifact의 `app-release.apk`입니다.

### 1. 배포용 keystore를 한 번만 만들기

JDK가 설치된 환경에서 저장소 루트에서 실행합니다. 비밀번호는 직접 정하고 안전하게 보관하세요.

```powershell
keytool -genkeypair -v -keystore android/release.keystore -alias expense-mail -keyalg RSA -keysize 2048 -validity 10000
```

이 keystore는 앱 업데이트에도 계속 필요합니다. 분실하거나 새 keystore로 바꾸면 기존 설치 위에 업데이트할 수 없으므로 암호와 파일을 별도로 백업하세요. `android/release.keystore`는 `.gitignore`에 포함되어 있습니다.

### 2. Google Cloud에 release APK 등록하기

```powershell
keytool -list -v -keystore android/release.keystore -alias expense-mail
```

출력된 `SHA1` 값을 Google Cloud의 Android OAuth 클라이언트에 등록합니다.

- 패키지명: `com.expensemail.android`
- 인증서 SHA-1: 위 명령으로 확인한 release keystore의 `SHA1`
- Gmail API를 활성화하고, OAuth 동의 화면의 테스트 사용자에 사용할 Google 계정을 추가

debug keystore의 SHA-1과 release keystore의 SHA-1은 다릅니다. release APK에서 Google 권한 화면이 실패하면 가장 먼저 패키지명과 release SHA-1 등록을 확인하세요.

### 3. GitHub Secrets 설정하기

GitHub 저장소의 **Settings → Secrets and variables → Actions → New repository secret**에서 아래 네 가지 Secret을 만듭니다.

| Secret 이름 | 값 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `release.keystore` 파일 전체를 Base64로 인코딩한 값 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 생성 시 사용한 비밀번호 |
| `ANDROID_KEY_ALIAS` | `expense-mail` |
| `ANDROID_KEY_PASSWORD` | alias 키 생성 시 사용한 비밀번호 |

PowerShell에서는 keystore 내용을 파일로 남기지 않고 클립보드로 복사할 수 있습니다.

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('android/release.keystore')) | Set-Clipboard
```

`ANDROID_KEYSTORE_BASE64`에는 클립보드 값을 붙여 넣습니다. keystore, 비밀번호, OAuth 토큰은 GitHub 저장소나 커밋에 올리지 마세요.

### 4. release APK 만들기

1. 변경 사항을 GitHub 저장소의 `main` 브랜치에 push합니다.
2. 저장소에서 **Actions → Build Android APK → Run workflow**를 선택합니다.
3. 실행이 끝나면 workflow 요약의 **Artifacts → `expense-mail-release-apk`**를 다운로드합니다.

`expense-mail-debug-apk`는 개발·테스트용이고, 다른 사람에게 전달할 파일은 반드시 `expense-mail-release-apk`의 `app-release.apk`를 사용하세요.

### 5. Android 휴대폰에 설치하기

1. 다운로드한 artifact 압축을 풀어 `app-release.apk` 파일을 준비합니다.
2. APK를 USB, 메신저, 클라우드 저장소 등으로 Android 휴대폰에 전달합니다.
3. APK를 여는 데 사용할 파일 관리자 또는 브라우저에 대해 Android 설정의 **알 수 없는 앱 설치** 권한을 허용합니다. 제조사에 따라 **설정 → 보안 및 개인정보 보호 → 알 수 없는 앱 설치** 등의 이름으로 표시됩니다.
4. `app-release.apk`를 탭하고 **설치**를 누릅니다.
5. 설치가 끝나면 **출장비 보내기**를 실행합니다. 처음 메일을 제출할 때 Google 계정 선택과 Gmail 발송·초안 작성 권한 승인이 표시됩니다.

설치 후에는 사진을 선택하고 정산 월·제목·내용을 확인한 뒤 제출합니다. 받는 이메일이 있으면 확인 후 Gmail로 발송하고, 비워 두면 발송하지 않는 Gmail 임시보관함 초안을 만듭니다. 사진을 선택하는 것만으로는 발송하지 않습니다.

같은 앱을 업데이트할 때는 반드시 동일한 release keystore로 빌드해야 합니다. 다른 keystore로 만든 APK는 기존 설치 위에 업데이트할 수 없습니다.

### 로컬에서 release APK 만들기

로컬 Android SDK와 Gradle 8.9가 있는 경우 `android/keystore.properties.example`을 복사해 `android/keystore.properties`를 만들고 실제 값을 입력한 뒤 실행합니다.

```powershell
Copy-Item android/keystore.properties.example android/keystore.properties
gradle -p android assembleRelease --no-daemon
```

생성물은 `android/app/build/outputs/apk/release/app-release.apk`입니다. 서명 값이 없는 상태에서 `assembleRelease`를 실행하면 안전을 위해 빌드가 실패합니다. debug 빌드는 서명 Secret 없이 기존처럼 실행할 수 있습니다.

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
