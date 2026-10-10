# First run and APK setup

1. Download/check out the complete `codex/reality-android` branch of `Kij0007/Reality`. It contains the updated Spring Boot account/ownership backend, web client, cloud deployment files and separate `android/` project. The Android-only ZIP does not contain the backend.
2. In Android Studio choose **Open** and select the Android folder containing `settings.gradle.kts`. Keep it separate from Spring Boot `src/main/resources/static`; do not import Android as Maven.
3. Select JDK 17 for Gradle under **Settings → Build, Execution, Deployment → Build Tools → Gradle**. Install SDK Platform 36 and Build Tools 35.0.0; sync the included Gradle wrapper. Spring Boot independently requires Java 21.
4. For the requested online use with your laptop switched off, follow the repository's [free hosting instructions](https://github.com/Kij0007/Reality/blob/codex/reality-android/deployment/README.md) for Neon Free + Render Free. Copy the actual Render HTTPS URL after deployment. No public service has already been provisioned by this project.
5. Open the deployed origin's `/api/health` and wait for `{"status":"UP"}`. Then open its `/` web page and register/sign in to verify database-backed access. An unauthenticated `/activities` request correctly returns 401; health alone does not establish database readiness.
6. Connect an Android 8/API 26 or newer phone or start an emulator. With the public HTTPS URL, either can use normal internet access. Your phone does not need the laptop to remain on or use the same network.
7. Set `BACKEND_BASE_URL` in `gradle.properties` to that public HTTPS origin before building debug, or use the **Settings** icon on the sign-in screen and save it. After sign-in settings are under **More → Settings**. Include a trailing `/`. Never enter Neon JDBC credentials in Android. Keep Backend clock at `Asia/Kolkata` for the Docker/cloud server. The unchanged initial debug default `http://10.0.2.2:8081/` is only for optional local development below.
8. Choose the `app` run configuration and device, then **Run**. **Test connection** checks public process health. Register with a unique username/display name/password, or sign in. Account submission warms a sleeping server using reads for up to 120 seconds before a single login/register request.
9. Username accepts 3–40 lowercase ASCII letters/digits/`_.-` after trimming/normalization. Display name is required and at most 80 Unicode code points. Password needs at least 12 code points and at most 72 UTF-8 bytes. The app never saves the password; IDs come from the server.
10. Create an activity with positive target, category, start date and weekdays; edit it; start a session; break; resume; finish; view history/progress/reports. Streaks evaluate finished scheduled days through server yesterday. Sign out from the Settings account card to revoke the current token.
11. Register a second account to verify separation. A new cloud database starts empty; legacy PC records are neither copied nor assigned to the first account automatically. See [MIGRATION.md](https://github.com/Kij0007/Reality/blob/codex/reality-android/deployment/MIGRATION.md) for reviewed import/ownership assignment.
12. Build debug from Studio, or run `.\gradlew.bat assembleDebug` in PowerShell from the Android directory. Output: `app/build/outputs/apk/debug/app-debug.apk`.
13. With USB debugging enabled, use `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to your phone and allow installation from your transfer/source app.
14. For release, supply `PRODUCTION_BACKEND_BASE_URL=https://YOUR_ACTUAL_RENDER_HOST/` using the real URL. Use Studio's signed APK/AAB wizard or the private signing environment variables in README.md. Keep signing keys/passwords outside Git. Unsigned release APKs cannot be installed as-is.

Debug/release have different application IDs. Debug permits local HTTP; release requires HTTPS and normal certificate verification. Devices need Android 8/API26+. Switching the configured backend requires a session for that backend; old tokens are never shared between servers.

## Optional development against your PC

This section applies only when you deliberately run a development server on the PC. It is not needed for the hosted application.

1. Start local PostgreSQL, supply `DATABASE_PASSWORD` privately to the Spring Boot process, and start the updated backend. Its default port is 8081. Confirm `http://localhost:8081/api/health` returns `{"status":"UP"}`.
2. An emulator reaches the PC at `http://10.0.2.2:8081/`, the initial debug default. Android's `localhost` is Android itself.
3. A physical phone uses `http://PC_LAN_IP:8081/`, replacing the address with the actual Windows `ipconfig` LAN IPv4. Both must be on the same trusted network, and the private-network firewall rule/listening interface must permit access. Emulator `10.0.2.2` does not work on a physical phone.
4. Register/sign in against this development backend. Its database and accounts are separate from the hosted service. Returning to the public HTTPS origin returns to the hosted data and requires that server's account.

If a write times out, inspect/refresh server state before repeating it; the operation may have committed. There is no offline write queue, email verification, self-service forgotten-password, social-login or account-delete endpoint.
