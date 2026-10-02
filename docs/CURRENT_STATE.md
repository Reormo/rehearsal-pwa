# Current State

## Current task

Establish the repository-specific AI development harness for the 무혼 rehearsal reservation PWA.

## Current branch

`docs/muhon-agent-harness`

Target base: `main`

Base commit at task start:

`1f11ddd35b323939de3d1b2d8bfd5c8978e5b8a9`

## Completed work

- Added repository-specific AI workflow rules in `AGENTS.md`.
- Defined PATCH mode for normal ChatGPT work.
- Defined DIRECT mode for Codex/local coding agents.
- Fixed the default development base branch to `main`.
- Added exact backend/frontend verification commands.
- Added production database/Flyway safety rules.
- Added authentication/security invariants.
- Added scheduling/reservation invariants.
- Added PWA/mobile UX constraints.
- Added deployment handoff and production-safety rules.
- Added explicit source-of-truth/conflict handling so agents do not silently reconcile stale documents.

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

- `main` is the default target/base branch for new work.
- ChatGPT PATCH mode should prefer unified Git patches rather than whole-file PowerShell rewrite scripts.
- Codex/local agents may edit directly after reading `AGENTS.md` and this file.
- Existing production PostgreSQL data, volumes, environment variables, and routing must be preserved.
- Already-deployed Flyway migrations are immutable; schema changes use new forward migrations.
- Authentication remains HttpOnly-cookie based; auth tokens must not be moved to `localStorage`.
- Product-policy conflicts must be reported rather than silently resolved.

## Tests / CI status

For PR #15 before merge:

- backend full Gradle tests: passed
- frontend Vitest: 5 files / 11 tests passed
- frontend ESLint: passed
- frontend static production build with `NEXT_STATIC_EXPORT=true`: passed
- local UI inspection: completed

No runtime tests are required solely for adding this documentation harness, but the harness PR diff should be reviewed and `git diff --check` should pass.

## Known documentation drift

These are known mismatches that future work must not silently "fix" without review:

1. `docs/REHEARSAL_PWA_POLICY.md` still contains older wording that songs are never physically deleted. Current implementation permits hard deletion of an archived song when reservation references no longer block deletion.
2. Older reservation-policy text still describes multiple reservations primarily as a Boolean setting. Current implementation supports a per-stage/per-song reservation maximum of 1-99.
3. `docs/RAILWAY_DEPLOYMENT.md` still mentions Flyway migrations only through V12 in its deployment checks. The repository currently contains migrations through V15.
4. Deployment documentation describes Railway, while operational delivery may also be handed to a server administrator as a source archive. Confirm the actual production deployment path before changing infrastructure.

## Remaining work

- Review and merge the harness PR.
- In a later dedicated documentation-sync task, reconcile the known policy/ERD/deployment documentation drift with the current approved behavior.
- Keep this file updated when future agent-driven work changes important implementation or decisions.

## Blockers

None.
