# Reality Offline troubleshooting

## APK does not install

Use Android 8/API 26 or newer. Open the APK using Files and allow installation from that specific source when prompted. A later APK with the same application ID must be signed with the same key; do not uninstall just to bypass a signing mismatch before exporting your existing records.

## Data is empty after installation

The offline app is a separate installation from the online app. It does not automatically read PostgreSQL, the web browser or another Android app's private storage. Import the already supplied Reality-localhost-backup-2026-10-14.json from More → Backup & restore → Choose backup file. It contains the checked 7 activities, 39 sessions and 28 breaks. Export again only if you need newer PC records. A new offline installation starts empty.

## Restore file is rejected

Use a Reality version 1 UTF-8 backup/export JSON file, not a PostgreSQL SQL dump, screenshot, ZIP or a copied web page. The importer checks the format/version, dates/enums/IDs, relationships and valid session/break intervals before changing the database. Files are limited to 20 MiB and 200,000 combined records. Keep the original file and read the displayed validation message. Do not manually guess missing IDs or edit duration values to make the file pass.

## Restore would overwrite records I created on the phone

Cancel. Export the phone dataset first. Restore is complete replacement after confirmation, not a merge; importing unrelated IDs without a deliberate merge policy could connect the wrong records. After cancellation, existing data remains available.

## Streak differs from an old screenshot

Compare the same activity, current date and original data timezone. Reality evaluates completed scheduled days through yesterday, skips non-scheduled days and stops at the first missed scheduled day. Today's completion does not yet increase that streak. A missed scheduled date after the old export can correctly reduce the present streak.

Check imported start date, repeat weekdays, minute target and finished session/break history. Backend streaks were calculated, not stored as an independent counter. Missing source history cannot be reconstructed from a single visible streak number.

## Reports are lower than the running timer

Only finished sessions count toward daily/report totals. Finish the session; any open break is closed automatically. Break time is excluded. The target is minutes while stored work is seconds. Monthly work can include today; completed/missed days and monthly best streak stop at yesterday.

## A session continued while the app was closed

That is intentional. Session/break start timestamps are stored locally, and the displayed timer is reconstructed when you reopen the app. Finish or resume it normally. No internet connection or laptop is involved.

## I changed the phone clock/timezone

History is based on the selected data timezone, initially Asia/Kolkata, to match the source server's LocalDateTime values. Keep the device clock accurate. Changing time can affect ongoing elapsed values; changing the data timezone can change calendar-day interpretation. Check settings before comparing migrated reports.

## Backup file cannot be found

Check the location you selected in Android's Save file picker. It is separate from the app-private database. If you selected a cloud document provider, saving there may require that provider's internet connection; choose local Documents/Downloads for fully offline export.

## Data disappeared after uninstalling/clearing storage

Android removes app-private data when the app is uninstalled or its storage is cleared. Reinstall and restore an exported backup. The offline application does not have a server copy or automatic account recovery.

## PC export connection fails

The one-time export needs the actual PostgreSQL service and correct database connection on the PC. It does not require installing PostgreSQL on the phone. A running localhost web page alone is not proof that its database matches the source folder you selected. Confirm the database used by your running application, keep credentials private, and avoid exporting while mutating records.

Keep reality-export.sql beside Export-Reality-Localhost.ps1. If psql.exe is missing from PATH, pass -PsqlPath with its full PostgreSQL bin path. If a backup file already exists, choose a new output filename or intentionally supply -Force. Database/API/streak mismatch errors mean no file was saved; check ProjectPath, BaseUrl, owner selection and original JVM TimeZone. For an account-enabled server, select your own OwnerId and supply its token privately; shared original data needs no login. Unowned legacy rows are hidden by the upgraded APIs and fail the required identity comparison rather than bypassing account isolation.

If your environment cannot execute psql, the optional export-localhost.py fallback needs Python 3.10+ and python -m pip install -r requirements-export.txt. It performs the same read-only export; Python is not needed for the installed Android application or the primary PowerShell/psql option.

## Android Studio build fails

Open the extracted Reality-Offline folder containing settings.gradle.kts. In the repository this folder is named android-offline; the Spring Boot Maven root is a different project. Use JDK 17, SDK Platform 36 and Build Tools 35.0.0, then sync the included wrapper. Initial Gradle downloads need internet. See VERIFICATION.md for the environment in which the supplied APK was actually built and tested.

