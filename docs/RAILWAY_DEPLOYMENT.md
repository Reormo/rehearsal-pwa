# Railway production deployment

## Architecture

Production uses one public application origin and one Railway PostgreSQL service.

```text
Browser / installed PWA
        |
        | HTTPS / WSS
        v
Railway public domain
        |
      Caddy
      /   \
     /     \
static    Spring Boot
Next.js   /api, /ws, /health
export
             |
             v
     Railway PostgreSQL
```

The frontend and backend intentionally share one browser origin. Do not deploy them to two
different default `*.up.railway.app` domains while keeping cookie authentication in the
current `SameSite=Strict` design.

## Repository deployment files

- `/Dockerfile` builds the Spring Boot JAR and the Next.js static export.
- `/deploy/Caddyfile` serves the frontend and proxies `/api/*`, `/ws`, and `/health`.
- `/deploy/start.sh` supervises Spring Boot and Caddy in the same container.
- `/backend/src/main/resources/application-prod.properties` contains production-only
  database and graceful-shutdown settings.
- `/infra/railway.app.env.example` documents the Railway application-service variables.

The Docker build sets `NEXT_PUBLIC_API_BASE_URL` to an empty string. Browser API requests
therefore use the deployed app's own origin. The realtime client falls back to
`window.location.origin`, so `/ws` also stays on the same origin.

## Railway setup

1. Create one Railway project.
2. Add a PostgreSQL service. The default service name is usually `Postgres`.
3. Add one application service from the `Reormo/rehearsal-pwa` GitHub repository.
4. Keep the application service source root at the repository root. Railway should detect
   `/Dockerfile`.
5. Generate a public domain for the application service.
6. Open the application service **Variables** tab and add the values from
   `/infra/railway.app.env.example`.
7. Replace every `REPLACE_WITH_...` value before deploying.
8. If the PostgreSQL service was renamed, change the `${{Postgres.*}}` references to the
   actual service name.
9. Configure the application service health check path as `/health`.
10. Deploy.

Do not set `NEXT_PUBLIC_API_BASE_URL` in Railway. The production container deliberately
uses same-origin requests.

## Secrets

Never commit real secret values.

Seal these Railway variables after entering them:

- `JWT_SECRET`
- `INITIAL_ADMIN_PASSWORD`
- `WEB_PUSH_VAPID_PRIVATE_KEY`
- database password/reference when managed manually

The VAPID private key must never be pasted into issues, pull requests, chat logs, or source
files.

## Required production variables

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | Activates production datasource settings |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL user |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |
| `FRONTEND_ORIGIN` | Exact public HTTPS origin used by CORS and WebSocket origin checks |
| `JWT_SECRET` | HMAC signing secret, at least 32 bytes |
| `COOKIE_SECURE=true` | HTTPS-only auth cookies |
| `COOKIE_SAME_SITE=Strict` | Same-origin deployment cookie policy |
| `INITIAL_*` | First SUPER_ADMIN bootstrap values |
| `WEB_PUSH_VAPID_*` | Web Push identity and signing keys |

## First deployment checks

After Railway reports a healthy deployment:

1. `GET https://<app-domain>/health` returns `{"status":"UP"}`.
2. Backend logs show Flyway migrations V1 through V12 complete without errors.
3. Open the app URL and log in.
4. Verify `rehearsal_access` and `rehearsal_refresh` are Secure + HttpOnly cookies.
5. Verify normal API requests are made to the same app origin.
6. Verify `/ws` upgrades to WebSocket and realtime schedule refresh still works.
7. Verify `/sw.js` is activated at scope `/`.
8. Install the PWA from the production HTTPS domain.
9. Enable Web Push and send the existing test Push.
10. Check announcement / reservation / swap flows before marking deployment complete.

## Rollback

If the new deployment fails its health check, keep the previous healthy Railway deployment
active and inspect the deployment logs before retrying. Do not run manual SQL to bypass a
Flyway failure.
