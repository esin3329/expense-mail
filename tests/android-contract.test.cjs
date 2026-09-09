const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');

const read=path=>fs.readFileSync(path,'utf8');

test('Android project declares a Gmail-capable installable app without committed secrets',()=>{
  const manifest=read('android/app/src/main/AndroidManifest.xml');
  const gradle=read('android/app/build.gradle.kts');
  const strings=read('android/app/src/main/res/values/strings.xml');
  assert.match(manifest,/android\.permission\.INTERNET/);
  assert.match(manifest,/android\.hardware\.camera\.any/);
  assert.match(gradle,/namespace\s*=\s*"com\.expensemail\.android"/);
  assert.match(gradle,/com\.google\.android\.gms:play-services-auth/);
  assert.match(strings,/expense_mail_app_name/);
  assert.doesNotMatch(`${manifest}\n${gradle}\n${strings}`,/AIza[0-9A-Za-z_-]{20,}/);
});

test('Android submission code branches to Gmail send or draft endpoints',()=>{
  const source=read('android/app/src/main/java/com/expensemail/android/GmailClient.kt');
  const activity=read('android/app/src/main/java/com/expensemail/android/MainActivity.kt');
  assert.match(source,/https:\/\/gmail\.googleapis\.com\/gmail\/v1/);
  assert.doesNotMatch(source,/gmail\/v1\/users\/me"/);
  assert.match(source,/users\/me\/messages\/send/);
  assert.match(source,/users\/me\/drafts/);
  assert.match(activity,/ACTION_IMAGE_CAPTURE/);
  assert.match(activity,/ACTION_OPEN_DOCUMENT/);
  assert.match(activity,/WindowInsetsCompat/);
  assert.match(activity,/AuthorizationClient/);
  assert.match(activity,/AuthorizationRequest/);
  assert.match(activity,/getAccessToken/);
  assert.doesNotMatch(activity,/GoogleAuthUtil/);
  assert.doesNotMatch(activity,/UserRecoverableAuthException/);
  assert.match(activity,/getAuthorizationResultFromIntent/);
  assert.match(activity,/if \(submission\.recipient\.isBlank\(\)\)/);
  assert.match(activity,/client\.createDraft/);
  assert.match(activity,/client\.send/);
  assert.match(activity,/attempt_in_flight/);
});

test('GitHub Actions publishes an installable debug APK artifact',()=>{
  const workflow=read('.github/workflows/android-apk.yml');
  assert.match(workflow,/setup-android/);
  assert.match(workflow,/testDebugUnitTest/);
  assert.match(workflow,/assembleDebug/);
  assert.match(workflow,/app-debug\.apk/);
});
