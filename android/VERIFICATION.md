# Reality Android verification

## Scope and source

All 18 endpoints in the six Spring Boot controllers were inspected at backend revision 09a7660d023c8e6d8a7c681c26daf7540d04ed18. DTO names, nullable legacy metadata, enum values, ISO date formats, status codes, error bodies, service rules and repository queries were reviewed. No backend or existing web files are changed by this Android project.

## Execution environment

Local Windows execution recovered after an initial helper failure. A workspace-local Android SDK and Gradle cache are used with JDK 21; GitHub Actions independently uses JDK 17. Bytecode targets Java 17. SDK 36, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 and compatible Compose BOM 2026.04.01 are pinned. OkHttp 5.4.0 is a stable version compatible with SDK 36; its published AAR metadata was checked. CI also runs Compose tests on an emulator.

## Current status

GitHub Actions run 37889405627 compiled the debug APK and passed the initial 17 JVM tests. Lint identified seven UI errors; observable locale formatting and resource lookups were corrected afterward. Combined compilation, expanded JVM tests, lint and emulator tests are being rerun for the corrected source. Final outcomes will be recorded before delivery; the initial run alone is not a passing final verification.

## Backend end-to-end limitations

The local Spring Boot server at http://localhost:8081 is reachable after execution recovered. An opt-in integration test exercises all 18 endpoints using uniquely named verification records and API cleanup. Set REALITY_TEST_BASE_URL to opt in; ordinary CI tests do not write to a live database. MockWebServer fixtures are confined to tests; runtime repositories use real Retrofit APIs with no mock fallback.

BACKEND_INTEGRATION.md identifies the existing main-branch backend limitations separately. A passing Android build does not repair or validate an unavailable Spring Boot deployment.
