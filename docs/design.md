# 출장비 보내기

사진만 제출하는 개인 출장비 정산 앱. 사용자가 승인한 흐름은 사진 업로드 → 월별 메일 자동 작성 → 등록 수신자 확인 → 이메일이 있으면 본인 Gmail 자동 발송, 없으면 초안 저장이다.

웹 버전은 Google Apps Script HTML Service에 한국어 모바일 반응형 화면을 두고, Android Chrome에서 카메라나 갤러리로 사진을 선택할 수 있게 한다. 설치형 버전은 `android/`의 가벼운 Kotlin Android Views 앱으로 제공한다. APK는 Google Play services `AuthorizationClient`로 Gmail OAuth 토큰을 얻고 Gmail REST API로 원본 이미지 MIME 메일을 보내거나 임시보관함 초안을 만든다. 등록 이메일이 있으면 제출 확인 후 자동 발송하고, 없으면 초안만 만든다. 웹 버전은 Advanced Gmail API를 사용한다.

웹 버전의 받는 이메일은 Google UserProperties에 저장하고, APK는 Android 기기 SharedPreferences에 저장한다. 메일 제목과 본문은 월 변경 시 자동 생성하고 사용자가 편집할 수 있다. 이미지는 브라우저 또는 앱 메모리에 보관하며 별도 Drive 파일로 저장하지 않는다. JPG/PNG, 최대 30장, 총 18,000,000바이트를 허용한다. 미리보기, 개별 제거, 내용 기반 중복 사진 제거, 발송 또는 초안 저장 전 확인, 결과와 불확실한 결과 안내를 제공한다.

메일 발송 요청 ID와 내용 해시를 기록하고 잠금 안에서 발송한다. 이미 성공한 동일 요청은 기록된 결과를 돌려준다. 결과가 불확실하면 자동 재전송하지 않는다. 잘못된 주소, 줄바꿈 헤더 주입, 빈 사진, 손상된 Base64, 형식 위조, 용량 초과를 서버에서 차단한다.

로컬 웹 미리보기는 사진과 메일 편집만 지원하며 연결 상태를 명확히 표시하고 발송을 허용하지 않는다. Android APK는 Google 로그인을 제출 시점에 요청하고 Gmail OAuth 토큰을 메모리에만 둔다. 실제 계정 연결과 배포는 Google 로그인이 필요하다. 자동 발송은 사용자가 제출 확인을 누른 뒤에만 수행한다.

## Android release distribution

The Android app keeps debug builds available for development, while direct distribution uses a signed release APK. The release signing configuration reads `android/keystore.properties` locally or `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` in CI. The keystore and properties file are ignored by Git. GitHub Actions restores a base64-encoded keystore from repository Secrets, builds `app-release.apk`, and uploads it as `expense-mail-release-apk`. The Google Cloud Android OAuth client must use package `com.expensemail.android` and the SHA-1 fingerprint of this same release keystore.
