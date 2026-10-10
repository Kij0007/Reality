# Reality Android backend integration

## Inspected authority

Repository: https://github.com/Kij0007/Reality
Original revision: 09a7660d023c8e6d8a7c681c26daf7540d04ed18 (main at inspection). Current authority: the complete codex/reality-android branch, including accounts/ownership, public health, web files and the previously identified minimal Java repairs.

All original controllers, DTOs, entities, enum, repositories, services, exception handlers and configuration were inspected. The updated AuthController, HealthController, auth DTOs/entities/repositories, token filter/security configuration and owner-scoped services were also reviewed. The current branch has eight controllers and 23 method/path routes. There is no server pagination or API query-parameter contract. Native Android does not need browser CORS changes.

The original GitHub main tree contained no HTML/CSS/JavaScript. The working web client was inspected from its local installation after execution recovered and is now included under src/main/resources/static on this branch. Its dashboard/activity/schedule/session/progress/report workflows informed native parity; both clients also implement real account APIs. Android contracts were independently verified against controller/service source.

## Complete API inventory

All paths are appended to the configured base URL, including any actual context path. Register/login/health are public; every other route requires Authorization: Bearer TOKEN. All 18 business routes preserve their original JSON shapes while enforcing owner access. JSON writes send application/json. Activity deletion ignores its plain-text body; session deletion/logout have no body.

| Android feature | Controller | Method | Endpoint | Request | Response | Success | Repository operation | Android API method |
|---|---|---|---|---|---|---|---|---|
| Register account (public) | AuthController | POST | api/auth/register | RegisterRequest | AuthDto | 201 | AuthRepository.signIn with displayName | AuthApi.register |
| Sign in (public) | AuthController | POST | api/auth/login | LoginRequest | AuthDto | 200 | AuthRepository.signIn | AuthApi.login |
| Validate/restore user | AuthController | GET | api/auth/me | none | UserDto | 200 | AuthRepository.currentUser | AuthApi.me |
| Sign out/revoke token | AuthController | POST | api/auth/logout | none | empty | 204 | AuthRepository.logout | AuthApi.logout |
| Connection/free-host warmup (public) | HealthController | GET | api/health | none | HealthDto | 200 | AuthRepository.health | AuthApi.health |
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

- RegisterRequest: username, displayName, password (Strings). Username is trimmed/lowercased with Locale.ROOT and must match [a-z0-9_.-]{3,40}; display name is trimmed/required and at most 80 code points; password needs at least 12 code points and at most 72 UTF-8 bytes.
- LoginRequest: username:String, password:String.
- AuthDto: token:String (opaque), expiresAt:ISO Instant, user:UserDto. UserDto: id:Long, username:String, displayName:String, createdAt:ISO Instant. These auth timestamps carry offsets, unlike session/activity LocalDateTime.
- HealthDto: status:String, currently UP; process liveness without a PostgreSQL query.
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

## Backend repairs and ownership changes on this branch

The original main revision predates the web-integration repairs. Those four Java replacement files are now applied here. Account/ownership changes are also included for the user's separate-account public deployment request. Android remains a REST client.

| File/behavior | Original problem | Applied repair/current behavior |
|---|---|---|
| Activity.java manual no-args constructor plus Lombok @NoArgsConstructor | Duplicate generated constructor can prevent compilation | Remove only the duplicate Lombok annotation |
| ErrorResponse.java manual all-args constructor plus @AllArgsConstructor | Duplicate generated constructor can prevent compilation | Remove only the duplicate Lombok annotation |
| SessionServiceImpl.deleteSession deletes parent with break children | Foreign key can reject deletion | Transactionally delete break children first |
| SessionServiceImpl.calculateOverlapDuration uses raw elapsed overlap | Daily/report/streak totals can include break time | Subtract each break's overlap clipped to the requested day, clamp at zero |
| SessionServiceImpl.stop/delete lack one encompassing transaction | Related updates/deletion are not atomic | Add transaction boundary to those operations |
| GlobalExceptionHandler lacks IllegalArgumentException handler | Invalid session transitions can become generic 500 | Return existing ErrorResponse with 400 for these known transitions |
| Existing activities have null owner_id | Original records have no known account owner | They remain inaccessible until explicit reviewed migration; first signup inherits nothing |
| Owned legacy activities lack startDate/weekdays | Streak/report needs valid metadata | User chooses actual commitment metadata through Edit |

AuthService uses BCrypt password hashes and random 32-byte URL-safe opaque tokens; the server stores only token SHA-256 hashes. Tokens expire after 30 days and logout revokes the submitted token. Authentication uses bearer headers without cookies or a refresh-token endpoint. OwnedResources checks ownership and existence together; foreign/unowned IDs return 404, and lists query only the user's rows, including session history linked to inactive activities.

Android saves encrypted session metadata in DataStore using Android Keystore-backed AES-GCM. AuthInterceptor adds the bearer only for a usable session bound to the configured backend and invalidates only the matching token on 401. GET me validates restored sessions. Passwords never enter disk or SavedStateHandle. Account submission first warms public health for up to 120 seconds; it never retries the account write automatically.

Cloud configuration, free-host limits and explicit legacy ownership migration are documented under ../deployment/. No hosted URL is implied by generated code, and no database credential belongs in Android.

## Error and retry strategy

404 missing/foreign resources, 400 validation/state transitions, 401 invalid/missing/expired/revoked tokens and 409 duplicate usernames are real outcomes. Wrong username/password share the same 401 message. Login/registration can also return 429 with Retry-After: the single-instance filter accepts at most 60 password requests per minute and four concurrent requests, using bounded in-memory state. This limit resets when the process restarts and is not a distributed quota.

Other HTTP/JSON/network failures have readable fallbacks; cancellation is rethrown. No write is automatically retried or reported successful offline. After an uncertain signup, try signing in before creating the account again.
