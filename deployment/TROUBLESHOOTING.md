# Free cloud troubleshooting

| Symptom | Check and action |
|---|---|
| First page or login is slow after inactivity | Open `/api/health` on the actual Render HTTPS origin and allow a cold start. See the free plan behavior in [README.md](README.md). A health response confirms process liveness; a subsequent authenticated read verifies database access. |
| Health never becomes UP | Read Render deployment/startup logs. Confirm `plan: free`, the correct branch, `SPRING_PROFILES_ACTIVE=cloud`, Java 21 image build success, and valid runtime database settings. |
| Render says no open port | Cloud profile must be active. It listens on `0.0.0.0:${PORT}`, with fallback 10000, rather than the PC-only 8081 address. |
| Database connection refused / unknown host | Confirm `DATABASE_URL` starts with `jdbc:postgresql://`, uses the actual Neon host and database, and is not `localhost`. Render cannot reach PostgreSQL running only on your PC. |
| Database authentication failed | Copy the exact role and password from the intended Neon project into Render secrets. Do not embed them in the JDBC URL; redeploy/restart after changing a secret. |
| SSL connection problem | Preserve `sslmode=require` for Neon. `verify-full` additionally needs a working trust configuration. Never disable Android HTTPS certificate checks. |
| Web page loads but data does not | Sign in, inspect the failed request status in browser DevTools Network, and check the backend logs. Public static resources can load while an API request is unauthorized or the database is unavailable. |
| 401 | The bearer token is missing, expired, revoked, or belongs to another backend. Sign in again; passwords are never sent to feature endpoints. |
| 404 for another user's ID | This is expected: the server hides foreign/unowned resources. Do not weaken ownership checks to make an old ID visible. Follow [MIGRATION.md](MIGRATION.md) for legacy rows. |
| 409 when registering | The normalized username already exists. Sign in or choose another unique username. |
| 400 creating account | Username must be 3–40 ASCII lowercase letters/digits/`_.-` after normalization; display name is required and limited to 80 code points; password needs at least 12 code points and at most 72 UTF-8 bytes. |
| 400 on break/resume/stop | Refresh the session; the requested state transition may already have happened. Writes are not retried automatically. |
| Progress/streak/report data missing | Check the activity's real start date and repeat days; repair missing legacy metadata via Edit. Requests use ISO date `yyyy-MM-dd` and report month `yyyy-MM`. |
| Android cannot reach cloud server | Configure the actual Render HTTPS origin ending in `/`, not `localhost`, the Neon JDBC URL, or a web hash route. Keep server timezone `Asia/Kolkata`. |
| Web resources return 404 | Deploy the full branch with `src/main/resources/static/index.html`, `css`, and `js`. Docker packages those resources automatically into the Spring Boot JAR. |
| Old PC activities absent | The cloud database is separate. Importing data and assigning reviewed owners are explicit administrator steps. |
| Container stops / out of memory | Check Render logs and memory metrics. The container has bounded Java defaults, but free compute is small. Review unexpected load and database/list sizes; do not automatically upgrade to paid compute when a zero budget is required. |
| Service suspended or build disabled | Check both providers' free usage limits. Keep the Free plans; wait for quota reset or reduce usage rather than accepting a paid upgrade. |
| CI fails before Docker smoke test | Open the Maven test report in `Reality-cloud-verification`; H2 tests should require no external PostgreSQL credentials. Fix compilation/security/ownership failures first. |
| Smoke test fails after a POST timeout | The write may have committed. Inspect server data before retrying. The script deliberately does not retry mutations and may leave disposable accounts/records on failure. |
| Render auto deployment does not run | Confirm source branch `codex/reality-android`, GitHub access, and successful applicable checks. Blueprint uses `autoDeployTrigger: checksPass`. |

To diagnose without creating records, open `/api/health`, load `/`, sign in normally, and perform an authenticated GET. The full `deployment/smoke_test.py` creates test accounts and data and requires the explicit `--allow-create-test-data` flag; use a disposable staging database.

Do not share screenshots containing provider credentials, request Authorization headers, or login response tokens. The deployment test report avoids logging those values.
