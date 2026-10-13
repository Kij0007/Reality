# Reality Offline

Reality Offline is a native Android application that keeps activities, recurring schedules, sessions, breaks and history in an on-device Room/SQLite database. Daily progress, current streaks and monthly reports are calculated on the phone using Reality's existing business rules. Normal use needs no internet, laptop, Spring Boot process, PostgreSQL installation or hosting account.

This is a separate application from the online client. Its application ID is `com.reality.offline` (`com.reality.offline.debug` for debug); both clients can coexist. No login is required, and one local dataset belongs to this installation. There is no automatic synchronization with the web application or another phone.

## Architecture

Compose → StateFlow ViewModel → local repository → Room transaction/SQLite.

Hilt supplies dependencies. DataStore holds preferences. The existing native screens remain organized by dashboard, activities, tracking, scheduling, progress, reports and settings. Activity/session/break records are the authoritative data; calculated summaries are not substituted with sample records. The app has no INTERNET permission.

Backups use Android's Storage Access Framework: choose where to save a JSON file, or select a file to restore. File selection does not require broad storage permission. Restore validates the whole file and requires confirmation before replacing existing phone records.

## Features

- Dashboard, active activities, search/category/sort and activity details.
- Create/edit activity, positive minute target, category, commitment start date and repeat weekdays.
- Soft-delete an activity while retaining its session/break history.
- Start/finish sessions; start a break, resume and review break notes/history.
- Finished-session daily history and progress, current streak and monthly reports.
- Light/dark/system theme and an explicit data timezone, initially Asia/Kolkata.
- Export backup, inspect a restore file, and confirm complete replacement.
- One-time import of records exported from the existing PC PostgreSQL database.

Open **More → Backup & restore** to export or import. See [SETUP.md](SETUP.md) for installation and migration, [BACKEND_INTEGRATION.md](BACKEND_INTEGRATION.md) for business-rule mapping, and [VERIFICATION.md](VERIFICATION.md) for actual build/test results.

Your existing localhost records are already supplied separately in **Reality-localhost-backup-2026-10-14.json**: 7 activities, 39 sessions and 28 breaks. Import that file after installing **Reality-Offline-debug.apk**; no new PC export is needed unless you add more PC records. The current streak of 0, monthly best streak of 9 and all twelve October monthly-report fields matched the live backend calculation. October 13's scheduled target was not completed; the earlier history is preserved. See SETUP.md for the exact phone steps.

## Dates, duration and streaks

Reality's Spring Boot timestamps are LocalDateTime values without offsets. The imported timezone must match the old server's JVM timezone; the original project uses Asia/Kolkata. Timezone is stored with the backup. Switching phone location must not silently reinterpret historical wall times.

Activity targets are minutes. Session/break durations and report work totals are seconds. Daily/report work comes from finished sessions, clipped to calendar-day boundaries with overlapping break time removed. Running timers survive closing/reopening the app because start/break timestamps are saved, rather than counting Compose ticks.

The current streak counts completed scheduled days through yesterday. Unscheduled days are skipped; a missed scheduled day ends the streak. Import preserves the records from which the streak is calculated, rather than freezing a number forever. If additional scheduled days were missed after export, the current streak can correctly change. Monthly longest streak is a separate selected-month value.

## Data ownership and backups

The database is inside Android's app-private storage and survives app restarts/reboots. Uninstalling the app, clearing its storage or losing the phone can remove it. Export a backup outside app-private storage before uninstalling, and keep another copy somewhere safe.

The JSON backup contains personal activity/history data and is not encrypted by the app. It contains no PostgreSQL password, authentication token or server login. Choose a location you control and transfer it securely. Restore replaces the local dataset; it does not merge unrelated databases or update the PC backend. Version 1 backups are limited to 20 MiB and 200,000 combined records, including optional streak snapshots, to bound import memory.

## Build

Requirements: Android Studio compatible with AGP 8.13.2, JDK 17, SDK Platform 36/Build Tools 35.0.0, and Android 8/API 26 or newer. The included Gradle 8.13 wrapper needs internet for its first dependency download. The installed application does not.

Open this directory, containing `settings.gradle.kts`, in Android Studio. Do not put Android files under Spring Boot's static folder. From this directory:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

Linux/macOS: use `./gradlew` instead. The APK is `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone, open it from Files and allow installation from that source when prompted, or use `adb install -r`.

Release signing uses your own private keystore via Android Studio's signed APK/AAB wizard, or the existing private REALITY_KEYSTORE_* environment variables. No backend URL is needed. Do not commit keys/passwords. Keep your signing key: later updates must use the same identity to retain installed data.

