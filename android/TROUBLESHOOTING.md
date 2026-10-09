# Reality Android troubleshooting

| Symptom | Concrete check/correction |
|---|---|
| localhost cannot connect | Android localhost is the device itself. Emulator uses http://10.0.2.2:8081/ for the PC. A physical phone uses the PC's actual LAN IPv4 address. |
| Connection refused | Confirm Spring Boot has started and http://localhost:8081/activities works on the PC. Verify port/context path and that the server listens on a reachable interface. |
| Phone cannot connect | Use the same trusted LAN, not cellular or guest-isolated Wi-Fi. Check ipconfig and Windows Firewall's private-network rule for the server port. Test the activities URL in the phone browser. |
| Cleartext blocked | Install the debug variant for local HTTP. Release deliberately requires HTTPS. Do not weaken TLS or add a trust-all certificate implementation. |
| Timeout/server unavailable | Check backend/database startup and logs, DNS, connectivity and firewall. Retry a read. For a timed-out write, first refresh/check whether the server already saved it. |
| Wrong server despite Gradle change | A saved Settings backend URL overrides the compiled initial default. Save the intended URL in the app, or clear the app's local data to restore compiled defaults. |
| 404 on every API | Base URL must be the application's root, including any context path, with a trailing slash. Activity API is activities, not api/activities. Do not append an API route to the base URL. |
| 404 on a single activity | It may have been soft-deleted or removed on another client. Refresh lists; existing session history can still identify it by server activity ID. |
| 400/409/422 | Read the inline backend message. Check positive target, a start date, selected weekdays, valid uppercase category and current session/break state. |
| 401/403 | The inspected backend has no auth contract. Check whether your deployment added a reverse-proxy login or Spring Security; Android must be adapted to that real auth mechanism instead of guessing tokens. |
| 500 on streak/report | Old activities may lack startDate. Edit them and choose the actual commitment date and weekdays. Inspect backend logs if metadata is complete. |
| 500 on session with breaks deletion | The inspected GitHub backend deletes a session before child breaks. See BACKEND_INTEGRATION.md for the previously identified minimal server repair. Android cannot repair foreign keys. |
| JSON/unexpected response | Confirm the URL returns the expected Spring REST JSON rather than a proxy/HTML login page. Match the inspected DTO contract and backend revision. Keep nullable endTime/duration as null until stopped. |
| Daily total seems to include breaks | The old main revision uses elapsed overlap for daily totals. Android displays authoritative server totals. Apply/review the documented backend accounting repair, not a fabricated client correction. |
| Streak is zero after completing today | Current streak evaluates only through server yesterday. Today remains in progress. |
| Best streak differs on historical report | longestStreak is selected-month-only; currentStreak is present current streak even for a past month. |
| Timer/day differs between devices | Set Backend clock to the Spring JVM's zone. API LocalDateTime has no UTC offset. Live estimates also depend on the phone clock; saved duration values remain authoritative. |
| HTTP URLs fail in release | Set PRODUCTION_BACKEND_BASE_URL to a real HTTPS URL ending in /. The release build and app settings enforce this. |
| Release artifact will not install | Unsigned APKs need signing. Use Studio's signed APK wizard or private environment variables. Match the signing key for upgrades. |
| Debug APK install fails | Device must support API26+. If another debug key signed the same app ID, uninstall that debug installation or use the same key. adb install -r reports the precise install error. |
| Gradle plugin/SDK error | Use a Studio version supporting AGP8.13.2, JDK17, SDK36 and Build Tools35.0.0. Sync with internet and the checked-in wrapper. Do not create missing source files manually. |
| No records | [] is a valid empty backend. Create an activity through the native form; production screens never substitute demo data. |
| Break buttons unavailable | Break state could not be verified. Retry/refetch it. The app avoids treating an unknown state as running and allows supported stop behavior. |

No database credentials, tokens, keystore passwords or private keys belong in screenshots/logs shared for troubleshooting. Debug networking records only basic diagnostic request information; production does not log response bodies.
