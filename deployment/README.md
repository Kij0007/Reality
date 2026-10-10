# Reality: free public hosting

This repository contains a deployable Spring Boot web/API image, separate user accounts, and an independently buildable Android client. Hosting is prepared here; no live cloud URL or cloud account is created by these files.

```text
Browser web client ──┐
                    ├─ HTTPS → Render Free: Spring Boot → TLS → Neon Free: PostgreSQL
Native Android ─────┘
```

The web files under `src/main/resources/static` are packaged in the same executable JAR. There is no separate frontend server, npm build, Android database connection, or browser CORS configuration to deploy.

## Free plan and availability

`render.yaml` explicitly selects `plan: free` (512 MB / 0.1 CPU); leaving that field out can select paid compute. It creates one Docker web service and no Render database. [Render Blueprint reference](https://render.com/docs/blueprint-spec).

Render Free sleeps after 15 minutes without inbound traffic, and waking can take about a minute. Its 750 instance hours per workspace per month are shared across free services. Free Render PostgreSQL expires after 30 days, so this setup uses an external Neon database. With no payment method, exceeding applicable free quotas can suspend services or disable builds rather than charge for overages. Do not add a payment method, select a paid plan, enable paid features, or accept an upgrade if your budget must remain zero. [Render free service limits](https://render.com/docs/free).

Neon's October 2, 2026 Free-plan announcement includes 1 GB storage and 100 CU-hours per project per month. Select **Free** in the Neon console and monitor that console's current limits; provider terms can change. [Neon Free announcement](https://neon.com/blog/neon-free-plan-1-gb-per-project).

This is free public hosting with cold starts and quotas. It does not provide an always-on service guarantee. Do not create periodic keep-awake jobs. A public URL alone does not provide app-store distribution or an availability SLA.

## 1. Prepare the GitHub branch

Use the complete `codex/reality-android` branch of `Kij0007/Reality`, including the backend account/ownership changes, static web files, `Dockerfile`, `render.yaml`, and cloud workflow. The Blueprint currently deploys that exact branch. It does not deploy the old backend-only default branch.

In GitHub **Actions**, run **Reality Cloud** if it has not run yet. Its checks compile the backend, run H2 integration tests, build the Docker image, then start the actual cloud profile against disposable PostgreSQL with a 512 MB limit and check authentication, two-user isolation, all existing feature routes, and packaged web resources. Successful checks are required before automatic deployment.

If you later merge into `main`, change `branch` in `render.yaml` to `main` and sync the Blueprint. Do not delete the deployed source branch before updating this setting.

## 2. Create the free Neon database

1. Sign in to [Neon](https://console.neon.tech/) and create a **Free** project named `Reality`.
2. Select a region close to the Render service's Singapore region where the Free plan offers one. The connection hostname supplied by Neon determines the actual database region.
3. Use the database name and role shown in **Connect**. A new project's default database is often `neondb`; use the actual value shown by your console.
4. Copy its host, database, role, and password privately. Prefer the direct connection endpoint for this single backend with five pooled connections. Do not paste the connection string into GitHub files or Android settings.
5. Prepare these three environment values:

   ```text
   DATABASE_URL=jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require
   DATABASE_USERNAME=YOUR_NEON_ROLE
   DATABASE_PASSWORD=YOUR_NEON_PASSWORD
   ```

   These are configuration examples, not existing credentials. Convert the console's `postgresql://user:password@host/database?...` URL into the JDBC format above; keep the username and password in their separate variables. Preserve any additional JDBC-compatible options Neon requires. `sslmode=require` encrypts the PostgreSQL connection; it does not verify the server certificate. You may use `sslmode=verify-full` with an appropriately trusted certificate configuration. [pgJDBC SSL documentation](https://jdbc.postgresql.org/documentation/ssl/).

The database is persistent separately from Render's temporary container filesystem. Keep the Neon project and role active. A free database is not a substitute for your own exported backups.

## 3. Deploy the free Render service

1. Sign in to [Render](https://dashboard.render.com/) using a free workspace and connect the Reality GitHub repository.
2. Choose **New → Blueprint**, select `Kij0007/Reality` and branch `codex/reality-android`, and use the root `render.yaml`.
3. Review the proposed resources: exactly one web service named `reality`, Docker runtime, **Free** compute, Singapore region. There should be no database, persistent disk, worker, paid instance, or preview deployment.
4. Enter `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` when prompted. They are `sync: false` secrets; their values belong only in the Render environment configuration. If you update an existing Blueprint, edit these values directly in the service's **Environment** page, because missing-value prompts apply to initial creation.
5. Apply the Blueprint and wait for the first image build and startup. The first deploy may take several minutes. `SPRING_PROFILES_ACTIVE=cloud` and the runtime timezone are already configured; Render supplies `PORT`.
6. After a successful deploy, copy the actual HTTPS service URL displayed by Render. There is no deployed URL stored or assumed in this repository.

For later commits, `autoDeployTrigger: checksPass` waits for applicable GitHub checks. If a check fails, fix it before redeploying; do not bypass failed ownership or authentication checks.

## 4. Verify and use the public application

1. Open `https://YOUR_RENDER_HOST/api/health`. Wait through a cold start; the expected JSON is `{"status":"UP"}`. This is process liveness, not a database readiness report.
2. Open that same origin's `/` page, create your own account, and sign in. Account usernames are unique; each signed-in user sees their own activities, sessions, breaks, progress, streaks, and reports.
3. Create an activity, select repeat days, start a session, take a break, resume, stop, and inspect progress/reports. Backend timestamps use `Asia/Kolkata`.
4. In Android **Settings**, set the backend to the exact Render HTTPS URL with a trailing `/`, keep the server timezone `Asia/Kolkata`, then register/sign in. The database URL and credentials never belong in the Android app.
5. New cloud databases start empty. Existing PC data is not transferred automatically. Follow [MIGRATION.md](MIGRATION.md) if you want to bring it across.

Accounts currently use username/password and server-issued opaque bearer tokens, with 30-day expiration. Logout revokes the current token. There is no email verification, self-service forgotten-password endpoint, social sign-in, or email sending in the current backend; no such cloud service is required by this setup.

## Reproduce the cloud checks locally

Java 21 is required for backend builds. Docker is required for the image/container test. Python 3.10 or later runs the smoke script without third-party packages.

```powershell
# From the repository root: H2 tests do not require your PC PostgreSQL.
.\mvnw.cmd --batch-mode --no-transfer-progress verify

# Package the web client and backend into the same image.
docker build --tag reality:local .
```

To run it against an existing reachable development PostgreSQL, supply the credentials to your current shell privately first. Use a JDBC hostname reachable **from inside Docker**; on Docker Desktop, `host.docker.internal` reaches the PC, while `localhost` reaches the container itself.

```powershell
# Set DATABASE_URL, DATABASE_USERNAME, and DATABASE_PASSWORD privately in this shell.
docker run --rm --name reality-local --memory 512m -p 10000:10000 --env DATABASE_URL --env DATABASE_USERNAME --env DATABASE_PASSWORD reality:local
```

To run the full write-capable smoke test, use a disposable staging database/server. It creates two test accounts, deactivates their test activities, deletes their sessions, and revokes its tokens on success. Accounts and deactivated rows remain because the backend has no account-delete or hard-delete activity API. If a check fails, it stops immediately and may leave test records; it never retries a write automatically.

```powershell
python deployment/smoke_test.py --base-url http://localhost:10000/ --allow-http --allow-create-test-data
```

CI uses a fresh disposable PostgreSQL database and removes the application container afterward; it never receives hosting credentials. The artifact **Reality-cloud-verification** contains test results, route checks, memory statistics, and startup logs. Passing local/CI checks does not mean a hosted account was provisioned or a Render deployment was performed.

See [ENVIRONMENT.md](ENVIRONMENT.md), [MIGRATION.md](MIGRATION.md), and [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
