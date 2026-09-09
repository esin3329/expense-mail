# Android APK Gmail Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** Add a lightweight native Android APK that accepts camera/gallery evidence photos and automatically sends through Gmail when a valid recipient exists, otherwise saving an unsent Gmail draft.

**Architecture:** Keep the existing Apps Script web app as a reference and add an independent Android Views app under `android/`. The APK uses Google Play services `AuthorizationClient` to obtain a Gmail OAuth token, calls Gmail REST endpoints directly with `HttpURLConnection`, and builds MIME in a small pure Kotlin helper. A GitHub Actions workflow builds an installable debug APK because the current workstation has no Android SDK.

**Tech Stack:** Kotlin, Android platform Views, Google Play Services Auth, Android `HttpURLConnection`, GitHub Actions, Node contract tests.

**Spec:** `docs/design.md`

## Global Constraints

- Keep the APK dependency-light; do not add Compose or a large UI framework.
- Support Android camera capture and multi-select gallery input for JPG/PNG evidence.
- Allow at most 30 files and 18,000,000 total bytes; preserve original bytes.
- If the trimmed recipient is valid, submit after confirmation to Gmail `users.messages.send`.
- If the recipient is blank, submit after confirmation to Gmail `users.drafts.create`; never send in that branch.
- Store only the recipient locally; never commit OAuth client IDs, signing keys, or access tokens.
- Fail closed on an ambiguous Gmail result and do not retry automatically.
- The existing web app must keep passing its current tests.

---

### Task 1: Android project and behavior contracts

**Files:**
- Create: `tests/android-contract.test.cjs`
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/colors.xml`
- Create: `android/app/src/main/res/values/themes.xml`
- Create: `.github/workflows/android-apk.yml`

**Interfaces:**
- The contract test checks the package name, Gmail scopes, camera/gallery intents, `users.messages.send`, `users.drafts.create`, and that no real credential is committed.
- The CI workflow exposes `android/app/build/outputs/apk/debug/app-debug.apk` as an artifact.

- [x] **Step 1: Write the failing contract test** for the Android manifest, workflow, and source symbols before creating those files.
- [x] **Step 2: Run `npm test tests/android-contract.test.cjs`** and confirm it fails because the Android project is absent.
- [x] **Step 3: Add the minimal Gradle project, manifest, resources, and workflow** with the exact package and scope declarations needed by later tasks.
- [x] **Step 4: Run the contract test** and confirm the project shape and workflow pass.

### Task 2: Gmail MIME and REST client

**Files:**
- Create: `android/app/src/main/java/com/expensemail/android/MimeMessage.kt`
- Create: `android/app/src/main/java/com/expensemail/android/GmailClient.kt`
- Create: `android/app/src/test/java/com/expensemail/android/MimeMessageTest.kt`

**Interfaces:**
- `data class EvidencePart(val name: String, val mimeType: String, val bytes: ByteArray)`
- `object MimeMessage { fun build(sender: String, recipient: String, subject: String, body: String, files: List<EvidencePart>): String }`
- `class GmailClient(private val tokenProvider: () -> String)` with blocking `fun send(...)` and `fun createDraft(...)` methods called from the activity's background executor, returning the Gmail message/draft ID.

- [x] **Step 1: Write failing pure Kotlin tests** for Korean UTF-8 text, exactly one `To:` header when present, no `To:` header for drafts, and byte-identical attachments.
- [x] **Step 2: Run the Android unit test command in CI-compatible form**; this workstation has no Android SDK or Gradle installation, so the workflow is the execution path.
- [x] **Step 3: Implement MIME encoding and REST calls** using platform Base64/JSON and `HttpURLConnection`; make non-2xx or missing IDs terminal errors.
- [x] **Step 4: Run the unit tests**; verified by GitHub Actions run `34373257166`.

### Task 3: Android photo and submission UI

**Files:**
- Create: `android/app/src/main/java/com/expensemail/android/MainActivity.kt`
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Create: `android/app/src/main/res/xml/file_paths.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Camera uses `MediaStore.ACTION_IMAGE_CAPTURE` with a `FileProvider` URI.
- Gallery uses `ACTION_OPEN_DOCUMENT` with multiple JPG/PNG selection.
- `submit()` trims the recipient and chooses `GmailClient.send` when non-empty, otherwise `GmailClient.createDraft`.

- [x] **Step 1: Add UI contract assertions** for camera, gallery, recipient, subject, body, and the single submission button.
- [x] **Step 2: Implement lightweight Views UI** with touch-sized controls and local recipient persistence.
- [x] **Step 3: Implement URI validation and byte limits** before submission; show a review dialog with the chosen branch.
- [x] **Step 4: Implement Google authorization consent and token retrieval**, then invoke the Gmail client on a background executor and update the UI on the main thread.
- [x] **Step 5: Run static checks and unit tests**; verify blank recipient never selects the send endpoint through the contract suite.

### Task 4: GitHub-ready documentation and verification

**Files:**
- Modify: `README.md`
- Modify: `docs/design.md`
- Modify: `tools/setup.html`

- [x] **Step 1: Document Google Cloud OAuth setup, package/SHA-1 registration, GitHub Actions APK artifact download, and Android installation.**
- [x] **Step 2: Document the automatic branch: recipient present sends after confirmation; recipient blank saves an unsent draft.**
- [x] **Step 3: Run `npm test`, `npm run check`, and the local HTTP smoke check.**
- [x] **Step 4: Record that GitHub Actions is the Gradle build verification path because this workstation has no Android SDK or Gradle installation.**

## Verification

- Existing Node tests remain green.
- Android contract tests pass and reject missing Gmail endpoints or committed secrets.
- Kotlin MIME tests prove original attachment bytes are retained.
- GitHub Actions builds a debug APK artifact with no credential files committed.
