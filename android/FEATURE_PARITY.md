# Reality feature parity

Scope: 18 original business routes plus four account APIs and public health, across eight controllers on codex/reality-android. Original authority: 09a7660d023c8e6d8a7c681c26daf7540d04ed18. This branch includes the web client, real accounts, ownership and Android; cloud provisioning is separate.

| Existing Reality feature | Backend support | Web support | Android support | Notes |
|---|---|---|---|---|
| Dashboard active activities | Activity list | Dashboard | Home | Uses actual active-list response |
| Scheduled-today count/list | Activity schedule metadata | Dashboard/schedule | Home/schedule | Derived from selected date/start date/weekdays |
| Daily recorded time/targets | Daily progress | Dashboard/progress | Home/progress | Independent reads; missing results aren't fake zero |
| Running sessions | Session list + breaks | Dashboard/sessions | Home/Track | Server sessions survive reopening |
| Activity list/details | List and get | Activities | Activities/detail | Handles nullable legacy metadata |
| Search/category/sort | Client-side list operations | Activities filters | Activities filters | Does not claim server pagination/filter endpoints |
| Activity create | POST activities | Form | Native form | Exact request fields and enum values |
| Activity edit | PUT activities/{id} | Form | Native saved form | Native date input and weekday selectors |
| Activity deletion | Soft DELETE | Confirmation | Confirmation | No unsupported restoration toggle |
| Created/updated timestamps | Activity response | Details | Details | Local timestamp display |
| Activity recurring schedule | Start date + weekday set | Schedule/edit | Schedule/edit | No unsupported time-of-day or reminder controls |
| Start session | POST api/sessions/start | Start controls | Track controls | UX duplicate-tap/open-session guard |
| Finish session | PUT stop | Stop controls | Finish controls | Backend records duration and closes open break |
| Start break/optional note | POST break | Break dialog | Native break dialog | Note is optional; no fabricated state |
| Resume | PUT resume | Resume button | Resume button | Available only with known open break |
| Break history | GET breaks | Session detail | Session detail | Durations and notes from server |
| All session history | GET sessions | Sessions | Track/history | Includes soft-deleted activities by ID |
| Session details | GET session | Detail dialog | Session detail | Server duration authoritative |
| Activity history filter | GET activity sessions | Filter | Track activity filter | Uses actual endpoint |
| Day history filter | GET activity/date | Filter/progress | Track date filter/progress | Ended overlapping sessions per server |
| Session deletion | DELETE session | Confirmation | Confirmation | Permanent delete; handles 204 |
| Live timer estimate | Timestamps/break rows | Live timer | Live timer | No separate background persistence source |
| Daily target completion | Daily progress.completed | Progress | Progress | No invented manual complete API |
| Current streak/evaluated date | GET streak | Progress | Progress; Home shows the streak count | Through backend yesterday |
| Monthly report | GET report | Reports | Reports | All 12 response fields represented |
| Selected-month longest streak | Report.longestStreak | Reports | Reports | Not presented as lifetime best |
| Present current streak in historical report | Report.currentStreak | Reports | Reports | Label explicitly distinguishes it |
| Incomplete legacy schedules | Nullable activity metadata | Named dashboard edit guidance | Home/progress/reports edit guidance | User chooses actual date, never auto-saved |
| Loading, empty, error/retry | HTTP/network results | All views | All views | No fake production data |
| Destructive confirmations | Client UX | Dialogs | Material dialogs | API remains authoritative |
| Theme preference | Client-only | Existing visual theme | System/light/dark | DataStore; not a backend feature |
| Backend URL/server-zone preference | Client-only | Same-origin root | Settings | One configured service, no database access |
| Registration/sign-in | POST register/login | Account forms | Native AuthScreen | Server IDs, validation, BCrypt and opaque bearer |
| Restore account identity | GET api/auth/me | Session gate | Startup validation | User is tied to the current token |
| Logout/token revocation | POST api/auth/logout | Sign out | Settings AccountCard | 204; network failure is not fake success |
| Account-isolated data | Owner-scoped services/repositories | Signed-in user's records | Signed-in user's records | Foreign/unowned IDs return 404 |
| Encrypted Android session | 30-day opaque bearer | Browser session handling | Keystore AES-GCM + DataStore | No persisted passwords; matching 401 clears token |
| Backend-bound authentication | Token identifies one backend | Same-origin API | Configured-backend-bound session | Old token not reused on another server |
| Health/free-host warmup | GET api/health | Connection/startup flow | Settings test/account warmup | Read-only warmup up to 120 seconds; health does not query DB |
| Password-request throttling | Register/login filter | Readable 429 error | Readable 429 error | 60 requests/minute and four concurrent password requests per server instance; no automatic write retry |
| Free online packaging | Docker/cloud/Render Blueprint | JAR-packaged static resources | Configurable HTTPS URL | Render Free + Neon Free; no already-provisioned URL |
| Legacy owner migration | Nullable owner_id, guarded queries | Reviewed admin migration | Reviewed admin migration | First signup inherits nothing; no auto import |
| Password reset/email verification/account delete | Not present | Not invented | Not invented | No unsupported account/email APIs |
| Reactivation/restore | Not present | Not present | Not invented | Delete means inactive; no restore API |
| Weekly/category/global statistics | Not present | Not present | Not invented | No unsupported analytics or demo charts |

Runtime implementation is native Compose. Build/test evidence and the distinction between implemented coverage and runtime verification are recorded in VERIFICATION.md.
