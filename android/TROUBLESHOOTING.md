# Reality Android troubleshooting

| Symptom | Concrete check/correction |
|---|---|
| localhost cannot connect | Android localhost is the device itself. Emulator uses http://10.0.2.2:8081/ for the PC. A physical phone uses the PC's actual LAN IPv4 address. |
| Connection refused | Confirm updated Spring Boot has started and http://localhost:8081/api/health returns UP. Verify port/context path and a reachable listening interface. |
| Phone cannot connect to a local development server | PC-hosted development requires the same trusted LAN, not cellular/guest isolation. Check ipconfig and the private-network firewall rule; test /api/health in the phone browser. |
| Phone requires the laptop to stay on | The configured URL points to the PC. For independent online use, deploy Spring Boot and PostgreSQL using deployment/README.md and save the actual public HTTPS origin. Your phone then needs internet, not the PC or its network. |
| Cleartext blocked | Install the debug variant for local HTTP. Release deliberately requires HTTPS. Do not weaken TLS or add a trust-all certificate implementation. |
| Timeout/server unavailable | Check backend/database startup and logs, DNS, connectivity and firewall. Retry a read. For a timed-out write, first refresh/check whether the server already saved it. |
| Wrong server despite Gradle change | A saved Settings backend URL overrides the compiled initial default. Save the intended URL in the app, or clear the app's local data to restore compiled defaults. |
| 404 on every API | Base URL must be the application's root, including any context path, with a trailing slash. Activity API is activities, not api/activities. Do not append an API route to the base URL. |
| 404 on a single activity | It may be inactive, foreign-owned or an unowned legacy row. Refresh lists; do not weaken ownership. Existing owned session history identifies inactive activities by ID. |
| 400/409/422 | Read the inline backend message. Check positive target, a start date, selected weekdays, valid uppercase category and current session/break state. |
| 401 | Sign in/register on this updated backend. Tokens expire after 30 days; logout revokes the current token; matching saved sessions are cleared on 401. |
| 403 | Read the backend error and verify the complete branch/security configuration is deployed. Do not disable ownership to expose another account's records. |
| 409 during signup | The normalized username exists. Sign in or choose another username. |
| 429 during signup/sign-in | Wait and try again after the server's Retry-After period. The small backend instance bounds concurrent and per-minute password requests. Do not repeatedly tap or automate retries. |
| 500 on streak/report | Old activities may lack startDate. Edit them and choose the actual commitment date and weekdays. Inspect backend logs if metadata is complete. |
| 500 on session with breaks deletion | This branch transactionally deletes break children before the parent. Confirm the updated backend is deployed and inspect its logs. |
| JSON/unexpected response | Confirm the URL returns the expected Spring REST JSON rather than a proxy/HTML login page. Match the inspected DTO contract and backend revision. Keep nullable endTime/duration as null until stopped. |
| Daily total includes breaks | This branch subtracts clipped break overlap. Confirm the updated backend deployment; Android displays authoritative totals without a fabricated correction. |
| Streak is zero after completing today | Current streak evaluates only through server yesterday. Today remains in progress. |
| Best streak differs on historical report | longestStreak is selected-month-only; currentStreak is present current streak even for a past month. |
| Timer/day differs between devices | Set Backend clock to the Spring JVM's zone. API LocalDateTime has no UTC offset. Live estimates also depend on the phone clock; saved duration values remain authoritative. |
| HTTP URLs fail in release | Set PRODUCTION_BACKEND_BASE_URL to a real HTTPS URL ending in /. The release build and app settings enforce this. |
| Release artifact will not install | Unsigned APKs need signing. Use Studio's signed APK wizard or private environment variables. Match the signing key for upgrades. |
| Debug APK install fails | Device must support API26+. If another debug key signed the same app ID, uninstall that debug installation or use the same key. adb install -r reports the precise install error. |
| Gradle plugin/SDK error | Use a Studio version supporting AGP8.13.2, JDK17, SDK36 and Build Tools35.0.0. Sync with internet and the checked-in wrapper. Do not create missing source files manually. |
| No records | [] is a valid empty backend. Create an activity through the native form; production screens never substitute demo data. |
| Break buttons unavailable | Break state could not be verified. Retry/refetch it. The app avoids treating an unknown state as running and allows supported stop behavior. |
| Signup/login waits for server | Free hosting sleeps. Account submission warms public health for up to 120 seconds before one account write; check Render logs if it never wakes. |
| Test connection passes, data fails | Health confirms process liveness, not database readiness. Check sign-in, an authenticated read, Neon availability and server logs. |
| Old PC data missing after first signup | Cloud storage is separate and legacy unowned rows are deliberately not assigned to the first account. See repository deployment/MIGRATION.md. |
| Saved login cannot be restored | Keystore/session storage may have been reset or corrupted. Sign in again; passwords are not stored. |
| Free hosting suspended | Review provider-console quotas and deployment/README.md. Do not accept a paid upgrade when budget must remain zero. |

No database credentials, tokens, keystore passwords or private keys belong in screenshots/logs shared for troubleshooting. Debug networking records only basic diagnostic request information; production does not log response bodies.
