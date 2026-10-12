# Install, migrate and use Reality Offline

## Install

1. Copy the provided Reality Offline APK to your Android phone.
2. Open the APK in Files. If prompted, allow that file/transfer app to install applications and tap Install.
3. Open Reality Offline. It works with an empty database and needs no sign-in, internet, server URL or hosting.
4. Keep the data timezone at Asia/Kolkata if your existing Spring Boot application uses that zone. This preserves the existing calendar days and local timestamps.
5. Use Activities to create/edit a commitment, start a session, take/resume a break and finish it. Progress and Reports use the local database.

The offline app has its own application ID, so installing it does not overwrite the online Reality app. Updates using the same APK signing identity retain its local records.

## Move existing PC data

Migration is a one-time copy. The PC database and website remain unchanged. Stop creating/editing sessions while exporting so the selected snapshot is easy to compare.

The supplied PC export utility reads activities, schedules, sessions and breaks directly from the configured local PostgreSQL database. It must include inactive parent activities, not only the active-list REST endpoint. Database connection details stay on the PC and are not included in the JSON file.

Keep the two supplied PC files together:

- Export-Reality-Localhost.ps1
- reality-export.sql

They are in the complete repository's scripts directory and are also supplied separately for migration. You need Windows PowerShell 5.1 or newer and psql.exe, which comes with PostgreSQL for Windows. There is no Python/Node requirement.

Keep PostgreSQL and your existing localhost Spring Boot application running for this one-time export. Open PowerShell in the folder containing those two files and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Export-Reality-Localhost.ps1
```

The command defaults to ProjectPath D:\Commitment_tracker\reality, BaseUrl http://localhost:8081, source timezone Asia/Kolkata and output Downloads\Reality-localhost-backup.json in your Windows user folder. It reads the project's datasource configuration privately, prompts for a hidden password if needed, and uses read-only repeatable-read SQL. It compares the selected database's active activities, session IDs/details and current streaks with the running localhost APIs before saving. The script changes no records or Java files.

For another project/port/output, supply only the changed values:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Export-Reality-Localhost.ps1 -ProjectPath "D:\Commitment_tracker\reality" -BaseUrl "http://localhost:8081" -OutputPath "$env:USERPROFILE\Downloads\Reality-localhost-backup.json"
```

If the output already exists, choose a new OutputPath; -Force explicitly permits overwriting that output file. If psql is not found, supply -PsqlPath with the full path to your PostgreSQL installation's bin\psql.exe. Use -TimeZone only if the original server's JVM uses another actual zone. Never post a database password/token in chat.

For the later account-enabled backend, the script requires an explicit -OwnerId for your own account (and its bearer token supplied privately as REALITY_EXPORT_TOKEN) or -Unowned for the original shared history. It refuses to export everyone's private rows together. The original shared backend needs neither argument. API/database mismatches stop the export instead of silently producing partial data.

After obtaining the export JSON:

1. Copy that file to your phone through USB, local file transfer or another method you control.
2. Open Reality Offline → More → Backup & restore.
3. Tap Choose backup file and select the JSON in Android's file picker.
4. Review the validated record counts and source timezone.
5. Confirm Replace and restore. This replaces existing phone activities/sessions/breaks; it does not merge records or change PostgreSQL.
6. Compare activity counts, known completed-session history and current streak for the same day/timezone.
7. Save a fresh phone backup once the imported records are correct. Your laptop/server can then remain off.

Invalid/unsupported files are rejected before replacement. If the old database has no session/break history or schedule/start dates, an export cannot manufacture that history. Preserve your old database and inspect the export summary before proceeding.

## Export phone backup

Open More → Backup & restore → Export backup. Android asks where to save the JSON file. Choose a folder outside the app's private storage, such as Documents, and keep another copy safe. The file contains personal activity/history records and is not encrypted.

Export again periodically, and before uninstalling or clearing the app's storage. Restore can also transfer a complete snapshot to another installation; continuing to use both creates independent datasets, not synchronization.

## Android Studio/build

1. Open the android-offline directory containing settings.gradle.kts in Android Studio.
2. Select JDK 17 for Gradle. Install SDK Platform 36 and Build Tools 35.0.0.
3. Sync the included Gradle 8.13 wrapper. Internet is needed for the first build's dependency downloads.
4. Run the app on an Android 8/API 26 or newer phone/emulator. No backend setup is required.
5. From this directory run:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

The debug APK is app/build/outputs/apk/debug/app-debug.apk. On Linux/macOS use ./gradlew. With USB debugging and Android platform tools, install with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

For a release build, use Android Studio's Generate Signed App Bundle or APK wizard with your own keystore. Keep it and its passwords outside Git. No PRODUCTION_BACKEND_BASE_URL or hosting account is used by the offline project.

