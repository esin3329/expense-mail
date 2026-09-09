# Android Release APK Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** Produce a directly installable, signed Android release APK through GitHub Actions while keeping signing material out of the repository.

**Architecture:** Keep the existing lightweight Kotlin Android app and debug test path. Add a release signing configuration that reads local `android/keystore.properties` or CI environment variables, then make the workflow restore a base64 keystore from GitHub Secrets and upload `app-release.apk`.

**Tech Stack:** Kotlin DSL Gradle, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, GitHub Actions, Node.js contract tests.

**Spec:** `docs/superpowers/specs/2026-09-10-android-release-apk-design.md`

## Global Constraints

- Keep the APK dependency-light; do not add Compose or a large UI framework.
- Keep debug builds available without signing secrets.
- Release builds must fail with an actionable error when signing values are incomplete.
- Never commit a keystore, OAuth token, client secret, or generated APK.
- The release OAuth client SHA-1 must match the keystore used for distribution.
- Preserve the existing Gmail send/draft behavior and all existing tests.

---

### Task 1: Add failing release-distribution contract coverage

**Files:**
- Modify: `tests/android-contract.test.cjs`

**Interfaces:**
- Consumes: the Android Gradle module, workflow, and documentation files.
- Produces: assertions that require a release signing configuration, a local properties example, four CI secret names, and a release APK artifact.

- [ ] **Step 1: Write the failing assertions**

Add tests with these exact behaviors:

```js
test('Android release build has a repository-safe signing path',()=>{
  const gradle=read('android/app/build.gradle.kts');
  const example=read('android/keystore.properties.example');
  assert.match(gradle,/signingConfigs/);
  assert.match(gradle,/keystore\.properties/);
  assert.match(gradle,/ANDROID_KEYSTORE_PATH/);
  assert.match(gradle,/assembleRelease/);
  assert.match(example,/storeFile=/);
  assert.match(example,/storePassword=/);
  assert.match(example,/keyAlias=/);
  assert.match(example,/keyPassword=/);
  assert.doesNotMatch(example,/actual|secret-value/i);
});

test('GitHub Actions builds and uploads a signed release APK',()=>{
  const workflow=read('.github/workflows/android-apk.yml');
  assert.match(workflow,/ANDROID_KEYSTORE_BASE64/);
  assert.match(workflow,/ANDROID_KEYSTORE_PASSWORD/);
  assert.match(workflow,/ANDROID_KEY_ALIAS/);
  assert.match(workflow,/ANDROID_KEY_PASSWORD/);
  assert.match(workflow,/assembleRelease/);
  assert.match(workflow,/app-release\.apk/);
  assert.match(workflow,/expense-mail-release-apk/);
});
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `npm test -- tests/android-contract.test.cjs`

Expected: FAIL because the current Gradle module and workflow only describe the debug APK path and no properties example exists.

### Task 2: Implement repository-safe release signing

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/keystore.properties.example`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: optional `android/keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; CI environment variables `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
- Produces: a `release` build type signed when all four values exist, with debug builds unaffected.

- [ ] **Step 1: Add the ignored local signing paths**

Add these lines to `.gitignore` under the credentials section:

```gitignore
android/keystore.properties
android/release.keystore
```

- [ ] **Step 2: Add the safe example properties file**

Create `android/keystore.properties.example` with placeholders only:

```properties
storeFile=../release.keystore
storePassword=CHANGE_ME
keyAlias=expense-mail
keyPassword=CHANGE_ME
```

- [ ] **Step 3: Implement lazy signing-value loading**

In `android/app/build.gradle.kts`, load the optional root-project `keystore.properties` file, then fall back to the four environment variables. Compute `hasReleaseSigning` only when all values are non-blank. Detect `assembleRelease` in `gradle.startParameter.taskNames` and throw:

```text
Release signing is required. Create android/keystore.properties or provide ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD.
```

