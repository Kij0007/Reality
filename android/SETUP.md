# First run and APK setup

1. Download/check out the codex/reality-android branch of Kij0007/Reality. Keep the existing Spring Boot source and web deployment intact. The new Android project is in android/. If downloading the Android-project workflow artifact, extract its ZIP and open the extracted project folder directly.
2. In Android Studio choose Open and select android/ (the folder containing settings.gradle.kts). Do not import it as a Maven project.
3. Select JDK 17 for Gradle in Settings → Build, Execution, Deployment → Build Tools → Gradle. Install SDK Platform 36 and Build Tools 35.0.0 in SDK Manager if Studio requests them. Let the included Gradle wrapper sync dependencies.
4. Start PostgreSQL and your existing Spring Boot backend. Its configured local port is 8081. In the PC browser verify http://localhost:8081/activities returns a JSON list, including [] for an empty database.
5. Create/start an Android emulator in Device Manager, API 26 or newer. The default debug base URL http://10.0.2.2:8081/ reaches the development PC. Do not use Android localhost for the PC server.
6. If a different address is required, set BACKEND_BASE_URL in gradle.properties before building, or use More → Settings → Backend base URL inside the debug app. Save before testing the connection. The server zone defaults to Asia/Kolkata; change Backend clock only when your server uses another JVM zone.
7. Select the app run configuration and emulator, then Run. Open More → Settings and Test connection to check the actual activities API.
8. Test a normal flow: create an activity with positive target, category, start date and weekdays; view/edit it; start a session; take a break; resume; finish; view the saved session, daily progress and monthly report. Current streak counts fully finished scheduled days through server yesterday.
9. Build the debug APK from Android Studio, or run .\gradlew.bat assembleDebug in PowerShell from android/. The output is app/build/outputs/apk/debug/app-debug.apk.
10. With a device connected through USB debugging, run adb install -r app/build/outputs/apk/debug/app-debug.apk. Alternatively copy that APK to your phone and approve installation from your chosen file-transfer/source app.
11. For a phone on Wi-Fi, use the PC's LAN IPv4 address, the same trusted network, and a private-network firewall rule for the backend port. Emulator 10.0.2.2 is not the PC address for a physical phone.
12. For a release build, provide PRODUCTION_BACKEND_BASE_URL using your actual HTTPS deployment, then use Android Studio's signed APK/AAB wizard or the private signing environment variables described in README.md. Keep signing keys/passwords outside Git. An unsigned release APK cannot be installed as-is.

Debug and release use different application IDs so development does not replace a signed production installation. A debug build permits HTTP for local testing; release networking blocks cleartext HTTP and never disables certificate validation. APK installation requires Android 8/API 26 or newer.

The existing GitHub backend has no login. Hosting/reachability and protecting a public deployment remain server-side responsibilities; Android does not manufacture an authentication API.
