# Existing backend behavior → local Android behavior

Reality Offline does not make REST calls at runtime. This document records the source API contracts that the local repositories replace. The Spring Boot/web/online clients remain intact.

| Local feature | Original method | Original route | Repository action | Local storage/result |
|---|---|---|---|---|
| Active activity list | GET | /activities | ActivityRepository.list | Active activity rows |
| Activity details | GET | /activities/{id} | ActivityRepository.get | Active activity row |
| Create | POST | /activities | ActivityRepository.create | New activity row |
| Edit/schedule | PUT | /activities/{id} | ActivityRepository.update | Updated active activity |
| Soft delete | DELETE | /activities/{id} | ActivityRepository.delete | active=false |
| All sessions | GET | /api/sessions | SessionRepository.list | All session rows |
| Session details | GET | /api/sessions/{id} | SessionRepository.get | Session row |
| Activity history | GET | /api/sessions/activity/{activityId} | SessionRepository.forActivity | Related sessions |
| Daily session history | GET | /api/sessions/activity/{activityId}/date/{date} | SessionRepository.forDay | Finished overlapping sessions and active seconds |
| Start | POST | /api/sessions/start | SessionRepository.start | Session start timestamp |
| Finish | PUT | /api/sessions/{id}/stop | SessionRepository.stop | End timestamp, closed break, active duration |
| Delete session | DELETE | /api/sessions/{id} | SessionRepository.delete | Session and dependent breaks deleted |
| Start break | POST | /api/sessions/{sessionId}/break | SessionRepository.startBreak | Break start timestamp and optional description |
| Resume | PUT | /api/sessions/{sessionId}/resume | SessionRepository.resume | Break end timestamp and duration |
| Break history | GET | /api/sessions/{sessionId}/breaks | SessionRepository.breaks | Related break rows |
| Daily progress | GET | /api/daily-progress/activity/{id}/date/{date} | ProgressRepository.day | Target minutes, finished work seconds, completed |
| Current streak | GET | /api/streaks/activity/{id} | ProgressRepository.streak | Count and lastEvaluatedDate |
| Monthly report | GET | /api/reports/activity/{id}/month/{month} | ReportRepository.month | Twelve-field monthly summary |

## Authoritative source fields

Activity: id, name, minimumDuration, category, active, startDate, scheduledDays, createdAt, updatedAt. minimumDuration is an integer number of minutes. scheduledDays contains uppercase Java DayOfWeek names. Category values are STUDY, FITNESS, WORK, LEARNING, HEALTH, PERSONAL and OTHER.

Session: id, activityId, startTime, nullable endTime and nullable duration. Break: id, sessionId, startTime, nullable endTime/duration and nullable description. Recorded durations use seconds. Database-generated IDs and all relationships are retained during restore; newly created local records use the local database.

Dates use ISO yyyy-MM-dd. LocalDateTime timestamps have no UTC offset. YearMonth input uses yyyy-MM. The data timezone defines calendar boundaries; imported wall times are not silently converted to UTC.

## Calculations retained

- Only finished sessions contribute to daily/report totals. Query finished sessions that overlap the day, clip start/end to the day, then subtract overlapping breaks. Work cannot be negative.
- Finish closes an active break at the same end time and records elapsed session time minus completed break time.
- Daily completion compares total seconds with minimumDuration × 60.
- Current streak starts at yesterday, walks backwards no earlier than activity.startDate, skips unscheduled weekdays and stops at the first missed scheduled day.
- Monthly duration includes days through today, including unscheduled work. Completion/missed/best streak evaluate only finished scheduled days through yesterday. Report start is no earlier than the commitment's start date.
- Monthly totalSessions counts finished sessions overlapping that report range. completionPercentage is rounded to two decimal places. currentStreak is the current overall streak; longestStreak is within the selected month.

There is no separate complete-activity endpoint, lifetime-best-streak entity, reminder/time-of-day scheduling API, or activate/restore API in the source backend.

## Full PC export requirements

An export must include all activities, including inactive rows; every session; every related break; original IDs; schedule/start date/target; nullable running state; and timestamps. Exporting only GET /activities is incomplete in the complete backend, because that list intentionally hides inactive rows. Session/break foreign keys still require their retained activity parents.

Current streak and monthly reports are derived data, not standalone database entities. Restoring the complete records and original timezone reproduces their calculation for the same as-of date. A historic export's streak is not frozen after later scheduled dates pass.

The PC migration utility is a one-time operation executed on the PC against PostgreSQL. It never puts database credentials in Android or in the backup. It consists of scripts/Export-Reality-Localhost.ps1 and scripts/reality-export.sql. SQL uses READ ONLY repeatable-read transactions; all source relationships/raw wall timestamps are retained. The PowerShell wrapper compares database/API identity and computed current streak/lastEvaluatedDate before saving. See SETUP.md for the exact invocation.

## Portable backup contract

The JSON root contains app="Reality", schemaVersion=1, exportedAt (UTC Instant), timeZone (ZoneId), source, activities, sessions, breaks and optional streaks. The three record arrays use the fields listed above. streaks contains activityId, activityName, currentStreak and lastEvaluatedDate; these snapshots are comparison evidence, not the future source of truth.

The importer accepts version 1 UTF-8 JSON, at most 20 MiB and 200,000 combined records. It checks unique positive IDs, parent relationships, enum/date/timestamp formats, required active-activity metadata, nonnegative durations and each record's start/end chronology. Breaks must not overlap each other; a break cannot precede the start of an unfinished session. Older inactive activities may retain nullable name/category/target/startDate/scheduledDays fields without losing their related history. Multiple running sessions are preserved because the original backend allows them. Passwords, account records, tokens and PostgreSQL credentials are excluded.

App version 1.0.1 retains historical break timestamps outside a finished session's recorded boundaries. Such history can exist after a source session was edited, and it is restored exactly rather than rewriting its session or break timestamps. Daily progress and monthly reports clip actual session/break overlap, matching the source calculations. Legacy unfinished breaks associated with finished sessions also remain as recorded; overlap calculations use the session's end when needed. The backup format stays schemaVersion=1 and the stored records are unchanged.

After user confirmation, one Room transaction replaces breaks, sessions and activities while retaining original IDs and setting the source timezone. An import generation change recreates the business navigation state, so prior records are not displayed after restoration. Subsequent local inserts use fresh database IDs. Existing PC data is not altered or synchronized.

The D:/Commitment_tracker/reality source inspected during migration contains an earlier incomplete Activity-only module and differs from the complete backend used to build this client. Its activity entity lacks schedule/startDate, and its listing uses findAll. Use the actual running application's database, not an assumed source-folder match; missing history cannot be invented.

## Verified personal migration snapshot

The latest actual localhost snapshot was exported read-only on 14 October 2026: 7 activities (6 inactive), 40 sessions, 28 breaks and one comparison streak snapshot. BackupCodec decoding/encoding retained every ID, timestamp, nullable field and relationship, including a historical break outside its finished session's edited end. The local calculation returned currentStreak=10 through 13 October, matching the export snapshot and actual Spring Boot streak API. October's monthly longest/best streak is 10. All twelve October 2026 report fields matched the actual report API.

The private JSON is delivered separately as Reality-localhost-backup-2026-10-14-1833.json. It is not a runtime fixture, APK asset or public repository file. The export/database checks and standalone calculation checks do not by themselves prove a successful on-phone Room restore; that result is recorded separately in VERIFICATION.md.

