# Current State

## Current task

Polish rehearsal booking/admin UX and fix time-sensitive notification behavior before the next server handoff.

## Current branch

`fix/rehearsal-ux-notification-refresh`

Target base: `main`

Base commit:

`1066c9efbb9803a2a7223a88d1b7992c9894f07b`

## Completed work on this branch

### Booking / member UX

- Simplified authenticated page headers and reservation copy after mobile UI review:
  - the shared top bar keeps only the current tab title (Home/Reservation/Notice/Admin/All)
  - removed English eyebrow labels above authenticated page titles (for example MY REHEARSALS / ADMIN · SCHEDULE / NOTIFICATIONS)
  - removed the booking-page leader-team count badge
  - removed the time-selection helper paragraph, "start available" count, and general-slot heading/helper copy
  - booking-open/close display uses compact `M.D(weekday) HH:mm`
  - duplicate reservation display uses `중복 예약 current/max건`, except max=99 shows only `중복 예약 current건`
  - booking-submit helper was replaced with a single amber warning line and warning icon
- Native team selection remains a browser `<select>`; desktop Chrome device emulation may render its popup outside the narrow emulated viewport, while real iPhone Safari/PWA uses the OS-native picker presentation.

- Added a fixed-size icon-only refresh button to the booking page.
  - Refetches calendar, selected-day schedule, booking options, and my reservations.
  - No "refreshing" / "just refreshed" copy is rendered, so the layout does not jump.
- Simplified booking-window display to compact "예약 시작 / 예약 마감" rows.
- Shows whether the visible window is a stage-specific setting or the round default.
- Primary tab headers are simplified to one bold section name (`홈 / 예약 / 공지 / 관리자 / 전체`); redundant user/role, WELCOME/SCHEDULE labels, and intro copy were removed from the five main tab pages.
- Booking-window timestamps now render with weekday, e.g. `10.31(토) 20:00`.
- Booking summary now shows only duplicate-reservation usage:
  - normal limit: `중복 예약 0/2건`
  - 99-limit mode: denominator hidden, e.g. `중복 예약 50건`
- The booking-team select is constrained to the page width; native picker presentation still follows the browser/OS.
- Home now shows all upcoming rehearsals instead of only four.
- Home link copy changed from "수정하기" to "전체보기".
- Member rehearsal cards no longer expose internal reservation IDs.
- My Teams cards expand on tap/click to show members, sessions, and leader status.
- All Songs cards now reserve a stable two-line title area so long song titles do not misalign the grid.
- Admin member search/password inputs include autocomplete hints to reduce credential autofill prompts.
- Admin reservation editing now uses separate date/hour/minute controls.
  - Minute selection contains only `00` and `30`.
  - The action button is labeled `설정` and submits immediately without a confirmation dialog.
  - Change reason is optional in the UI; when omitted, the audit reason defaults to `관리자 예약 시간 변경`.
  - Backend reservation validation already enforces 00/30-minute start boundaries.

### Operating hours / time input

- Reworked admin operating-hours management around a Sunday-first monthly calendar.
- Admins can select multiple dates first, then apply one operating-hours override to all selected dates.
- Existing override dates are marked in the calendar.
- Selected dates can also be restored to the default 10:00-22:00 schedule.
- Booking-round and stage booking datetime inputs now use 30-minute steps.
- Backend round/stage booking-window updates also reject times outside 00/30-minute boundaries.
- Existing test fixtures that intentionally open booking windows in the past were aligned to valid 30-minute boundaries.

### Notifications / Web Push

- Booking-open notification dedupe keys now include the effective booking-open instant.
  - If an admin changes a stage-specific booking time after an earlier alert was generated, the new effective time can produce its own correct alert.
- Added notification integration coverage for stage-specific booking windows.
- Rehearsal reminders now use a two-minute lookback so stale reminders are not sent long after their configured time.
- Time-sensitive pushes (booking open/pre-open, rehearsal reminders, popup test) use:
  - TTL 300 seconds
  - Web Push urgency HIGH
- General pushes such as announcements keep the existing normal policy.
- Android notification settings explain that heads-up popup behavior is controlled by the OS/browser notification settings.
- Popup-test copy now asks the user to verify that the notification appears over another app.

## Verification status

Local verification is in progress.

Completed locally on 2026-11-03:
- backend full Gradle tests: passed (`BUILD SUCCESSFUL`)
- frontend Vitest: 5 files / 11 tests passed
- frontend static production build with `NEXT_STATIC_EXPORT=true`: passed
- frontend lint initially found 1 warning + 1 error in branch-added code:
  - unused admin reservation datetime helper
  - synchronous Android `setState` inside an effect
- both lint findings were fixed on the branch
- frontend ESLint re-run: passed with no warnings/errors
- frontend Vitest re-run after lint fixes: 5 files / 11 tests passed
- frontend static production build re-run after lint fixes: passed

This branch was edited directly through GitHub as explicitly requested.

Required local verification before merge:

### Backend

```powershell
cd C:\dev\rehearsal-pwa\backend
.\gradlew.bat test
```

### Frontend

```powershell
cd C:\dev\rehearsal-pwa\frontend
npm.cmd run test:run
npm.cmd run lint
$env:NEXT_STATIC_EXPORT="true"
npm.cmd run build
```

### Manual UI / device checks

- shared top bar shows only the active tab title
- authenticated page titles have no English eyebrow labels
- booking team picker width/presentation on actual iPhone Safari/PWA (not only Chrome device emulation)
- booking refresh while waiting for booking-open time
- booking refresh after another reservation occupies a slot
- compact booking-open/close display
- multi-date operating-hours apply + restore
- long song titles in All Songs
- member expansion in My Teams
- Home upcoming rehearsal list
- admin member search autofill behavior
- 30-minute datetime picker behavior on desktop/Android/iPhone
- admin reservation edit date/hour/00·30-minute controls and immediate `설정` apply
- stage-specific booking-open alert
- stale reminder suppression
- Galaxy popup test while another app is in the foreground
- iPhone/iPad installed PWA push behavior where available

## Important decisions

- No refresh-status text is shown; the refresh button remains visually stable.
- Web Push urgency can prioritize delivery but cannot force Android heads-up presentation.
  Android/One UI notification popup/importance settings remain the final presentation control.
- Do not request Android "display over other apps" permission; Web Push heads-up notifications do not use overlay permission.
- Existing production PostgreSQL data, volumes, environment variables, domain, and routing must remain unchanged.
- No Flyway migration is required for this task.

## Known documentation drift

Existing unrelated drift remains intentionally out of scope:

1. POLICY still contains older wording that songs are never physically deleted, while current implementation permits conditional archived-song hard deletion.
2. Older reservation-policy text describes the former Boolean multiple-reservation policy, while current implementation supports per-stage/per-song limits of 1-99.
3. Railway deployment documentation still references older migration state.
4. Confirm the actual production deployment path before infrastructure changes.

## Remaining work

1. Inspect the requested UI changes in browser/mobile widths.
2. Test Android popup behavior on the actual Galaxy device.
3. Fix any failures found during manual verification and push them to this branch.
4. Open/review the PR only after manual verification.
5. Merge to `main` only after approval.
6. Create the final source ZIP from verified `main` and hand it to the server administrator.

## Blockers

- Local runtime/device verification is pending.
