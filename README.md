# 출장비 보내기

증빙 사진을 Gmail로 보내거나, 발송하지 않고 Gmail 임시보관함에 초안으로 저장하는 개인용 Android 앱입니다.

## 바로 설치하기

최신 release APK를 다운로드하세요.

**[expense-mail-release.apk 다운로드](https://drive.google.com/file/d/1HnMndsbikNxTROU8RU53xS-o5iR9sDFW/view?usp=drivesdk)**

1. Android 휴대폰에서 APK를 다운로드합니다.
2. 파일 관리자 또는 브라우저에 **알 수 없는 앱 설치** 권한을 허용합니다.
3. `app-release.apk`를 열고 설치합니다.
4. 사진을 선택하고 월·제목·내용을 확인한 뒤 제출합니다.
5. 처음 제출할 때 Google 계정을 선택하고 Gmail 권한을 승인합니다.

`app-release.apk`를 설치해야 합니다. `app-debug.apk`는 개발용이며 Google OAuth 등록 인증서가 다릅니다.

## 사용 방법

- **카메라로 촬영** 또는 **갤러리에서 선택**으로 증빙 사진을 추가합니다.
- 받는 이메일이 있으면 확인 후 Gmail로 발송합니다.
- 받는 이메일을 비우면 메일을 발송하지 않고 Gmail 임시보관함에 초안을 저장합니다.
- 사진을 선택하는 것만으로는 메일이 발송되지 않습니다.

사용 제한: JPG/PNG 최대 30장, 전체 18 MB. 사진은 원본 그대로 첨부합니다.

## Gmail 권한 설정

권한 화면이 취소되거나 승인되지 않으면 Google Cloud에서 다음 항목을 확인하세요.

### Google Cloud 설정

Android OAuth 클라이언트와 OAuth 동의 화면은 **같은 Google Cloud 프로젝트**에 있어야 합니다.

| 항목 | 값 |
| --- | --- |
| Android 패키지명 | `com.expensemail.android` |
| release APK SHA-1 | `73:F2:FE:BF:C9:58:39:F6:84:87:54:17:4C:62:DA:B1:4B:2D:DD:77` |
| Gmail API | 사용 설정 |
| Data Access | `gmail.send`, `gmail.compose` |

OAuth 동의 화면의 **테스트 사용자**에 사용할 Gmail 계정을 추가하세요. 앱이 `Testing` 상태이면 테스트 권한은 7일 후 만료될 수 있습니다.

### 권한을 다시 승인하는 방법

1. Google 계정의 타사 앱 연결에서 **출장비 보내기** 권한을 삭제합니다.
2. 휴대폰 설정에서 앱 데이터를 삭제합니다.
3. 최신 `expense-mail-release.apk`를 다시 설치합니다.
4. 테스트 사용자로 등록된 Google 계정으로 권한을 다시 승인합니다.

앱은 Google 권한 화면을 닫은 경우, 권한 거부, OAuth 설정 오류를 서로 다른 메시지로 표시합니다. 오류가 계속되면 화면에 표시된 정확한 문구를 확인하세요.

## 웹앱으로 사용하기

Android APK 대신 Google Apps Script 웹앱을 사용할 수도 있습니다.

1. [Google Apps Script](https://script.google.com/home)에서 새 프로젝트를 만듭니다.
2. `app/Code.gs` 내용을 기본 `Code.gs`에 붙여 넣습니다.
3. HTML 파일 `Index`를 만들고 `app/Index.html`을 붙여 넣습니다.
4. 매니페스트 표시를 켜고 `app/appsscript.json`을 붙여 넣습니다.
5. **배포 → 새 배포 → 웹 앱**에서 실행 사용자를 `나`, 액세스 권한을 `나만`으로 설정합니다.
6. 본인 Google 계정으로 Gmail 권한을 승인합니다.
7. 웹앱 URL을 Android Chrome에서 열고 필요하면 **홈 화면에 추가**를 선택합니다.

별도의 Google Cloud 프로젝트를 연결했다면 그 프로젝트에서도 Gmail API를 사용 설정해야 합니다. 회사 계정은 관리자 정책으로 외부 앱 권한이 차단될 수 있습니다.

## 개발자용

웹 미리보기와 테스트에는 Node.js가 필요합니다.

```powershell
npm test
npm run check
npm run preview
```

미리보기 주소: `http://127.0.0.1:49187`

Android APK는 [GitHub Actions](https://github.com/esin3329/expense-mail/actions/workflows/android-apk.yml)가 빌드합니다.

1. 변경사항을 `main` 브랜치에 push합니다.
2. **Actions → Build Android APK** 실행 결과를 엽니다.
3. `expense-mail-release-apk` artifact에서 `app-release.apk`를 다운로드합니다.

release APK를 만들려면 기존과 동일한 `android/release.keystore`가 필요합니다. keystore를 바꾸면 기존 앱 위에 업데이트할 수 없습니다.

### GitHub Actions 서명 설정 (배포자만)

Repository Secrets에 다음 네 가지 값을 등록해야 signed APK가 생성됩니다.

| Secret | 설명 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | release keystore의 Base64 값 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 비밀번호 |
| `ANDROID_KEY_ALIAS` | `expense-mail` |
| `ANDROID_KEY_PASSWORD` | alias 비밀번호 |
## 공식 문서

- [Google Android 클라이언트 인증](https://developers.google.com/android/guides/client-auth)
- [Android AuthorizationClient](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)
- [Gmail API 메일 발송](https://developers.google.com/workspace/gmail/api/guides/sending)
- [Gmail API 초안 생성](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.drafts/create)
- [Apps Script 웹앱](https://developers.google.com/apps-script/guides/web)