# Reality Android backend integration

## Inspected authority

Repository: https://github.com/Kij0007/Reality
Revision: 09a7660d023c8e6d8a7c681c26daf7540d04ed18 (main at inspection).

All six controllers, all ten DTOs, three entities, enum, three repositories, six service interfaces/implementations, exception handlers, Maven dependencies and application configuration were inspected. No pagination, query parameters, authentication, Spring Security or CORS policy exists in this revision. Native Android does not require browser CORS changes.

**The inspected GitHub main tree contains no HTML/CSS/JavaScript frontend files.** The installed web frontend at D:/Commitment_tracker/reality/src/main/resources/static was inspected after local execution recovered. Its api.js calls all 18 endpoints below; its dashboard, activity, schedule, session, progress and report workflows are the behavioral reference for native feature parity. The Android contract was also independently verified against the GitHub backend.

## Complete API inventory

All paths are appended to the configured base URL (including a configured context path). All calls are unauthenticated in the existing contract. JSON writes send application/json. Successful activity deletion ignores the response body because Spring may label its plain string as JSON; successful session deletion has no body.

| Android feature | Controller | Method | Endpoint | Request | Response | Success | Repository operation | Android API method |
|---|---|---|---|---|---|---|---|---|
| Activity list | ActivityController | GET | activities | none | List<ActivityDto> | 200 | ActivityRepository.list | ActivityApi.list |
| Activity details/edit load | ActivityController | GET | activities/{id} | none | ActivityDto | 200 | ActivityRepository.get | ActivityApi.get |
| Create activity/schedule | ActivityController | POST | activities | ActivityRequest | ActivityDto | 201 | ActivityRepository.create | ActivityApi.create |
| Edit activity/schedule | ActivityController | PUT | activities/{id} | ActivityRequest | ActivityDto | 200 | ActivityRepository.update | ActivityApi.update |
| Remove activity | ActivityController | DELETE | activities/{id} | none | plain text ignored | 200 | ActivityRepository.delete | ActivityApi.delete |
| Start tracking | SessionController | POST | api/sessions/start | SessionRequest | SessionDto | 201 | SessionRepository.start | SessionApi.start |
| Finish tracking | SessionController | PUT | api/sessions/{id}/stop | none | SessionDto | 200 | SessionRepository.stop | SessionApi.stop |
| All history/open sessions | SessionController | GET | api/sessions | none | List<SessionDto> | 200 | SessionRepository.list | SessionApi.list |
| Session details | SessionController | GET | api/sessions/{id} | none | SessionDto | 200 | SessionRepository.get | SessionApi.get |
| Activity history filter | SessionController | GET | api/sessions/activity/{activityId} | none | List<SessionDto> | 200 | SessionRepository.forActivity | SessionApi.forActivity |
| Day history filter | SessionController | GET | api/sessions/activity/{activityId}/date/{date} | none | SessionDayDto | 200 | SessionRepository.forDay | SessionApi.forDay |
| Delete session | SessionController | DELETE | api/sessions/{id} | none | empty | 204 | SessionRepository.delete | SessionApi.delete |
| Take break | SessionBreakController | POST | api/sessions/{sessionId}/break | optional BreakRequest | BreakDto | 201 | SessionRepository.startBreak | SessionBreakApi.start |
| Resume work | SessionBreakController | PUT | api/sessions/{sessionId}/resume | none | BreakDto | 200 | SessionRepository.resume | SessionBreakApi.resume |
| Break history/state | SessionBreakController | GET | api/sessions/{sessionId}/breaks | none | List<BreakDto> | 200 | SessionRepository.breaks | SessionBreakApi.list |
| Daily target/progress | DailyProgressController | GET | api/daily-progress/activity/{activityId}/date/{date} | none | DailyProgressDto | 200 | ProgressRepository.day | DailyProgressApi.day |
| Current streak | StreakController | GET | api/streaks/activity/{activityId} | none | StreakDto | 200 | ProgressRepository.streak | StreakApi.get |
| Monthly report | ReportController | GET | api/reports/activity/{activityId}/month/{month} | none | MonthlyReportDto | 200 | ReportRepository.month | ReportApi.month |

All IDs are server-issued Long path values. There are no query parameters. Date paths are yyyy-MM-dd; month paths are yyyy-MM. Retrofit interfaces in data/remote/api implement these exact routes; screen ViewModels call the corresponding repository operations above, not Retrofit directly.

## JSON contracts and units

