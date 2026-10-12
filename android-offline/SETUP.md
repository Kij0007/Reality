# Install, migrate and use Reality Offline

## Install

1. Copy the provided Reality-Offline-debug.apk to your Android phone.
2. Open the APK in Files. If prompted, allow that file/transfer app to install applications and tap Install.
3. Open Reality Offline. It works with an empty database and needs no sign-in, internet, server URL or hosting.
4. Keep the data timezone at Asia/Kolkata if your existing Spring Boot application uses that zone. This preserves the existing calendar days and local timestamps.
5. Use Activities to create/edit a commitment, start a session, take/resume a break and finish it. Progress and Reports use the local database.

The offline app has its own application ID, so installing it does not overwrite the online Reality app. Updates using the same APK signing identity retain its local records.

## Import the backup already prepared for you

Your current localhost database was exported read-only on **13 October 2026**. The supplied **Reality-localhost-backup-2026-10-13.json** contains **7 activities (including 6 inactive), 38 sessions and 27 breaks**. The active activity's calculated **current streak is 9**, matching the localhost API. All twelve October 2026 monthly-report fields were also compared with the localhost response.

You do not need to run the export script to use this prepared snapshot:

1. Copy Reality-localhost-backup-2026-10-13.json to the phone alongside the APK.
2. Install/open Reality Offline.
3. Open **More → Backup & restore → Choose backup file** and select the JSON.
4. Review **7 activities, 38 sessions, 27 breaks** and timezone **Asia/Kolkata**.
5. Tap **Replace and restore**. If you already created phone records, export them first or cancel; restore replaces the whole local dataset.
6. Open Progress and Reports. Inactive activities remain hidden from the active list but their stored history remains available.
7. Export a fresh phone backup after checking the imported data. Your laptop, PostgreSQL and Spring Boot can then remain off.

This is a point-in-time copy, not synchronization. If you record more PC sessions after 13 October, create a newer export using the section below. The streak remains a live calculation: later missed scheduled days can change its current value. The personal JSON is supplied separately and is not committed to the public repository or embedded in the APK/source ZIP.

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

For the later account-enabled backend, use an explicit -OwnerId for your own signed-in account and supply that account's bearer token privately as REALITY_EXPORT_TOKEN. The original shared backend needs neither argument. The tool refuses to export everyone's private rows together. Although -Unowned selects owner-null legacy database rows, the upgraded authenticated APIs hide those rows, so the mandatory API checks will reject that selection; this is not an administrator bypass or a supported unowned-data export from the upgraded server. API/database mismatches stop export instead of silently producing partial data.

An optional Python fallback is provided for environments where psql cannot run. It requires Python 3.10+, the supplied export-localhost.py, requirements-export.txt and reality-export.sql together, and an initial dependency installation:

```powershell
python -m pip install -r .\requirements-export.txt
python .\export-localhost.py --project "D:\Commitment_tracker\reality" --base-url "http://localhost:8081" --output "$env:USERPROFILE\Downloads\Reality-localhost-backup.json"
```

This uses pinned pg8000 to perform the same read-only database export and API checks. It is optional; the primary Windows PowerShell/psql exporter needs no Python. These PC tools are used only for migration, not installed or run on Android.

After obtaining the export JSON:

1. Copy that file to your phone through USB, local file transfer or another method you control.
2. Open Reality Offline → More → Backup & restore.
3. Tap Choose backup file and select the JSON in Android's file picker.
4. Review the validated record counts and source timezone.
5. Confirm Replace and restore. This replaces existing phone activities/sessions/breaks; it does not merge records or change PostgreSQL.
6. Compare activity counts, known completed-session history and current streak for the same day/timezone.
7. Save a fresh phone backup once the imported records are correct. Your laptop/server can then remain off.

Invalid/unsupported files are rejected before replacement. Older inactive activities can lack metadata added later; these null fields are retained so their session/break history is not discarded. Active activities need valid metadata to calculate progress/streaks. Missing history cannot be manufactured. Preserve your old database and inspect the export summary before proceeding.

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


