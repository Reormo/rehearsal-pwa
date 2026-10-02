# AGENTS.md

## Purpose

This repository uses a strict AI-assisted development workflow so that changes remain reproducible, reviewable, safe, and easy to resume.

These rules apply to ChatGPT, Codex, and other coding agents working on the 무혼 합주 예약 PWA.

The goals are:

- preserve human review and control
- avoid accidental edits to unrelated code
- keep Git history clean and understandable
- make interrupted work resumable
- keep secrets out of source control and chat
- require verification before merge or deployment
- make every change easy to inspect, revert, or continue
- preserve existing production data and deployment behavior

---

## 1. Operating Modes

There are two supported operating modes. Do not mix them inside one task without explicitly reporting the switch.

### A. PATCH mode — normal ChatGPT workflow

Normal ChatGPT does not directly modify the developer's local repository by default.

Workflow:

1. Read this file.
2. Read `docs/CURRENT_STATE.md`.
3. Inspect the current repository state, logs, screenshots, diffs, PRs, or CI as needed.
4. Diagnose the requested change.
5. Record the patch base:
   - target branch
   - exact base commit SHA
6. Create a focused unified Git patch.
7. The developer applies it locally:

   ```powershell
   git apply --check .\<patch-file>
   git apply .\<patch-file>
   ```

8. The developer reviews the change in the IDE.
9. Run the required verification described below.
10. Commit and push from a focused branch.
11. Open a PR.
12. Merge only after the diff and required checks are verified.

Patch requirements:

- use repository-relative paths
- use proper unified Git patch format
- use `new file mode` for new files
- preserve unrelated local changes
- do not include secrets
- include tests when behavior changes
- keep the patch focused on one task
- do not replace source files through PowerShell `Set-Content` or similar whole-file rewrites unless explicitly necessary
- avoid line-ending-only changes

GitHub access in PATCH mode is primarily for:

- reading current repository state
- checking branches, commits, PRs, diffs, and CI
- verifying whether a proposed change already exists
- validating state before merge or deployment

Do not mutate the remote repository through GitHub tools unless the developer explicitly requests that action.

### B. DIRECT mode — Codex / local coding agent workflow

A local coding agent may directly edit the checked-out repository.

Before editing:

1. Read this `AGENTS.md`.
2. Read `docs/CURRENT_STATE.md`.
3. Inspect:

   ```bash
   git branch --show-current
   git status
   git log --oneline -n 10
   ```

4. Start from the latest `main`.
5. Create a focused feature/fix/docs branch unless the human explicitly requests otherwise.

During work:

- edit only files relevant to the task
- preserve unrelated local changes
- add or update tests for changed behavior
- run targeted tests first
- run the broader required verification before completion when practical
- inspect `git diff` before committing
- do not silently change architecture, public API contracts, authentication behavior, database semantics, or deployment topology

Before stopping, handing off, or opening a PR:

- update `docs/CURRENT_STATE.md`
- record what was completed
- record the current branch
- record tests run and their result
- record remaining work
- record blockers or known documentation drift
- never record credentials, tokens, passwords, private keys, or secret values

---

## 2. Source-of-Truth and Conflict Rules

Use these repository sources for different purposes:

- `AGENTS.md`: development workflow and safety rules
- `docs/CURRENT_STATE.md`: latest handoff checkpoint and recently approved decisions
- `docs/REHEARSAL_PWA_POLICY.md`: product and operating policy
- `docs/REHEARSAL_PWA_ERD.md`: data model and persistence design
- `docs/RAILWAY_DEPLOYMENT.md`: repository deployment architecture documentation
- source code and Flyway migrations: currently implemented behavior

Do not silently reconcile conflicting sources.

If two sources disagree:

1. identify the conflict
2. determine whether `CURRENT_STATE.md` records a newer human-approved decision
3. inspect the current implementation
4. report the mismatch to the developer
5. do not change product policy or architecture solely to make documents agree

`CURRENT_STATE.md` may record a newer human-approved decision, but an agent must not invent policy changes in that file.

Current task instructions from the human may override older project decisions, but the agent must make the impact explicit before changing security, database semantics, public APIs, or deployment architecture.

---

## 3. Git Branch Rules

The default target branch is:

```text
main
```

Do not use an old merged feature branch as a base.

Before starting implementation:

```bash
git switch main
git pull --ff-only origin main
git switch -c <type>/<short-description>
```

Recommended prefixes:

- `feat/`
- `fix/`
- `chore/`
- `docs/`
- `refactor/`
- `test/`

Before committing:

```bash
git status
git diff
git diff --check
```

Before pushing:

- verify the current branch
- verify the intended base commit
- verify no unrelated files are included

Keep commits focused. Avoid mixing cleanup, refactoring, formatting, and feature work unless required for the same change.

Do not force-push `main`.

A feature branch may use `--force-with-lease` only when history rewriting is intentional and the developer understands why.

---

