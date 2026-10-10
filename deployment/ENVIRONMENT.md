# Runtime configuration

| Variable | Where to set it | Meaning |
|---|---|---|
| `DATABASE_URL` | Render Environment; prompted on first Blueprint creation | Required PostgreSQL JDBC URL, such as `jdbc:postgresql://HOST/DATABASE?sslmode=require`. Keep credentials separate. |
| `DATABASE_USERNAME` | Render Environment | Required Neon database role. |
| `DATABASE_PASSWORD` | Render Environment | Required Neon role password. Never commit it. |
| `SPRING_PROFILES_ACTIVE` | Supplied by Docker/Blueprint | `cloud` activates `application-cloud.properties`. |
| `PORT` | Supplied by Render | Listening port; cloud fallback is `10000`. Bind address is `0.0.0.0`. |
| `TZ` | Supplied by Docker/Blueprint | `Asia/Kolkata`. |
| `JAVA_TOOL_OPTIONS` | Supplied by Docker; optional host override | Java 21 heap/native memory limits and `-Duser.timezone=Asia/Kolkata`; preserve the timezone flag if overriding. |

Standard Spring `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` can override the corresponding datasource properties, but this Blueprint uses `DATABASE_*` consistently. Set one convention rather than conflicting duplicate values.

No database credential, password, or bearer token is a Docker build argument. The build produces the same image for all environments and credentials are injected only at runtime. `.dockerignore` admits only the production Maven/source inputs and excludes secret/key/backup file patterns. Do not put a secret in an ordinary Java, HTML, or properties source file: source resources are packaged into the JAR.

The cloud pool has maximum five PostgreSQL connections, minimum idle zero, a 60-second idle timeout, and no periodic keepalive. The public health endpoint does not query PostgreSQL. Hibernate retains this project's existing `ddl-auto=update` startup behavior to initialize a fresh database and add the account schema; review backups and migration instructions before starting it against existing data.

The container runs as numeric non-root user `10001:10001`. Its Java defaults are heap maximum 256 MB, metaspace maximum 128 MB, code cache 32 MB, direct memory 16 MB, and a small worker pool. These reduce memory use; free hosting still has finite total process memory, CPU, request capacity, and provider quotas. CI runs the actual container under a 512 MB limit and reports observed usage.

Render terminates HTTPS before forwarding to Spring Boot. `server.forward-headers-strategy=framework` lets Spring understand those proxy headers. The web client uses the same HTTPS origin for relative API calls; Android uses that HTTPS origin through Retrofit with normal TLS validation. No database access or CORS workaround is needed on Android.

Security uses hashed opaque bearer tokens, not JWTs or authentication cookies. There is no `JWT_SECRET` setting. Only `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/health`, and public static resources are unauthenticated. `GET /api/auth/me`, `POST /api/auth/logout`, and all activity/session/progress/report routes require `Authorization: Bearer TOKEN`.

Changing the Render database password requires updating the Render secret and restarting/redeploying the service. If a real credential was previously committed or shared, rotate it at the database provider; merely removing it from the latest source does not invalidate the old value or erase Git history.
