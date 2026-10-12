# Reality Offline verification

This document applies to the new offline project, not the previously verified online APK. The online client's test counts do not establish that the Room, backup or migration code passes.

## Initial implementation status

| Check | Result |
|---|---|
| Full backend business-rule audit | Inspected six original business controllers/services/entities and existing web/native workflows |
| Current PC source comparison | Inspected D:/Commitment_tracker/reality read-only; Activity-only source differs from the complete repository |
| Room database and local repositories | Implementation in progress; no PASS claimed |
| Backup parser/export/replace transaction | Implementation in progress; no PASS claimed |
| One-time PC exporter | Integration/contract verification pending |
| assembleDebug / Kotlin compilation | Not yet run for this offline version |
| Unit tests | Not yet run for this offline version |
| lintDebug | Not yet run for this offline version |
| Release optimization | Not yet run for this offline version |
| Connected emulator/Room/backup/UI tests | Not yet run for this offline version |
| Airplane-mode runtime flow | Not yet verified |
| Debug APK signature | No new offline APK verified yet |
| User's real PostgreSQL export/import | Not yet performed; no personal data copied or modified |

Final delivery must replace this status with actual commands, run URLs/results, test counts, migration checks and artifact validation. No live export or successful personal-data migration is inferred from source inspection.

## Required meaningful checks

- Activity CRUD and soft deletion, including retained session/break relationships.
- Start → break → resume → finish; finish while on break; duplicate/illegal transitions.
- Finished work crossing day boundaries, clipped break subtraction, minute/second conversion.
- Scheduled-day current streak through yesterday, start-date bound, missed/unscheduled days.
- Monthly counts/percentage/current streak/month longest and ongoing-session exclusion.
- Backup round trip preserving IDs, nullable running state, inactive records, schedule/timezone.
- Invalid backup rejection without data loss and atomic confirmed replacement.
- Restored IDs followed by new inserts without collisions.
- Android file picker success/cancel/error handling and private storage persistence after recreation.
- No INTERNET permission/runtime API calls, no auth/server dependency, native navigation and empty/error/loading states.