when release assembly is requested without complete values. Create the `release` signing config and attach it to `buildTypes.release` only when the values are complete; leave the existing `debug` build unmodified.

- [ ] **Step 4: Run focused contract coverage**

Run: `npm test -- tests/android-contract.test.cjs`

Expected: PASS for the new signing configuration and example file.

### Task 3: Build and publish the signed release APK in CI

**Files:**
- Modify: `.github/workflows/android-apk.yml`

**Interfaces:**
- Consumes: GitHub repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
- Produces: `expense-mail-release-apk` containing `android/app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 1: Add the keystore restoration step**

Restore the base64 secret into `$RUNNER_TEMP/expense-mail-release.keystore`, set file permissions to owner-only, and fail with a clear message if any required secret is empty.

- [ ] **Step 2: Build the signed release variant**

Keep the existing Android unit test command, then run `gradle -p android assembleRelease --no-daemon` with the keystore path and the three signing values mapped to the environment names consumed by Gradle.

- [ ] **Step 3: Upload the release APK**

Upload only `android/app/build/outputs/apk/release/app-release.apk` with artifact name `expense-mail-release-apk`. Keep `permissions: contents: read`.

- [ ] **Step 4: Run the focused contract coverage**

Run: `npm test -- tests/android-contract.test.cjs`

Expected: PASS with the release workflow declarations present.

### Task 4: Document direct APK distribution

**Files:**
- Modify: `README.md`
- Modify: `docs/design.md`

**Interfaces:**
- Consumes: the final Gradle and workflow configuration.
- Produces: exact instructions for generating a keystore, deriving SHA-1, setting GitHub Secrets, running the workflow, downloading the APK, and installing it.

- [ ] **Step 1: Rewrite the Android build section**

Explain the difference between debug testing and signed release distribution, name the `expense-mail-release-apk` artifact, and state that the APK must be installed with “unknown app” permission enabled when distributed outside Google Play.

- [ ] **Step 2: Add keystore and OAuth setup commands**

Document this command with user-selected passwords:

```powershell
keytool -genkeypair -v -keystore android/release.keystore -alias expense-mail -keyalg RSA -keysize 2048 -validity 10000
```

Document SHA-1 extraction:

```powershell
keytool -list -v -keystore android/release.keystore -alias expense-mail
```

State that the output SHA-1 belongs in the Google Cloud Android OAuth client for package `com.expensemail.android`.

- [ ] **Step 3: Add GitHub Actions secret mapping**

Document base64 encoding for PowerShell and the exact four secret names; explicitly warn not to commit the resulting keystore or passwords.

- [ ] **Step 4: Update project design notes**

Record that release signing is externalized and that the release OAuth SHA-1 must match the distribution keystore.

### Task 5: Verify the full change

**Files:**
- Test: `tests/*.test.cjs`
- Inspect: `git diff --check`, tracked credential paths, and Android build availability.

- [ ] **Step 1: Run all Node tests**

Run: `npm test`

Expected: all tests pass with zero failures.

- [ ] **Step 2: Run JavaScript and manifest checks**

Run: `npm run check`

Expected: `Server, browser JavaScript and manifest syntax OK`.

- [ ] **Step 3: Check the Android build toolchain**

Run: `Get-Command gradle -ErrorAction SilentlyContinue; Get-Command adb -ErrorAction SilentlyContinue; Test-Path android/local.properties`

If Gradle and the Android SDK are available, run `gradle -p android testDebugUnitTest --no-daemon`, create a temporary test keystore outside the repository, and run `gradle -p android assembleRelease --no-daemon` with temporary environment values. Otherwise, report that GitHub Actions is the release-build verification path.

- [ ] **Step 4: Verify repository hygiene**

Run: `git diff --check; git status --short; git ls-files '*.keystore' '*.jks' '*.apk' '*.aab'`

Expected: no whitespace errors and no tracked signing material or generated APKs.
