# Android Release APK Distribution Design

## Goal

Make the native Android app distributable as a signed release APK while keeping debug builds available for development and ensuring signing material never enters the repository.

## Scope

- Configure the Android app's `release` build type to use signing values supplied by a local `android/keystore.properties` file or environment variables.
- Fail the release build with an actionable message when signing values are absent.
- Update GitHub Actions to restore a base64-encoded keystore from repository secrets, run tests, build `assembleRelease`, and upload `app-release.apk`.
- Document keystore generation, SHA-1 registration for the Android OAuth client, GitHub Secrets, local release builds, and artifact installation.
- Preserve the existing debug build path and existing application behavior.

## Non-goals

- Publishing to Google Play or creating an Android App Bundle.
- Committing a keystore, OAuth token, client secret, or generated APK.
- Changing Gmail authorization or mail delivery behavior.

## Design

`android/app/build.gradle.kts` will load four values (`storeFile`, `storePassword`, `keyAlias`, and `keyPassword`) from `android/keystore.properties` first, then environment variables. The release signing configuration is attached only when all values are present. A release task without complete values fails during configuration rather than silently producing an unsigned artifact. Debug tasks do not require signing values.

The GitHub Actions workflow will keep `testDebugUnitTest` and add a release-signing step. The workflow will require `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` secrets, decode the keystore into a temporary ignored path, then expose the path and remaining values as environment variables for Gradle. It uploads `android/app/build/outputs/apk/release/app-release.apk` under a stable artifact name.

The README will distinguish debug testing from release distribution. It will include exact `keytool` commands, the SHA-1 command needed for Google Cloud, the four secret names, and the workflow steps. It will explain that the OAuth client's SHA-1 must match the release keystore used to build the APK.

## Verification

- `npm test`
- `npm run check`
- `npm test -- tests/android-contract.test.cjs`
- A Gradle release build with a temporary test keystore, proving the signed APK path works without committing credentials.
- A repository diff/secret scan confirming no keystore or token is tracked.
