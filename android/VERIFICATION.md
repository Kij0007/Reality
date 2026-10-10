# Reality verification

## Current integration scope

The complete codex/reality-android branch contains native Android, the existing web client, the previously identified four Java repairs, real account APIs, owner-scoped services, public health, Docker/cloud configuration and free Render + Neon instructions. The 18 original business routes retain their JSON contracts; four account routes and health make 23 routes across eight controllers.

The account-enabled native update has completed fresh build and emulator verification. Results from the earlier anonymous Android version are retained only as historical evidence. Actual provider provisioning remains separate from CI.

## Current update status

| Check | Current result |
|---|---|
| Android configuration / assembleDebug | PASS on Linux GitHub Actions; account-enabled debug APK built |
| assembleDebugAndroidTest | PASS on Linux GitHub Actions |
| JVM tests including authentication | PASS: 84 tests, zero failures/errors/skips, including the real Spring Boot/PostgreSQL Retrofit flow |
| lintDebug | PASS: zero errors, 28 warnings (27 dependency/plugin/target notices and intentional debug cleartext) |
| Release R8/resource shrinking | PASS: unsigned release built with a compile-only HTTPS loopback URL; no production URL or signing key implied |
| Connected emulator tests/current screenshots | PASS: five API 35 tests; four 1080×1920 native screenshots |
| Debug APK signature | PASS: Android Build Tools 35.0.0 apksigner verifies APK Signature Scheme v2; debug certificate |
| Backend Maven verify/H2 accounts and ownership | PASS locally and in CI: 21 tests, zero failures/errors/skips, including authentication, throttling and two-user ownership |
| Browser API/auth regression tests | PASS locally and in CI: eight Node tests covering all 18 business contracts, account routes, session invalidation, stale data and duplicate/uncertain writes |
| Docker/512 MB real PostgreSQL smoke | PASS in Reality Cloud CI: all 23 routes and two-account ownership; 100 API/static/integrity checks |
| Real Chromium cloud workflow checks | PASS: 15 workflow checks, all 23 routes through real UI interactions, no page/console errors or HTTP failures |
| Live Render/Neon provisioning | NOT PERFORMED; no live URL claimed |
| Android Studio graphical sync | NOT RUN; command-line build is the compilation check |

Reality Cloud CI completed Maven/H2 tests, Node regression tests, Docker build, actual PostgreSQL API/ownership checks and real Chromium workflows. Android CI completed debug/release builds, JVM tests, lint and connected emulator tests. Neither workflow provisions public hosting.

Local backend evidence is in `target/surefire-reports/`: one application-context test, six authentication tests, six limiter unit tests, one throttle integration test, and seven ownership tests. Browser regression coverage is in `scripts/web-api.test.mjs`; it uses test doubles and does not establish successful rendering in a real browser. H2 tests establish backend behavior in isolation; the native CI flow and container smoke additionally exercise actual PostgreSQL.

## Current native evidence

[GitHub Actions run 38044194592](https://github.com/Kij0007/Reality/actions/runs/38044194592) passed both `verify` and `emulator-tests` at `c24a04c616ee3e5a23f86dbb859fe8382e80c3a9`. Subsequent cloud/browser-only changes do not change this native code: all 95 non-document Android files in the CI source archive match the current source after line-ending normalization, including the wrapper binary.

Downloaded JUnit XML confirms 84 JVM tests with no failures, errors or skips. The opt-in `LiveBackendFlowTest` ran for 65.378 seconds against the workflow's real Spring Boot service and disposable PostgreSQL, registering a test account and exercising all 18 business routes through actual Retrofit interfaces. It verified create/read/edit, start, breaks/resume, stop, day history/progress, streak, monthly report and deletion; it was not skipped. Separate auth, expiry/backend binding and form recovery tests are included.

The five connected API 35 emulator tests cover Hilt startup with signed-out connection settings, native account validation, Android Keystore-encrypted session save/restore and conditional token clearing, and two safe break-control states. Four native screenshots record login, registration, settings and validation; they are 1080×1920. These emulator tests do not claim an on-device end-to-end flow against a public provider URL.

The Windows-local AAPT2 process failed during resource processing in this environment. Linux CI subsequently completed the same native source's debug/release builds, tests and lint successfully. Android Studio graphical sync was not run. The release check uses `https://127.0.0.1/` only as build configuration and does not connect to or claim a production service.

## Historical Android baseline

Before accounts/cloud changes, [GitHub Actions run 37898966489](https://github.com/Kij0007/Reality/actions/runs/37898966489) passed build/emulator jobs at 91888b1de829b869d6c557de288ca214b2dd8371. It covered debug/instrumentation APK builds, JVM tests, lint with zero errors, unsigned release R8, four connected emulator tests, APK signature verification and six valid native screenshots. This is historical evidence, not a claim that the current authentication version passed.

That baseline covered API serialization, validation, timers/timezones, duplicate mutations, settings/configuration locking/recovery, saved forms, stale data, cancellation, report filters, break controls, Hilt startup and navigation. Runtime production data never comes from the test fixtures.

## Earlier live-server limitation

The earlier opt-in LiveBackendFlowTest used actual Retrofit interfaces/DTOs. Both localhost:8081 and 127.0.0.1:8081 refused the connection before the first POST, so no test records were created and PC PostgreSQL persistence was not verified then. An unavailable server is not a successful end-to-end test.

## Current cloud and browser evidence

[Reality Cloud run 38044990347](https://github.com/Kij0007/Reality/actions/runs/38044990347) completed successfully at `4aedfb7d`. Its downloaded backend XML confirms 21 passing tests and no failures, errors or skips. The real container/PostgreSQL smoke records 100 passing API/static/integrity checks: two-account signup/login, all 23 method/path routes, bidirectional ownership, activity edit, session break/resume/stop/delete, progress/streak/monthly JSON, static resources and token revocation. It does not fabricate responses.

Downloaded Chromium evidence records 15 passing workflows and coverage of all 23 routes through real web interactions. It includes account creation/login/logout/reload, activity CRUD, scheduling and filters, session break/resume/history, progress/streak/report views, cross-account data clearing and phone-sized navigation. Page errors and console errors are empty; no observed resource/API response failed. Six browser screenshots capture sign-in, schedule, break, report, account isolation and the mobile dashboard.

The non-root Docker application stayed running under a 512 MiB memory limit; the collected snapshot shows 255.9 MiB used. This is an observed snapshot, not a peak-memory guarantee. CI permits one CPU, while free provider CPU capacity, cold starts and availability still need observation after actual deployment.

Free accounts and a real deployment are separate external steps. A successful container/Android build does not create a public URL. Local records are not automatically imported or inherited by the first cloud account; migration is an explicit administrator operation.