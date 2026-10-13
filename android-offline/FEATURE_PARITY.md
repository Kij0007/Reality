# Reality Offline feature parity

The local implementation preserves the business behavior of Reality's six original business controllers. Accounts, server configuration and hosting belong to the online client and are deliberately absent from the offline client.

| Existing feature | Existing implementation | Offline Android implementation | Notes |
|---|---|---|---|
| Dashboard | Activity/session/progress APIs and web calculations | Native Home screen using local repositories | No sample fallback |
| Activities list/detail | ActivityController | Activities and detail screens | Active list; retained inactive history |
| Search/category/sort | Web/native client filters | Native list filters | Local UX |
| Create/edit activity | ActivityController and ActivityServiceImpl | Native form and Room writes | Same fields; native validation |
| Delete activity | Soft DELETE | Confirmed local soft deletion | Sessions/breaks retained |
| Categories | ActivityCategory enum | Exact seven categories | STUDY, FITNESS, WORK, LEARNING, HEALTH, PERSONAL, OTHER |
| Schedule | startDate + scheduledDays | Schedule screen and activity editor | Repeating weekdays; no invented time-of-day/reminders |
| Start session | SessionController/SessionServiceImpl | Local transactional start | Locally generated persistent database ID |
| Finish session | SessionServiceImpl | Local transactional finish | Closes an open break; stores active seconds |
| Break/resume | SessionBreakController/SessionBreakServiceImpl | Native tracking controls and local transactions | Illegal transitions rejected |
| Break descriptions/history | BreakRequestDTO/BreakResponseDTO | Session detail/history | Exact notes retained |
| Session list/detail/activity/day | SessionController | Tracking/detail/day history | Includes retained inactive activity history |
| Delete session | SessionController | Confirmation and Room transaction | Dependent breaks removed |
| Daily completion | DailyProgressServiceImpl | Progress screen and local calculation | Finished work ≥ target minutes × 60 |
| Current streak | StreakServiceImpl | Progress screen and local calculation | Scheduled successful days through yesterday |
| Monthly reports | ReportServiceImpl | Reports screen and local calculation | All twelve report fields |
| Session timers | Native timestamp calculation | Timestamp-backed native timer | Works while app is closed; no per-second database writes |
| Theme/timezone | Android preferences | Local settings | Light/dark/system; original timezone preserved |
| Backup export | New client capability | Save JSON through Android file picker | Activities, sessions, breaks, metadata and timezone |
| Restore | New client capability | File validation, summary and replacement confirmation | Atomic local replacement; no silent merge |
| PC data migration | Existing PostgreSQL records | One-time export file and Android restore | Includes inactive activity parents and full history |
| Online registration/login/logout | New online-only account APIs | Not used | One phone installation has one local dataset |
| Backend URL/network connection | Online client | Not used | No INTERNET permission |
| Web/other-phone synchronization | Online shared backend | Not supplied | Offline backups transfer copies explicitly |

Daily totals/reports exclude ongoing sessions until finished, as the existing repository query does. Today's duration may appear in monthly totals, while completion, missed days and streak evaluation stop at yesterday. The current streak in a monthly report is the present overall streak; longestStreak is the best streak inside the selected month.

Migration does not claim that a stored streak row exists: the backend calculates it from activity targets/start dates/weekdays, finished sessions and breaks. Those source records must all be exported. Backend accounts/passwords/tokens are not copied to the offline app.

