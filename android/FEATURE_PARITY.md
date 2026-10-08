# Reality feature parity

Scope: all 18 APIs from the six controllers at inspected revision 09a7660d023c8e6d8a7c681c26daf7540d04ed18. Web behavior refers to the web client previously delivered in this task; no web source is currently committed to main.

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
| Current streak/evaluated date | GET streak | Progress | Progress/Home | Through backend yesterday |
| Monthly report | GET report | Reports | Reports | All 12 response fields represented |
| Selected-month longest streak | Report.longestStreak | Reports | Reports | Not presented as lifetime best |
| Present current streak in historical report | Report.currentStreak | Reports | Reports | Label explicitly distinguishes it |
| Incomplete legacy schedules | Nullable activity metadata | Named dashboard edit guidance | Home/progress/reports edit guidance | User chooses actual date, never auto-saved |
| Loading, empty, error/retry | HTTP/network results | All views | All views | No fake production data |
| Destructive confirmations | Client UX | Dialogs | Material dialogs | API remains authoritative |
| Theme preference | Client-only | Existing visual theme | System/light/dark | DataStore; not a backend feature |
| Backend URL/server-zone preference | Client-only | Same-origin root | Settings | One configured service, no database access |
| Login/registration/token storage | Not present | Not present | Not invented | Deployment protection remains backend responsibility |
| Reactivation/restore | Not present | Not present | Not invented | Delete means inactive; no restore API |
| Weekly/category/global statistics | Not present | Not present | Not invented | No unsupported analytics or demo charts |

Runtime implementation is native Compose. Build/test evidence and the distinction between implemented coverage and runtime verification are recorded in VERIFICATION.md.
