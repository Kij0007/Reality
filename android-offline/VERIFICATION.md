# Reality Offline verification

The supplied offline APK was built and tested independently of the existing online application. The verified runtime/test commit is `a7d83b2e60cfd3f2a9af42f8b56d8b7a734c8e8d`. Later documentation changes do not change the compiled application.

## Actual results

| Check | Result |
|---|---|
| Repository/business-rule audit | Six original business controllers/services, entities and web/native workflows inspected |
| Existing backend/web regression | PASS: Spring Boot API and real browser workflows on a disposable CI database; cloud workflow 38455789324 |
| Gradle debug compilation | PASS: `assembleDebug` on GitHub Actions Ubuntu with JDK 21 |
| Instrumentation APK compilation | PASS: `assembleDebugAndroidTest` |
| Gradle JVM tests | PASS: 82 tests, zero failures/errors/skips |
| Lint | PASS: zero errors; 36 warnings |
| Release optimization | PASS: `assembleRelease`, R8/resource shrinking; unsigned release artifact |
| Connected native tests | PASS: 11 tests, zero failures/errors/skips; Android API 35 emulator |
| Airplane-mode operation | PASS: Wi-Fi/data disabled and airplane mode checked before real Room/Compose tests |
| Native screenshots | PASS: six complete PNGs; dashboard, settings, backup, validation, tracking and report visually inspected |
| APK internet permission | PASS: built APK does not request INTERNET |
| APK signature | PASS: Android debug certificate, APK signature v2 verified with SDK apksigner |
| Export helper tests | PASS: six Python exporter safety tests |
| PowerShell helper syntax | PASS: no parser errors; native psql execution not claimed in this sandbox |
| Actual localhost export | PASS: private read-only snapshot; 7 activities, 39 sessions, 28 breaks |
| Actual private JSON round trip | PASS: Kotlin BackupCodec preserves all source records, IDs, timestamps, nullable fields and relationships |
| Actual current streak comparison | PASS: local calculation=0; exported snapshot=0; Spring Boot API=0; monthly best=9 |
| Actual October 2026 report comparison | PASS: all twelve local report fields equal the real Spring Boot response |
| Source archive | PASS: 96 files, wrapper integrity and ZIP contents verified; Room schema included; no private backup/build caches/keys |
| Android Studio graphical sync | Not run; Gradle builds performed directly |
| Installation on the user's physical phone | Not performed; copy/install/import steps remain for the user |

Completed build and emulator evidence: [Reality Offline workflow run 38455789343](https://github.com/Kij0007/Reality/actions/runs/38455789343). Reports and six screenshots are supplied separately as Reality-Offline-verification.zip. The native tests run the real Hilt/Room application without replacing repositories with fakes. JVM tests use isolated test doubles where appropriate.

Lint warnings concern dependency/tool-version suggestions, KAPT versus KSP, a plural-resource suggestion, unused resources and monochrome launcher resources. There are no lint errors. The delivered installable APK is a development build signed with the Android debug certificate; release compilation was checked without a private signing key. A personally signed release uses the supplied secure signing configuration.

## Actual localhost migration

On 14 October 2026 the running localhost application and its configured PostgreSQL database were compared before export. The database contains 7 activities, including 6 inactive, with 39 sessions and 28 breaks. The prepared private file is Reality-localhost-backup-2026-10-14.json. It includes inactive parent activities required for session relationships; GET /activities alone would omit them.

The optional pure-Python pg8000 exporter performed the actual export in a read-only, repeatable-read database transaction. The supplied Windows PowerShell/psql helper was syntax-checked. Both share the same SQL and API identity/streak comparison requirements. No records or existing Spring Boot source files were changed by this conversion/export.

The actual Kotlin BackupCodec decoded, validated, encoded and decoded the private file without changing its records. Older archived activities missing later schedule metadata were retained. The local engine returned currentStreak=0, equal to the export snapshot and localhost streak endpoint. Every one of the twelve October monthly-report fields matched the real report endpoint, including longestStreak=9. The October 13 scheduled target was incomplete, so the current streak is now 0 while earlier history remains intact.

The private JSON remains outside GitHub, source ZIPs and the APK. The on-device restore tests use disposable fixture records, preserving this privacy. They prove atomic Room restore/export behavior; importing the user's private file on their actual phone is not claimed. Streaks continue to follow scheduled-day rules as time passes rather than keeping an old snapshot number forever.

## Connected coverage

The five storage tests check session/break transitions and active duration, soft deletion and child-break deletion, invalid replacement preserving data/timezone/revision, restored IDs followed by safe new inserts, and database reopen persistence. The backup test uses Android ContentResolver to export, validate and restore real Room data, including archived rows and clock metadata, and rejects malformed files without changing records.

The native tests cover system navigation, startup with no INTERNET permission, invalid form submission without writes, and create → start → break with note → resume → finish → Activities tab → activity detail → monthly report, with assertions against actual persisted Room rows. This includes regression coverage for returning from tracking through a top-level tab: a tab opens its declared screen instead of restoring a mixed navigation stack. Two additional Compose tests verify legal tracking controls. Six PNG screenshots were exported during instrumentation, before Gradle uninstalled the disposable test application.

The local Windows Gradle build was attempted, but this restricted environment could not reliably run native Android tools. Full compilation, R8, lint and emulator checks therefore ran in the Linux CI environment above. The emulator tests exercise the debug variant on API 35; minimum-API-26 support is checked by configuration and lint, not a separate API 26 device run.

## Artifact identity

```text
Reality-Offline-debug.apk SHA256: cd40d2f37469ff768c64aa8ec067092105efff0496837a09d52328dcd79bf7f2
Reality-localhost-backup-2026-10-14.json SHA256: 20a835032473d4bdc9e27620db9bbc81d39d6f8da2f451c007968dd9da14cfca
```

Keep backups before uninstalling or clearing storage. Later updates to the same application ID must use the same signing key to retain installed data.
