# Reality verification

## Current integration scope

The complete codex/reality-android branch contains native Android, the existing web client, the previously identified four Java repairs, real account APIs, owner-scoped services, public health, Docker/cloud configuration and free Render + Neon instructions. The 18 original business routes retain their JSON contracts; four account routes and health make 23 routes across eight controllers.

Authentication/cloud changes require new verification. Results from the earlier anonymous Android version do not establish successful verification of this update.

## Current update status

| Check | Current result |
|---|---|
| Android configuration / assembleDebug | PENDING new account version build |
| assembleDebugAndroidTest | PENDING new account version build |
| JVM tests including authentication | PENDING new account version execution |
| lintDebug | PENDING new account version execution |
| Release R8/resource shrinking | PENDING new account version execution |
| Connected emulator tests/current screenshots | PENDING new account startup execution |
| Backend Maven verify/H2 accounts and ownership | PASS locally: 21 tests, zero failures/errors/skips, including authentication, throttling and two-user ownership |
| Browser API/auth regression tests | PASS locally: eight Node tests covering all 18 business contracts, account routes, session invalidation, stale data and duplicate/uncertain writes |
| Docker/512 MB real PostgreSQL smoke | PENDING Reality Cloud workflow; local Docker CLI unavailable |
| Live Render/Neon provisioning | NOT PERFORMED; no live URL claimed |
| Android Studio graphical sync | NOT RUN; command-line build is the compilation check |

Replace pending results only with actual completed evidence. Reality Cloud CI verifies Maven/H2 tests, Docker build, real PostgreSQL routes/ownership and packaged web resources, and records memory/startup evidence. Android CI builds/tests/lints debug and release, runs an emulator, and packages source/APK artifacts.

Local backend evidence is in `target/surefire-reports/`: one application-context test, six authentication tests, six limiter unit tests, one throttle integration test, and seven ownership tests. Browser regression coverage is in `scripts/web-api.test.mjs`; it uses test doubles and does not establish successful rendering in a real browser. H2 tests establish backend behavior in isolation; actual PostgreSQL/container and Android validation remain separate pending checks.

## Historical Android baseline

Before accounts/cloud changes, [GitHub Actions run 37898966489](https://github.com/Kij0007/Reality/actions/runs/37898966489) passed build/emulator jobs at 91888b1de829b869d6c557de288ca214b2dd8371. It covered debug/instrumentation APK builds, JVM tests, lint with zero errors, unsigned release R8, four connected emulator tests, APK signature verification and six valid native screenshots. This is historical evidence, not a claim that the current authentication version passed.

That baseline covered API serialization, validation, timers/timezones, duplicate mutations, settings/configuration locking/recovery, saved forms, stale data, cancellation, report filters, break controls, Hilt startup and navigation. Runtime production data never comes from the test fixtures.

## Earlier live-server limitation

The earlier opt-in LiveBackendFlowTest used actual Retrofit interfaces/DTOs. Both localhost:8081 and 127.0.0.1:8081 refused the connection before the first POST, so no test records were created and PC PostgreSQL persistence was not verified then. An unavailable server is not a successful end-to-end test.

The current cloud smoke script exercises actual Spring Boot and ephemeral PostgreSQL: two-account signup/login, every method/path route, bidirectional ownership, activity edit, session break/resume/stop/delete, progress/streak/monthly JSON, static resources and token revocation. It does not fabricate responses. Its current result remains pending until CI completes.

Free accounts and a real deployment are separate external steps. A successful container/Android build does not create a public URL. Local records are not automatically imported or inherited by the first cloud account; migration is an explicit administrator operation.