## 4. Pull Request Rules

Every meaningful code change should normally go through a PR targeting `main`.

A PR should explain:

- the problem
- the change
- important implementation decisions
- tests executed
- deployment or migration impact
- known limitations
- documentation drift left intentionally unresolved

Before merge, verify:

- diff is limited to intended files
- required tests pass
- CI passes when CI is configured for the affected area
- no secrets are present
- no generated build artifacts were accidentally committed
- configuration changes are documented
- DB/schema changes include a forward migration and deployment impact note

If CI is not configured for the affected area, do not claim "CI passed". Record the local verification that actually ran.

---

## 5. `docs/CURRENT_STATE.md` Handoff Checkpoint

`docs/CURRENT_STATE.md` is the single lightweight checkpoint for ongoing AI-assisted work.

Update it:

- after meaningful implementation work
- before stopping
- before opening a PR
- when blocked
- before handing work to another AI agent
- after an approved architecture, deployment, security, or database decision

Do not update it for trivial typo-only changes unless they materially affect handoff.

Keep it factual and short enough to scan quickly.

Never put secrets in it.

---

## 6. Project Stack

Current repository stack:

### Backend

- Java 21
- Spring Boot 4.1.x
- Gradle Wrapper
- PostgreSQL
- Spring Data JPA
- Spring Security
- Flyway
- Testcontainers
- WebSocket / STOMP
- Web Push

### Frontend

- Next.js 16.x
- React 19.x
- TypeScript
- Tailwind CSS 4
- TanStack Query
- Vitest
- React Testing Library
- static export for production PWA

### Production packaging

- root `Dockerfile`
- Next.js static export
- Spring Boot JAR
- Caddy serves static files and proxies backend routes
- PostgreSQL is persistent external state

Do not change these architecture choices as a side effect of unrelated work.

---

## 7. Required Verification

Use the smallest useful test first, then broader verification.

### Backend behavior changes

On Windows PowerShell:

```powershell
cd backend
.\gradlew.bat test
```

On POSIX:

```bash
cd backend
./gradlew test
```

For a narrow backend fix, targeted tests may be run first, but the full backend test task should pass before completion when practical.

### Frontend behavior changes

On Windows PowerShell:

```powershell
cd frontend
npm.cmd run test:run
npm.cmd run lint
$env:NEXT_STATIC_EXPORT="true"
npm.cmd run build
```

On POSIX:

```bash
cd frontend
npm run test:run
npm run lint
NEXT_STATIC_EXPORT=true npm run build
```

### Full-stack changes

If both backend and frontend behavior change, run both backend and frontend verification.

### Documentation-only changes

Runtime tests are not required unless the documentation change depends on generated output or commands that should be validated.

At minimum:

```bash
git diff --check
```

and manually inspect the rendered Markdown/diff.

### Authentication / authorization changes

Add or update regression coverage for both:

- allowed access
- denied/unauthenticated access

Do not weaken route protection to make a test pass.

### Database / Flyway changes

- run backend tests
- verify the migration applies against PostgreSQL/Testcontainers when covered by the suite
- inspect migration ordering
- document production migration impact

### PWA / Service Worker changes

In addition to frontend verification:

- verify static export contains the expected PWA assets
- manually check update/install behavior when the task affects Service Worker lifecycle

### Deployment-file changes

If `Dockerfile`, `deploy/`, production properties, or deployment environment examples change:

- build the relevant artifact/container when practical
- inspect startup behavior
- verify `/health`
- document any required administrator action

---

## 8. Debugging Rules

Do not guess at a fix when logs or a stack trace can identify the cause.

Preferred flow:

1. reproduce
2. capture the exact error
3. inspect the relevant code/configuration
4. identify the root cause
5. make the smallest reasonable fix
6. add regression coverage when practical
7. re-run the failing scenario
8. run broader verification

For production errors, do not recommend destructive restarts, database resets, or volume deletion as a shortcut.

---

## 9. Configuration and Secrets

Never commit or paste into chat:

- passwords
- OAuth client secrets
- access tokens
- JWT secrets
- VAPID private keys
- database credentials
- private keys
- temporary authorization codes

Use environment variables or the existing secret-management/deployment mechanism.

When checking whether a value exists, print presence only.

PowerShell example:

```powershell
if ($env:JWT_SECRET) {
    "JWT_SECRET=SET"
} else {
    "JWT_SECRET=NOT_SET"
}
```

Do not ask the developer to paste real secrets into chat.

---

## 10. Database and Flyway Safety

Production data must be preserved.

Rules:

- never edit an already-deployed Flyway migration
- add a new forward migration such as `V16__...` instead
- never reset the production database to resolve a migration problem
- never delete the PostgreSQL production volume
- never run `docker compose down -v` against production data
- never recommend manual SQL that bypasses Flyway merely to make startup succeed
- schema changes must preserve existing data unless explicit destructive migration approval is given
- inspect foreign-key and historical-data implications before hard deletion features

