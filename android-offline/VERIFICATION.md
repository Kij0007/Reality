# Reality Offline verification

The supplied offline APK was built and tested independently of the existing online application. The verified runtime/test commit is `c4ec1465388aa140fb39669cf58ea849923a6f9d`. Later documentation changes do not change the compiled application.

## Actual results

| Check | Result |
|---|---|
| Repository/business-rule audit | Six original business controllers/services, entities and web/native workflows inspected |
| Existing backend/web regression | PASS: Spring Boot API and real browser workflows on a disposable CI database; cloud workflow 38461619460 |
| Gradle debug compilation | PASS: `assembleDebug` on GitHub Actions Ubuntu with JDK 21 |
| Instrumentation APK compilation | PASS: `assembleDebugAndroidTest` |
| Gradle JVM tests | PASS: 86 tests, zero failures/errors/skips |
| Lint | PASS: zero errors; 36 warnings |
| Release optimization | PASS: `assembleRelease`, R8/resource shrinking; unsigned release artifact |
| Connected native tests | PASS: 12 tests, zero failures/errors/skips; Android API 35 emulator |
| Airplane-mode operation | PASS: Wi-Fi/data disabled and airplane mode checked before real Room/Compose tests |
| Native screenshots | PASS: six complete PNGs; dashboard, settings, backup, validation, tracking and report visually inspected |
| APK internet permission | PASS: built APK does not request INTERNET |
| APK signature | PASS: Android debug certificate, APK signature v2 verified with SDK apksigner |
| Export helper tests | PASS: six Python exporter safety tests |
| PowerShell helper syntax | PASS: no parser errors; native psql execution not claimed in this sandbox |
| Actual localhost export | PASS: private read-only snapshot; 7 activities, 40 sessions, 28 breaks |
| Actual private JSON round trip | PASS: Kotlin BackupCodec preserves all source records, IDs, timestamps, nullable fields and relationships |
| Actual current streak comparison | PASS: local calculation=10; exported snapshot=10; Spring Boot API=10; monthly best=10 |
| Actual October 2026 report comparison | PASS: all twelve local report fields equal the real Spring Boot response |
| Source archive | PASS: 96 files, wrapper integrity and ZIP contents verified; Room schema included; no private backup/build caches/keys |
| Android Studio graphical sync | Not run; Gradle builds performed directly |
| Installation on the user's physical phone | Not performed; copy/install/import steps remain for the user |

Completed build and emulator evidence: [Reality Offline workflow run 38461619492](https://github.com/Kij0007/Reality/actions/runs/38461619492). Reports and six screenshots are supplied separately as Reality-Offline-verification.zip. The native tests run the real Hilt/Room application without replacing repositories with fakes. JVM tests use isolated test doubles where appropriate.

Lint warnings concern dependency/tool-version suggestions, KAPT versus KSP, a plural-resource suggestion, unused resources and monochrome launcher resources. There are no lint errors. The delivered installable APK is a development build signed with the Android debug certificate; release compilation was checked without a private signing key. A personally signed release uses the supplied secure signing configuration.

## Actual localhost migration

On 14 October 2026 the running localhost application and its configured PostgreSQL database were compared before this fresh export. The database contains 7 activities, including 6 inactive, with 40 sessions and 28 breaks. The prepared private file is Reality-localhost-backup-2026-10-14-1833.json. It includes inactive parent activities required for session relationships; GET /activities alone would omit them.

The optional pure-Python pg8000 exporter performed the actual export in a read-only, repeatable-read database transaction. The supplied Windows PowerShell/psql helper was syntax-checked. Both share the same SQL and API identity/streak comparison requirements. No records or existing Spring Boot source files were changed by this conversion/export.

The actual Kotlin BackupCodec decoded, validated, encoded and decoded the private file without changing its records. Older archived activities missing later schedule metadata were retained. The local engine returned currentStreak=10, equal to the export snapshot and localhost streak endpoint. Every one of the twelve October monthly-report fields matched the real report endpoint, including longestStreak=10. The source session history had changed since the previous export; the latest source data is authoritative.

One edited finished session retains a historical break outside its current timestamp boundaries. Version 1.0.1 preserves those raw source timestamps instead of dropping or changing the break. Existing report/progress calculations already clip actual overlapping intervals, so a wholly out-of-session break subtracts no work. Validation still checks parent IDs, break order, overlaps, nonnegative durations and boundaries for unfinished sessions. Four new JVM tests and a native Room restore test cover this compatibility case.

The private JSON remains outside GitHub, source ZIPs and the APK. The on-device restore tests use disposable fixture records, preserving this privacy. They prove atomic Room restore/export behavior; importing the user's private file on their actual phone is not claimed. Streaks continue to follow scheduled-day rules as time passes rather than keeping an old snapshot number forever.

## Connected coverage

The five storage tests check session/break transitions and active duration, soft deletion and child-break deletion, invalid replacement preserving data/timezone/revision, restored IDs followed by safe new inserts, and database reopen persistence. Two backup tests use Android ContentResolver to export, validate and restore real Room data, including archived rows, clock metadata and an out-of-session break in edited finished history; malformed files are rejected without changing records.

The native tests cover system navigation, startup with no INTERNET permission, invalid form submission without writes, and create → start → break with note → resume → finish → Activities tab → activity detail → monthly report, with assertions against actual persisted Room rows. This includes regression coverage for returning from tracking through a top-level tab: a tab opens its declared screen instead of restoring a mixed navigation stack. Two additional Compose tests verify legal tracking controls. Six PNG screenshots were exported during instrumentation, before Gradle uninstalled the disposable test application.

The local Windows Gradle build was attempted, but this restricted environment could not reliably run native Android tools. Full compilation, R8, lint and emulator checks therefore ran in the Linux CI environment above. The emulator tests exercise the debug variant on API 35; minimum-API-26 support is checked by configuration and lint, not a separate API 26 device run.

## Artifact identity

```text
Reality-Offline-debug.apk SHA256: f9a6c9172ec1504ff3bba10bfcc234015ec28161161cdc113d5285e5c68c9d74
Reality-localhost-backup-2026-10-14-1833.json SHA256: 000fa8736d0bc3bb3321f5a84edef1b613dc8d32f9f573e7cb250d08c4d79d1c
```

Keep backups before uninstalling or clearing storage. Later updates to the same application ID must use the same signing key to retain installed data.