- ActivityRequest: name:String, minimumDuration:Int (minutes), category:uppercase enum, scheduledDays:array of uppercase weekdays, startDate:ISO date.
- ActivityDto: the request fields plus id:Long, active:Boolean, createdAt/updatedAt:local timestamp. Legacy fields can be null; Kotlin response models handle those explicitly.
- ActivityCategory: STUDY, FITNESS, WORK, LEARNING, HEALTH, PERSONAL, OTHER.
- Weekdays: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.
- SessionRequest: activityId:Long.
- SessionDto: id, activityId, startTime, endTime, duration. endTime/duration are null while running. duration is seconds.
- BreakRequest: optional description:String. Android limits notes to 255 characters to match the entity's default String column capacity.
- BreakDto: id, sessionId, startTime, endTime, duration, description. Open break has null endTime/duration; description is nullable. duration is seconds.
- SessionDayDto: activityId, date, totalDuration:seconds, sessions:array.
- DailyProgressDto: activityId, activityName, date, minimumDuration:minutes, totalDuration:seconds, completed:Boolean.
- StreakDto: activityId, activityName, currentStreak:Int, lastEvaluatedDate:date.
- MonthlyReportDto (12 fields): activityId, activityName, year, month, totalDuration:seconds, totalSessions, scheduledDays:Int count, completedDays, missedDays, completionPercentage:Double (0–100), currentStreak, longestStreak.
- ErrorDto: timestamp, status, error, message, path. Android also accepts safe HTTP fallback errors, missing/empty bodies and malformed error JSON without crashing.

## Lifecycle and business rules

Activity list/detail reads are active-only. DELETE sets active=false and preserves records; no restore endpoint exists. Android confirms removal and asks the user to finish open sessions first.

The backend permits multiple open sessions and sessions on unscheduled dates. Android prevents an accidental duplicate open session for the same activity as a client UX guard, while still displaying/managing existing server sessions. Break state is derived from returned break rows. Unknown break state never means running: pause/resume controls are disabled until loaded; stopping is still supported. Stop closes an open break automatically.

Activity service requires positive minimumDuration, nonnull startDate and at least one weekday. Android additionally provides required name/category selection and sensible database-length constraints. These are UX validation, not a claim of extra backend annotations.

Daily progress reads only ended sessions overlapping the selected day. Today is unfinished for streak/report accountability. Monthly duration can include ended work today and unscheduled days; completed/missed/scheduled counts stop at yesterday. longestStreak is selected-month-only. currentStreak is current even for historical reports. Editing the current activity schedule/target recalculates historical accountability; no schedule-version history API exists.

## Time handling

LocalDate is yyyy-MM-dd. YearMonth is yyyy-MM. LocalDateTime is an ISO local value, potentially with fractional seconds, **without a zone offset**. Android never silently interprets it as UTC. A DataStore server-zone preference (default Asia/Kolkata) interprets live estimates and chooses day/month boundaries. Configure it to match the actual JVM timezone. Stored backend durations always remain authoritative. Supporting independent worldwide user time zones requires an explicit future backend contract, not a client-side guess.

## Known existing backend limitations; no Android backend edits

The checked GitHub revision predates the separately delivered web-integration Java repairs. Android adds no backend modifications.

| Existing file/behavior | Problem | Minimal backend repair previously identified |
|---|---|---|
| Activity.java manual no-args constructor plus Lombok @NoArgsConstructor | Duplicate generated constructor can prevent compilation | Remove only the duplicate Lombok annotation |
| ErrorResponse.java manual all-args constructor plus @AllArgsConstructor | Duplicate generated constructor can prevent compilation | Remove only the duplicate Lombok annotation |
| SessionServiceImpl.deleteSession deletes parent with break children | Foreign key can reject deletion | Transactionally delete break children first |
| SessionServiceImpl.calculateOverlapDuration uses raw elapsed overlap | Daily/report/streak totals can include break time | Subtract each break's overlap clipped to the requested day, clamp at zero |
| SessionServiceImpl.stop/delete lack one encompassing transaction | Related updates/deletion are not atomic | Add transaction boundary to those operations |
| GlobalExceptionHandler lacks IllegalArgumentException handler | Invalid session transitions can become generic 500 | Return existing ErrorResponse with 400 for these known transitions |
| Old activities have null startDate | Streak/report calculations dereference missing dates | User chooses actual commitment start date and weekdays via activity edit |

Use the already working/fixed backend deployment where available. Android displays returned values and cannot correct server totals or foreign keys without changing authoritative business logic. These limitations are documented separately instead of silently rewriting Spring Boot.

## Error and retry strategy

404 resources, 400 validation and any 401/403/409/422 supplied by a later deployment receive readable messages; 500 and network/timeout/JSON failures have safe UI errors and retry. Cancellation is rethrown. There is no offline-success fallback and no automatic retry for a write. If a connection drops after sending a mutation, the app states that the result is uncertain and asks the user to refresh/check server state first.
