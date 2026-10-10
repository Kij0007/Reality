# Reality Android

Native Kotlin/Jetpack Compose client for the existing Reality Spring Boot REST backend. The Android project is independent of Maven and lives entirely in this directory. Open **this android directory** in Android Studio, not the Spring Boot project root.

## What is included

Native registration/sign-in, account display and logout; dashboard; activities with search/category/sort; activity creation, details, editing and soft deletion; recurring weekday/start-date schedule; session start/stop and history; break/resume with notes/history; daily progress, current streak and monthly reports; theme, backend URL and clock settings. Runtime records come from the signed-in user's API. There is no WebView, direct database connection, or demo fallback.

## Architecture

Compose → lifecycle-aware StateFlow ViewModel → repository → Retrofit/OkHttp → existing Spring Boot → PostgreSQL.

Hilt constructs dependencies. DataStore stores connection/theme/clock preferences and an encrypted account session. AES-GCM encryption uses an Android Keystore key; passwords are never saved. APIs/DTOs/repositories, ViewModels, screens, navigation and theme are separate. DTOs preserve backend JSON names; API dates remain ISO strings and java.time handles display. The API is authoritative for ownership, durations, completion, streaks and reports.

## Requirements

- Android Studio with support for Android Gradle Plugin 8.13.2.
- JDK 17 for Gradle (Android Studio's compatible bundled JDK is suitable). This is independent of the Spring backend's Java 21.
- Android SDK Platform 36 and Build Tools 35.0.0, installed from Android Studio's SDK Manager.
- Android 8.0/API 26 or newer on emulator/device.
- Internet access for the first Gradle sync, and a reachable existing Reality backend.
- The checked-in standard Gradle 8.13 wrapper includes its JAR and a pinned distribution checksum. No globally installed Gradle is needed.

## Backend URL: one place

Default debug URL is **http://10.0.2.2:8081/**. Port 8081 is taken from Reality's application configuration.

Change **BACKEND_BASE_URL** in **gradle.properties** before building debug, or use the Settings icon on the sign-in screen; after signing in use **More → Settings → Backend base URL**. Saved settings override the compiled default. Use a trailing slash and any actual context path, such as https://your-host.example/reality/. Changing servers requires signing in to the configured backend; tokens are not shared between servers.

For release, provide **PRODUCTION_BACKEND_BASE_URL=https://your-real-backend/** in your local Gradle properties or environment. There is deliberately no fake production server default. Release builds fail clearly until this is supplied and require HTTPS. The application rejects user-info credentials, query strings and fragments in the base URL.

## Online phone use

For everyday use with your laptop switched off, host Spring Boot on Render Free and PostgreSQL on Neon Free, then configure the actual Render HTTPS origin. Your phone can use mobile internet or any Wi-Fi; it does not need your laptop's network. Free hosting can sleep and has quotas. These files prepare hosting but do not create provider accounts or a live deployment.

The following PC/emulator and LAN instructions are optional local development setups.

## Local development: emulator

Start PostgreSQL and the updated Spring Boot app on the PC, with DATABASE_PASSWORD supplied privately. Wait for http://localhost:8081/api/health to return {"status":"UP"}. Run an emulator and the debug app, then register/sign in. Unauthenticated /activities now correctly returns 401. Android's 10.0.2.2 reaches the PC; Android localhost refers to Android itself.

## Local development: physical phone

Put the PC and phone on the same trusted LAN. Find the PC's LAN IPv4 address with Windows ipconfig. Save http://PC_LAN_IP:8081/ in the debug app's Settings. Ensure Spring Boot listens on an address reachable from the LAN, and allow its port through Windows Firewall for the trusted private network. Guest Wi-Fi isolation can prevent access. No router port forwarding is required for same-LAN use.

## Hosted server and account behavior

Deploy the existing Spring Boot service and its database separately from Android. Configure the actual public HTTPS base URL. Native Retrofit requests do not need browser CORS workarounds. Android connects only to Spring Boot, never to PostgreSQL. No database credentials belong in the app.

This branch includes Spring Security, registration/login, opaque bearer tokens, and owner-scoped activities and related data. Tokens expire after 30 days; logout revokes the current token and 401 clears its matching saved session. Restored sessions are validated through /api/auth/me. Foreign/unowned IDs return 404. First registration never inherits legacy unowned rows.

Free Render + Neon files are documented in [deployment/README.md](https://github.com/Kij0007/Reality/blob/codex/reality-android/deployment/README.md). No public URL has been provisioned by these files. Keep provider plans Free, account for cold starts/quotas, and configure Android with the actual Render HTTPS URL after deployment.

## Build and install debug APK

From this directory:

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

macOS/Linux:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android Studio: open this directory, let Gradle sync, choose the app configuration and emulator/device, and click Run. To generate an APK, use Build → Generate App Bundles or APKs → Generate APKs (menu wording can vary by Studio release). The output is **app/build/outputs/apk/debug/app-debug.apk**.

## Signed release

Configure PRODUCTION_BACKEND_BASE_URL first. Android Studio's Build → Generate Signed App Bundle or APK wizard can create/select your own keystore and generate a release APK or AAB. Keep the key and passwords outside Git and back up the key securely.

For command-line signing, supply these environment variables privately:

- REALITY_KEYSTORE_PATH: absolute path to your private keystore.
- REALITY_KEYSTORE_PASSWORD
- REALITY_KEY_ALIAS
- REALITY_KEY_PASSWORD

Then run **.\gradlew.bat assembleRelease** (or **./gradlew assembleRelease**). Without those signing values, the release artifact is unsigned; it must be signed before installation/distribution. No signing key/password is generated or committed by this project.

## Project structure

```text
android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/reality/android/
│       │   │   ├── RealityApplication.kt, MainActivity.kt
│       │   │   ├── core/network/, core/util/
│       │   │   ├── data/remote/, data/repository/
│       │   │   ├── di/
│       │   │   └── ui/auth/, activities/, tracking/, dashboard/, schedule/,
│       │   │          progress/, reports/, settings/, navigation/, theme/
│       │   └── res/
│       ├── debug/     (HTTP permission is restricted to this build)
│       ├── test/      (JVM tests and fake data only here)
│       └── androidTest/ (Compose behavior tests)
├── gradle/wrapper/   (standard wrapper including JAR)
├── gradle/libs.versions.toml
├── gradle.properties
├── settings.gradle.kts, build.gradle.kts, gradlew, gradlew.bat
├── scripts/verify-emulator.sh
├── BACKEND_INTEGRATION.md
├── FEATURE_PARITY.md
├── FILE_MANIFEST.md
├── SETUP.md
├── TROUBLESHOOTING.md
└── VERIFICATION.md
```

## Important behavior

- Activity deletion is soft deletion; there is no restore or separate activate API.
- Recurring schedules contain a start date and weekdays, without time-of-day or reminder APIs.
- Stopping records a session and closes an open break. Completion is the backend's daily target result, not a separate complete endpoint.
- Finished duration values, daily totals and reports are shown exactly as returned. This branch applies the minimal backend accounting/deletion repairs and adds ownership; see BACKEND_INTEGRATION.md.
- Live timers are explicitly estimates from returned timestamps and break records. They do not write time every second and do not run a separate background recording engine.
- Streaks evaluate through server yesterday; selected-month best streak and present current streak are distinct.
- Daily/report reads can fail independently. Missing dates/repeat days are named and editable without guessing historical metadata.
- Writes are not automatically retried after a timeout. A request may already have reached the server; refresh and inspect server state before trying again.
- Before account submission, a read-only health warmup waits up to 120 seconds for sleeping free hosting. It never repeats the account write. Settings Test connection checks process liveness; an authenticated read additionally checks database access.
- The backend limits login/registration password work on its single instance to 60 accepted attempts per minute and four concurrent requests. A rejected request returns 429 and a Retry-After header; wait before retrying.
- The API returns local timestamps without offsets. Set Settings → Backend clock to the server JVM's zone (default Asia/Kolkata for this project). This controls Android's date boundaries and timer interpretation; it does not change the server.

Read SETUP.md for first-run steps, BACKEND_INTEGRATION.md for all 23 routes, FEATURE_PARITY.md for coverage, and VERIFICATION.md for actual test evidence. There is no email verification, self-service password reset, account-delete API, or token-refresh endpoint.
