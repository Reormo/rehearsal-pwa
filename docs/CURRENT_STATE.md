# Current State

## Current task

Establish a reusable AI development harness pattern, then apply it to the 무혼 rehearsal reservation PWA.

## Current branch

`docs/muhon-agent-harness`

Target base:

`main`

Base commit at task start:

`1f11ddd35b323939de3d1b2d8bfd5c8978e5b8a9`

## Completed work

- Reworked the harness into two layers:
  1. reusable generic AI development harness
  2. 무혼-specific repository overrides
- Added explicit PATCH mode for normal ChatGPT workflows.
- Added explicit DIRECT mode for Codex/local coding agents.
- Clarified Source of Truth:
  - current implementation is established by code/Git/migrations/tests/deployment state
  - desired behavior is established by human-approved policy/specification
  - code drift does not silently become policy
- Added exact base-commit recording for generated patches.
- Added protections for existing local changes and destructive Git commands.
- Added generic rules for:
  - minimal change scope
  - API contracts
  - DB migrations
  - testing/CI
  - debugging
  - security
  - deployment
  - documentation drift
  - Definition of Done
- Added 무혼-specific rules for:
  - `main` as default base branch
  - Java 21 / Spring Boot / PostgreSQL / Flyway / Next.js PWA stack
  - exact backend/frontend verification commands
  - HttpOnly cookie authentication invariants
  - Flyway and production DB/volume safety
  - 30-minute scheduling and 10:00-22:00 default room hours
  - current 1-99 maximum reservation-count policy
  - archived-song conditional hard delete behavior
  - mobile/PWA UX constraints
  - production domain and server-admin archive handoff
  - mandatory `docs/CURRENT_STATE.md` handoff policy

## Recent implemented state

PR #15 was merged into `main`.

Merge commit:

`1f11ddd35b323939de3d1b2d8bfd5c8978e5b8a9`

Recent behavior includes:

- per-stage maximum reservations per song/round
- 1-99 reservation-limit UI using a vertical number wheel
- current/max reservation count exposure
- expired room-exception cleanup
- active-song catalog filtering/sorting
- archived-song hard deletion when reservation references do not block it
- Sunday-first app calendars with Sunday/Saturday color distinction
- mobile admin schedule overflow improvements
- PWA authentication/update recovery improvements
- Flyway V15

## Important decisions

- The reusable harness and project-specific rules are conceptually separate layers.
- The root `AGENTS.md` in this repository contains both the generic rules and the 무혼 overrides so agents need one entry point.
- `main` is the default target/base branch for new work.
- ChatGPT PATCH mode should use unified Git patches and report exact base branch/commit.
- Codex/local agents may edit directly after reading `AGENTS.md` and this file.
- Existing production PostgreSQL data, volumes, environment variables, and routing must be preserved.
- Already-deployed Flyway migrations are immutable; schema changes use new forward migrations.
- Authentication remains HttpOnly-cookie based; auth tokens must not be moved to `localStorage`.
- Product-policy conflicts must be reported rather than silently resolved.
- Pre-existing unrelated documentation drift should be recorded and handled in a dedicated docs-sync task rather than mixed into feature work.

## Tests / CI status

For PR #15 before merge:

- backend full Gradle tests: passed
- frontend Vitest: 5 files / 11 tests passed
- frontend ESLint: passed
- frontend static production build with `NEXT_STATIC_EXPORT=true`: passed
- local UI inspection: completed

For the harness documentation branch:

- runtime code is unchanged
- only documentation files are changed
- Markdown structure/diff should be reviewed before merge
- `git diff --check` should be run locally before merge

## Known documentation drift

These are known mismatches that future work must not silently "fix" without review:

1. `docs/REHEARSAL_PWA_POLICY.md` still contains older wording that songs are never physically deleted. Current implementation permits hard deletion of an archived song when reservation references no longer block deletion.
2. Older reservation-policy text still describes multiple reservations primarily as a Boolean setting. Current implementation supports a per-stage/per-song reservation maximum of 1-99.
3. `docs/RAILWAY_DEPLOYMENT.md` still mentions Flyway migrations only through V12 in its deployment checks. The repository currently contains migrations through V15.
4. Deployment documentation describes Railway, while operational delivery may also be handed to a server administrator as a source archive. Confirm the actual production deployment path before changing infrastructure.

## Remaining work

- Review PR #16.
- Run local `git diff --check` after pulling the branch if desired.
- Merge the harness PR when the rules are accepted.
- In a later dedicated documentation-sync task, reconcile known policy/ERD/deployment drift with the current approved behavior.

## Blockers

None.
