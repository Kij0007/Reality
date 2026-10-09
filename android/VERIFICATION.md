# Reality Android verification

## Scope and source

All 18 endpoints in the six Spring Boot controllers were inspected at backend revision 09a7660d023c8e6d8a7c681c26daf7540d04ed18. DTO names, nullable legacy metadata, enum values, ISO date formats, status codes, error bodies, service rules and repository queries were reviewed. No backend or existing web files are changed by this Android project.

## Execution environment

Local Windows execution recovered after an initial helper failure. A workspace-local Android SDK and Gradle cache are used with JDK 21; GitHub Actions independently uses JDK 17. Bytecode targets Java 17. SDK 36, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 and compatible Compose BOM 2026.04.01 are pinned. OkHttp 5.4.0 is a stable version compatible with SDK 36; its published AAR metadata was checked. CI also runs Compose tests on an emulator.

## Current status

GitHub Actions run [37898966489](https://github.com/Kij0007/Reality/actions/runs/37898966489), code commit 91888b1de829b869d6c557de288ca214b2dd8371, passed both the build and emulator jobs:

| Check | Result |
|---|---|
| Gradle configuration and assembleDebug | PASS; real debug APK generated |
| assembleDebugAndroidTest | PASS; instrumentation APK compiled |
| JVM tests | PASS: 67 passed, 0 failures/errors, 1 opt-in live test skipped |
| lintDebug | PASS: 0 errors, 27 warnings |
| assembleRelease with R8/resource shrinking | PASS; unsigned compile check with a temporary HTTPS URL, no production service contacted |
| connectedDebugAndroidTest | PASS: 4 tests on Android API 35 x86_64 Pixel 2 emulator |
| Native screenshots | PASS: 6 valid 1080 x 1920 PNG captures, visually inspected |
| Debug APK signature | PASS: Android APK Signature Scheme v2 verified |
| Android Studio graphical sync | Not run; command-line Gradle configuration/build was verified |

Tests cover all 18 API contracts, request bodies/statuses, error parsing, validation, timers/time zones, duplicate writes, configuration locking, settings-disk recovery, saved forms, stale data, request cancellation, report filters, break controls, actual Hilt application startup, navigation and local form validation. Of the 27 lint warnings, 26 concern newer dependency/SDK releases and one flags intentional debug-only cleartext HTTP for emulator/LAN development. Release blocks cleartext. The compatible SDK 36 toolchain is deliberately pinned; no lint baseline or warning suppression is used.

The independently openable source archive contains all 89 files, includes the wrapper JAR and executable Unix wrapper, and excludes build caches, machine-specific SDK paths and private signing files. Six native screenshots were exported during instrumentation before AGP uninstalled the debug app; CI validated their PNG structure. Home, Activities, Create Activity, Track, More and Settings were visually inspected in the offline/form state. These captures do not represent successful real database operations or a visual test of every populated screen.

Local Windows assembleDebug was attempted with JDK 21, including a shorter source path and two workers. Its native AAPT2 linker exited unexpectedly without an error message; the independent Linux Android build and emulator tests above passed. The failure is recorded rather than reported as a successful local build.

## Backend end-to-end limitations

The opt-in LiveBackendFlowTest was compiled and executed using the Android client's actual Retrofit interfaces and DTOs in a temporary JVM verification harness. Connection to both localhost:8081 and 127.0.0.1:8081 was refused before the first create request could reach Spring Boot. No test records were created. Real PostgreSQL persistence, server break/stop behavior and server-generated reports could therefore not be verified in this run.

The test exercises all 18 endpoints, waits for a genuine one-minute session, and cleans up uniquely named records through APIs when a server is available. To run it from the Android project in PowerShell:

```powershell
$env:REALITY_TEST_BASE_URL = 'http://localhost:8081/'
.\gradlew.bat testDebugUnitTest --tests 'com.reality.android.integration.LiveBackendFlowTest'
```

Ordinary CI tests skip this opt-in live test and do not write to a database. MockWebServer fixtures are confined to tests; runtime repositories use real Retrofit APIs without a mock fallback. Start the complete backend, confirm its activities API, and repeat the live flow for deployment verification.

BACKEND_INTEGRATION.md identifies the existing main-branch backend limitations separately. A passing Android build does not repair or validate an unavailable Spring Boot deployment.