If a migration fails in production, preserve the database and investigate the exact Flyway/database error first.

---

## 11. Authentication and Security Invariants

Current authentication design uses HttpOnly cookies for access/refresh authentication.

Rules:

- do not move auth tokens to `localStorage`
- preserve `HttpOnly` authentication cookie behavior
- preserve `Secure` cookies in production
- do not weaken protected routes to remove a 401/403
- distinguish authentication failures from CORS, configuration, mapping, and application failures
- do not expose internal exceptions or secrets in public error responses

SUPER_ADMIN protection is security-sensitive. Changes to deletion, demotion, bootstrap, or authorization rules require explicit review and regression tests.

---

## 12. Scheduling and Reservation Invariants

Unless the human explicitly changes product policy, preserve the current design:

- booking time is built on 30-minute atomic slots
- default room operating hours are 10:00-22:00
- date-specific operating-hour exceptions may override the default
- reservation duration is constrained to supported 30-minute increments
- operational round logic and UI calendar layout are separate concepts
- administrators must not silently bypass reservation integrity constraints
- reservation changes that require locks/transactions must preserve concurrency safety
- historical references must not be broken by destructive deletion

If a requested feature conflicts with `docs/REHEARSAL_PWA_POLICY.md` or current persistence constraints, report the conflict before redesigning the model.

---

## 13. Frontend / UX Constraints

- mobile PWA behavior is a first-class requirement
- avoid horizontal overflow on admin and schedule screens
- preserve same-origin production API behavior unless explicitly changing deployment architecture
- native browser/OS pickers cannot be assumed to support custom visual styling
- do not replace accessible native semantics with custom UI without preserving keyboard/screen-reader behavior
- user-visible changes should be manually inspected at mobile width when practical

---

## 14. Deployment Safety

Do not change deployment architecture as a side effect of an unrelated fix.

Architecture-level changes requiring explicit approval include:

- changing cloud/server provider
- introducing or replacing a reverse proxy
- changing ports
- replacing Docker packaging
- replacing PostgreSQL
- changing frontend/backend origin topology
- changing cookie SameSite strategy
- changing domain routing
- changing persistent volume layout

The repository contains Railway deployment documentation, but actual production handoff may be performed by a server administrator. Do not assume which operational path is active when making deployment changes; confirm the target deployment process first.

When preparing a source archive for a server administrator, prefer an exact Git revision:

```powershell
git switch main
git pull --ff-only origin main
git archive --format=zip --output="<destination>\rehearsal-pwa-server.zip" main
```

A deployment update must preserve:

- existing PostgreSQL data
- persistent volumes
- production environment variables
- domain/routing configuration

For production deployment verification:

1. confirm the intended revision
2. build successfully
3. start the replacement safely
4. inspect startup logs
5. verify Flyway completed
6. verify `/health`
7. verify login/authentication
8. smoke-test critical reservation behavior
9. verify the externally visible PWA

Do not claim deployment succeeded until the running service was checked.

---

## 15. Change Scope

Prefer the smallest change that fully solves the problem.

Avoid:

- unrelated refactoring
- broad formatting changes
- renaming unrelated code
- dependency upgrades without a concrete reason
- silent public API changes
- silent database semantic changes
- weakening authentication or authorization to make a test pass

If a larger architectural change becomes necessary, stop and explain why before proceeding.

---

## 16. Dependency Changes

When changing a dependency version:

- identify the concrete reason
- check framework/runtime compatibility
- prefer the smallest supported upgrade
- run regression tests
- verify affected runtime behavior, not only compilation

Do not bundle dependency upgrades into unrelated feature work.

---

## 17. Communication Rules for AI Agents

When reporting work:

- state what changed
- state what was verified
- distinguish facts from assumptions
- do not claim tests passed unless they actually ran
- do not claim CI passed unless CI actually ran
- do not claim deployment succeeded unless the running service was verified
- do not hide failed attempts
- give exact next commands when human action is required

When blocked, report the blocker instead of inventing missing information.

When a repository document appears stale, identify the exact stale statement and the current implementation/decision that conflicts with it.

---

## 18. Default Completion Checklist

Before considering a coding task complete:

- [ ] `AGENTS.md` read
- [ ] `docs/CURRENT_STATE.md` read
- [ ] working tree inspected
- [ ] latest `main` used as base
- [ ] focused branch used
- [ ] intended change implemented
- [ ] unrelated changes avoided
- [ ] tests added or updated where appropriate
- [ ] targeted tests passed
- [ ] broader required verification passed when practical
- [ ] `git diff` reviewed
- [ ] `git diff --check` passed
- [ ] no secrets committed
- [ ] Flyway rules respected
- [ ] `docs/CURRENT_STATE.md` updated for meaningful agent-driven work
- [ ] PR created for meaningful changes
- [ ] CI checked when configured
- [ ] deployment smoke-tested when deployment was part of the task
