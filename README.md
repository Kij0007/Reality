# Reality

Reality is an accountability and activity tracking application with a Spring Boot API, a browser frontend and a native Android client. Each account owns its activities, sessions, breaks, progress, streaks and monthly reports.

```text
Web frontend ─────────┐
                      ├─ HTTPS → Spring Boot → PostgreSQL
Android Compose ──────┘
     → ViewModel → Repository → Retrofit
```

Android connects to the REST API; it never connects directly to PostgreSQL. With the server and database hosted online, your laptop can stay switched off and your phone can use Wi-Fi or mobile internet. Hosting files are included, but a public service and URL still need to be provisioned in your provider accounts.

## Project locations

| Location | Purpose |
|---|---|
| `src/main/java/com/reality/` | Spring Boot controllers, services, persistence, bearer authentication and ownership checks |
| `src/main/resources/static/` | Browser frontend served by Spring Boot, including registration and sign-in |
| `android/` | Independently openable Kotlin, Jetpack Compose, Material 3, Hilt and Retrofit project |
| `deployment/` | Free hosting setup, environment configuration, migration guidance and real-server smoke test |
| `Dockerfile`, `render.yaml` | Complete web/API container and explicitly Free Render service configuration |
| `.github/workflows/` | Backend/container and Android build/test verification |

## Run the backend locally

Use Java 21 and a reachable PostgreSQL database. Set these environment variables in your IDE run configuration or shell, keeping the actual password outside source control:

| Variable | Meaning | Local default |
|---|---|---|
| `DATABASE_URL` | JDBC PostgreSQL URL, without embedded username/password | `jdbc:postgresql://localhost:5432/reality` |
| `DATABASE_USERNAME` | PostgreSQL role | `postgres` |
| `DATABASE_PASSWORD` | Database password | Required; no committed default |
| `PORT` | HTTP port | `8081` |

From this repository root, run `./mvnw spring-boot:run` on macOS/Linux or `.\mvnw.cmd spring-boot:run` in Windows PowerShell. Then open [http://localhost:8081/](http://localhost:8081/) if you kept the default port. The web frontend is already packaged with the application. Create an account or sign in; protected APIs require the server-issued bearer token.

`GET /api/health` is public and reports process liveness. It does not test database readiness. Backend tests use a separate, disposable H2 database: run `./mvnw verify` or `.\mvnw.cmd verify`.

## Android and online hosting

Open the **android** directory in Android Studio and follow [android/README.md](android/README.md) and [android/SETUP.md](android/SETUP.md). Set `BACKEND_BASE_URL` for debug, or `PRODUCTION_BACKEND_BASE_URL` to the actual deployed HTTPS origin for release. Connection settings are also available on the sign-in screen. Do not put database credentials in Android.

Follow [deployment/README.md](deployment/README.md) to use **Render Free for Spring Boot and Neon Free for PostgreSQL**. Free hosting has cold starts and quotas. Keep both provider plans Free; the repository does not create paid resources or claim an already deployed URL.

See [BACKEND_INTEGRATION.md](android/BACKEND_INTEGRATION.md) for all 23 routes, [FEATURE_PARITY.md](android/FEATURE_PARITY.md) for feature coverage, and [VERIFICATION.md](android/VERIFICATION.md) for completed checks and remaining validation. Existing unowned database records require explicit reviewed migration; see [deployment/MIGRATION.md](deployment/MIGRATION.md). The first person who registers never inherits them automatically.
