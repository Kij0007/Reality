# Reality Offline verification

This applies to the offline project and its separate APK. Historical online APK results are not reused as proof for Room or backup behavior.

## Verified now

| Check | Result |
|---|---|
| Source business-rule audit | Six original business controllers/services, entities and web/native workflows inspected |
| Pure local calculations | PASS: 13 standalone JVM tests |
| Backup format/validation | PASS: 11 standalone JVM tests |
| Optional Python exporter checks | PASS: six tests |
| Primary PowerShell exporter syntax | PASS: PowerShell parser reported no syntax errors |
| Actual localhost PostgreSQL export | PASS: read-only private snapshot; 7 activities, 38 sessions, 27 breaks |
| Personal backup round trip | PASS: all IDs, timestamps, nullable fields and relationships unchanged |
| Actual current streak comparison | PASS: local calculation=9; exported snapshot=9; Spring Boot API=9 |
| Actual October 2026 report comparison | PASS: all twelve local report fields equal the real Spring Boot response |
| Full Gradle debug/release compilation | CI running; no PASS claimed yet |
| Full Gradle JVM suite/lint | CI running; final counts/results pending |
| Connected Room/backup/native UI tests | Pending CI result |
| Airplane-mode UI flow/screenshots | Pending CI result |
| New offline debug APK signature | Pending built artifact |
| Android Studio graphical sync | Not run |

The new build is being checked by [Reality Offline GitHub Actions run 38304477737](https://github.com/Kij0007/Reality/actions/runs/38304477737). This link is evidence of the verification job, not a claim that it has completed successfully. Final delivery must record the completed build/test/lint/emulator results and APK checks.

## Actual localhost migration evidence

On 13 October 2026 the running localhost application and its configured PostgreSQL database were compared before export. The database contains 7 activities, including 6 inactive, with 38 sessions and 27 breaks. The prepared file is Reality-localhost-backup-2026-10-13.json. It contains the inactive parent activities needed for those relationships; GET /activities alone would omit them.

The optional pure-Python pg8000 exporter performed the actual read-only database export in this environment. The primary Windows PowerShell/psql script is supplied for the user's normal Windows/PostgreSQL installation; its syntax was checked, but its native psql execution is not claimed here. Both tools share the same read-only SQL and identity/streak comparison requirements.

The private file was decoded, validated and encoded with the actual Kotlin BackupCodec. Every source record, ID, timestamp, nullable field and relationship survived unchanged. Older archived activities missing later scheduling metadata were preserved. The actual local calculation returned currentStreak=9, equal to the export snapshot and the real Spring Boot streak endpoint. All twelve current October monthly-report fields matched the real report endpoint.

No database records or existing Spring Boot source files were changed. The personal snapshot is supplied privately and is not committed to GitHub, packaged inside the APK or used as a runtime demo. These export/standalone checks do not yet establish an on-device Room restore; the connected tests must establish that separately.

## Current native test coverage

The connected suite exercises the real Hilt/Room application in addition to isolated Room/backup persistence tests. StartupNavigationTest verifies absence of INTERNET permission, dashboard/settings/backup navigation, invalid forms without database writes, and native create → start → break note → resume → finish → report with assertions against actual persisted Room rows.

scripts/verify-emulator.sh enables and checks emulator airplane mode, executes connectedDebugAndroidTest, and exports screenshots while instrumentation remains alive. Required images are dashboard, settings, backup, validation, tracking and report. It validates complete PNG headers/dimensions/trailers after collection. Tests and screenshots remain pending until this workflow passes.

## Required final checks

- Debug and instrumentation APK builds; full JVM tests; lint and release optimization.
- Native offline navigation and actual Room-backed tracking flow with airplane mode enabled.
- Room transaction/recreation persistence, imported IDs followed by fresh inserts, soft deletion and child-break deletion.
- Backup encode/decode/invalid-file handling and confirmed replacement without partial data loss.
- APK signature and source archive/file-manifest verification.
- Honest distinction between disposable emulator verification and installing/importing on the user's actual phone.


