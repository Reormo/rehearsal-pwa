# Current State

## MT 자유형 채팅·버저, 화면 전환, 운영 메뉴, 지난 MT 삭제 (2026-11-18)

- Branch: `feat/mt-games-easter-egg`, parent HEAD `bf5de0e572876351d3d2c8164635288f9e8aa02a`. main 미병합, 운영 미배포.
- 참가자 버저: 외곽 카드를 제거하고 중앙 버튼에 `버저비터`만 표시. 실패·미개방·미배정은 버튼의 비활성화 상태로 표현.
- 문자 게임: 대화 영역 외곽 카드와 상태·빈 메시지 문구 제거. 채팅 말풍선은 좌우 여백을 줄여 배치, 입력창/전송은 하단 고정. `전송 대기 중` 삭제.
- 공통 MT 헤더 뒤로가기는 주소별 부모 경로를 사용(일반 페이지는 기존 기록 기반 유지). MT 하위 페이지끼리의 앞으로/뒤로 전환 애니메이션 복구.
- MT 운영 화면: 게임 정의 목록에서 공개된 게임만 상단 진행 탭에 노출. 설정은 계속 유지. 운영 메뉴는 조 관리/문장 관리(문자 게임 공개 시)/MT 관리자 지정/지난 MT로 구성하며 화살표 제거·스타일 통일.
- 종료된 MT 기록 영구 삭제 API/UI 추가. 행사명 입력 확인; 같은 동아리에서 종료된 행사만 삭제. 연결된 MT 문자 제출→라운드→제외 조→조원→문제→조→행사 순으로 FK를 보존하며 삭제. 활성 행사/타 동아리 기록 보호. DB 스키마·기존 합주 데이터 변경 없음. 되돌릴 수 없는 삭제이므로 운영 전 검증 필수.
- 배경: 유광 3D 느낌의 **벡터 SVG** 스프라이트로 교체해 해상도 무관 선명도 확보, 8개→12개 배치. MT 경로에서만 적용.
- 백엔드 삭제 격리/활성 보호 테스트, 프론트 전환·메뉴·버저·메신저 회귀 테스트 추가.
- 이 세션은 GitHub 커밋·정적 코드 점검까지만 가능. Gradle/Testcontainers, Next/Vitest/lint/build, 브라우저/모바일 실기기 테스트는 **미실행**. 사용자가 로컬 검증한 후 병합·배포 판단.

---


## MT 3D glossy emoji + app-wide polish (2026-11-18)

- Branch: `feat/mt-games-easter-egg`, base `7c5a3af21bce11ca5297997d06a74a0095237d4f`; **main 미병합, 운영 미배포**.
- 디자인: 사용자가 참고한 유광 젤리 질감에서 영감을 받아 생성한 **오리지널** 3D 이모지 (웃음/별눈/파티콘/풍선/소주병·잔/색종이)를 투명 WebP 스프라이트로 최적화. 실제 스톡 이미지와 개별 상표/문구는 복제하지 않음.
- MT 화면에만 흩어진 장식이 표시되도록 별도 컴포넌트 `MtPartyBackdrop`와 `.mt-app-shell` CSS로 제한. 기존의 반복 SVG 패턴은 제거. 홈/예약/공지/관리자/전체는 기존 배경으로 유지.
- 최초 MT 진입 및 MC 권한 확인/데이터 로딩 중 즉시 코랄 스피너 렌더. /my 이스터에그 진입 시 MT 상태 쿼리 사전 요청.
- 게임 공개 스위치: 전체 MT 쿼리 무차별 갱신·공통 busy로 인한 다른 컨트롤의 깜빡임을 줄임. 해당 스위치만 전송 중 비활성화, 호스트 데이터 낙관적 갱신 + 실패 시 롤백, 필요한 쿼리만 재확인.
- 모든 페이지 공통 viewport의 최대 스케일 1 및 userScalable false, 헤더 제목 텍스트 선택 방지. 모바일 브라우저/접근성 설정에서 확대 제한을 무시할 수 있어 실제 iOS/Android 검증 필요.
- 기존 MT 기능/예약/알림/DB 변경 없음. **Gradle/Next/Vitest 및 실기기 검증은 GitHub-only 세션에서 실행하지 못했으며 로컬 확인 필요.**

---


## MT 코랄·3D 파티 디자인 + 게임 상태/기록 개편 (2026-11-18)

- Target branch: `feat/mt-games-easter-egg`, starting HEAD `ae13adf625f40fee446b91782e3b302d8e430235`. **Not merged or deployed.**
- MT 전용 코랄/피치 스타일, 자체 SVG 파티 패턴 배경, 채움형 게임 카드, 같은 높이의 잠금 카드. 상태 안내/화살표 없는 간결한 선택 UI.
- AppShell의 MT 경로별 헤더 제목 추가. MT 콘텐츠 중복 뒤로가기/제목 제거. 합주 페이지 스타일 보존.
- MT 관리자 지정 화면 설명 제거, 메뉴/버튼 및 토글 코랄 통일.
- 백엔드 V18 `buzzer_paused`: MC 수동 닫기 상태와 자동 선착순 성공 상태 구분, 참가자 대기/회색 버튼 반영.
- 문자 입력·전송 고정, 메시지 영역만 스크롤; 모바일 visualViewport 대응.
- 지난 MT 목록·제출 기록·복구 구현. 활성 행사 중 복구 금지, 복구 후 게임 공개 OFF, 기존 기록 보존.
- 변경한 게임/기록 API에 통합 테스트, 게임 카드 UI 테스트 추가. **GitHub 코드 단계에서 Gradle/Testcontainers·Vitest·lint·Next static build 및 모바일 실측은 실행하지 못함**. 사용자 로컬 검증 뒤 병합·배포 판단.
- 기존 합주 예약·알림·인증 권한·운영 데이터 및 기존 DB 마이그레이션 무변경.

---


## MT 관리자 지정 및 역할별 자동 진입 (2026-11-18)

- Branch: `feat/mt-games-easter-egg` (`43fa66f` 기반). **main 미병합 / 운영 미배포**.
- SUPER_ADMIN은 기본 MC이며 `/mt/host/managers`에서 활성 회원을 MT 관리자로 지정·해제한다.
- 지정된 MT 관리자만 슈퍼관리자와 함께 MC API를 호출할 수 있다. 합주 ADMIN 권한과는 독립이며 기존 권한·예약 로직은 변경하지 않았다.
- `/mt`는 MT 관리자를 `/mt/host`로 자동 이동시키고 일반 회원에게 참가자 화면을 표시한다. 이름 5회 터치 이스터에그 유지.
- Flyway V17 `mt_host_managers` 테이블을 추가한다. 기존 DB 데이터 초기화 없음.
- 백엔드 역할 허용/거부 통합 테스트 및 프론트 접근/진입 테스트 추가. 이 환경에서 실제 테스트·빌드 미실행: 로컬에서 검증 후에만 병합·배포.

---

## Active feature handoff — MT Easter Egg (2026-11-18)

- Branch: `feat/mt-games-easter-egg` based on main `79b966690bb4144da0e9ea3ac64097fb77593495`; **not merged or deployed**.
- Added new Flyway V16 schema and authenticated `/api/mt/**` for MT events, teams, buzzer and typing.
- Added participant `/mt`, `/mt/buzzer`, `/mt/typing`; host `/mt/host`, `/mt/host/teams`, `/mt/host/questions`.
- Hidden entry: tap account name 5 times on `/my`; direct `/mt` link also works. No navbar items added.
- MC role policy is superseded by the new MT manager section above. Feature flags still default OFF.
- Buzzer ordering is atomic at the server via PostgreSQL event row lock. Multiple members can press for one MT team.
- Typing is messenger-style, allows re-submission, ranks only latest submission per member, preserves text and team snapshots.
- Tests added: `MtIntegrationTests` (visibility, claim, retries, Unicode, latest submission).
- **Verification pending:** backend full Gradle tests with Docker/Postgres, frontend Vitest/lint/static build, Android/iOS manual check. Tests have not been executed in this environment.
- **Safety:** no modifications to reservation/notification/auth flows or existing DB migrations; only new V16 and MT endpoints. See `docs/MT_GAMES.md` for test and operation steps.
- Continue with local validation and user review; do not merge into main or deploy until approved.

---


## Current task

Polish rehearsal booking/admin UX and fix time-sensitive notification behavior before the next server handoff.

## Current branch

`fix/rehearsal-ux-notification-refresh`

Target base: `main`

Base commit:

`1066c9efbb9803a2a7223a88d1b7992c9894f07b`

## Completed work on this branch

### First-visit loading feedback and low-risk security hardening (2026-11-09)

- Added a compact, delayed (220ms) loading spinner for the initial authenticated landing view, Home reservations and Home rehearsal-calendar fetches. Previously cached content does not show the indicator; original navigation transitions and layout remain intact. Reduced-motion preference disables spinner rotation.
- Added a single-instance in-memory login attempt limiter: 8 attempts per login identifier + observed peer address per 10 minutes; subsequent attempts receive HTTP 429 without revealing account existence. A multi-instance deployment still needs a shared edge/distributed limiter, and the server administrator must verify real client-IP forwarding.
- Member-facing song endpoints now omit other members' login IDs; administrative song APIs retain IDs needed for team management.
- Added Caddy security headers (nosniff, clickjacking protection, privacy-oriented referrer and permissions policy, restrictive frame/object/form CSP directives) and noindex directives. This does not replace authentication or make the site undiscoverable immediately.
- Added backend limiter/privacy regression tests and an auth-loading test. No schema changes, production data changes, or authentication-cookie changes.
- **Pending local validation:** backend full tests, frontend Vitest/lint/static build, Caddy config validation and Docker build, first-visit PWA check, admin song screen, and HTTP 429 behavior. Do not deploy/merge until these pass.


### Stage alert live diagnosis (2026-11-07)

- Confirmed reported symptom: a saved Busking override appeared to be ignored at notification time; this is **not yet resolved** by the existing implementation or tests.
- Upgraded the two-stage regression case to exercise `StageBookingWindowService.upsert` (real admin-save path) instead of inserting the stage window directly with SQL. It asserts stored window then independently validates the default Festival and custom Busking 10-minute/open alerts.
- Added `docs/BOOKING_NOTIFICATION_DIAGNOSTICS.md` with safe SELECT-only queries for the affected round, actual stored override, eligible leader count, and emitted booking notifications. These are necessary to distinguish a wrong-round save or old/pre-change alert from missing Push delivery.
- No production code or DB schema changed; local Docker/Testcontainers tests have NOT been executed yet.


### Stage-specific booking notification regression coverage (2026-11-07)

- Reviewed `NotificationScheduleService`: the existing scheduler already uses `coalesce(stage_override, round_default)` independently per song stage type, filters `song_members.is_leader = true`, and deduplicates by round + stage type + effective open time + recipient.
- Added a PostgreSQL integration regression case for Festival (default) and Busking (custom): pre-open and open notifications must fire at separate times, reach only the matching stage leaders, not reach ordinary team members, and avoid repeat delivery.
- No production notification logic, DB schema, configuration, or frontend behavior changed. Live symptom may reflect deployed code, stored stage settings, or leader assignments and still requires production verification without exposing user data.
- Local backend tests have NOT been run in this GitHub-only session; run the targeted Gradle test and full suite before deploying.


### Refresh icon size follow-up (2026-11-05)

- Corrected the previously oversized refresh glyph: switched to a conventional 20px thin-line two-arrow refresh symbol, matching the approved small icon-only mock.
- Kept the borderless/backgroundless 44px touch area and the existing refresh action / disabled and spinning states.
- Only presentation changed; verify locally before merge.


### Refresh icon-only control (2026-11-05)

- Removed the visible border and button background from the booking-time refresh control while retaining its accessible button, 44px touch target, refresh/spin behavior, and focus indicator.
- Kept the current 32px glyph; no booking logic or backend changes.
- Awaiting local visual and automated verification after GitHub DIRECT commit.


### Announcements list/detail and schedule UI stabilization (2026-11-05)

- Announcements now show only a two-line, wrapping title and short preview; selecting a card opens the static-export-compatible `/announcements/detail?id=...` child route with the full text and author information.
- Full announcement text wraps even long unbroken characters, preventing mobile horizontal overflow. Announcement detail stays under the Notices footer tab.
- Rehearsal schedule dialog uses a stable, viewport-bounded height and a fixed scrollbar gutter; the embedded day details have minimum height to reduce layout shift as dates change.
- UPCOMING booking rounds display one amber status message instead of two; other booking closure reasons remain.
- Booking refresh button dimensions remain unchanged; the SVG is larger with a fuller stroke.
- Added preview-text tests and modal sizing assertion; remote commit requires local tests, lint, static build and mobile inspection.


### Nested-page navigation, schedule modal and booking-calendar selection (2026-11-05)

- "전체 팀 보기" in My Teams is now "전체 곡 보기" and links to `/songs` for every role.
- `/songs` is treated as a child of the All tab, preserving the active footer icon.
- My Reservations now opens a dark-backdrop schedule dialog; its calendar and daily schedule reuse the Home component.
- Child-to-child navigation (including browser back) has no slide; tab and tab-to-child animations remain.
- UPCOMING booking dates are selectable, but selecting and submitting reservation slots remains disabled; refresh SVG enlarged without changing button dimensions.
- The All menu has distinct icons for My Teams and All Songs.
- Added regression tests for nested transitions and dialog dismissal/focus. Local automated tests have not yet run.


### First-visit tab animation stabilization (2026-11-05)

- Deferred page-entry animation until the destination pathname is rendered; the outgoing screen no longer animates while Next.js prepares a previously unvisited route.
- Kept existing left/right tab and browser-back directions, transition duration, and page layout.
- Limited animation-end clearing to the page surface itself, not bubbled child animations.
- Added provider regression tests for first navigation, direction changes, and browser back.
- Pushed as a GitHub DIRECT change for local user verification; no local build, lint, or UI test has been run for this commit yet.


### Booking / member UX

- Reservation calendar state cleanup:
  - past dates and inactive dates now show only the date number
  - upcoming prepared week dates are disabled and labeled `준비 중`
  - active bookable week dates are labeled `예약 가능`
  - the previous `10~22` per-day label was removed
- Home upcoming rehearsal action copy changed from `전체보기` back to `수정하기`.

- Header/title refinement after UI review:
  - fixed app header now carries the current page title instead of animating a duplicate body title
  - Home header alone uses the brand name `무혼`
  - main-tab headers are `합주 예약`, `공지`, `관리자`, and `전체`
  - route-specific subpages also expose their page title in the fixed header
  - duplicate body titles were removed from Home/Reservation/Announcements/Admin main tabs
  - `내 계정` was removed from the All-tab menu because account identity/logout information is already shown in the account summary card

- Main-tab first-content layout refinement:
  - `전체` now starts with an account summary card showing name, login ID, account type, and logout
  - the old account summary card was removed from Home to avoid duplication
  - Home starts with a bold `홈` heading
  - Reservation starts with a bold `합주 예약` heading
  - Announcements starts with a bold `공지` heading
  - Admin starts with a bold `관리자` heading
  - the detailed `내 계정` menu item remains available for password/account-management actions

- Mobile UI/navigation refinement after the second browser review:
  - shared authenticated header is centered on `무혼`
  - main tabs (Home/Reservation/Notice/Admin/All) do not show an in-app back button
  - subpages show a top-left back button while keeping the notification bell on the right
  - bottom-tab navigation uses replace-style navigation so switching main tabs does not keep stacking browser history
  - static-export path variants (trailing slash, index.html, .html) are normalized before route comparison
  - the currently active main tab is rendered as a non-link button, so tapping it is a complete no-op with no navigation or animation
  - only exact bottom-navigation destinations are treated as main tabs; subpages alone show the in-app back button
  - page transition duration is 360ms
  - only exact bottom-navigation destinations are treated as main tabs; subpages alone show the in-app back button
  - page transition duration is 300ms (slightly slower than the previous 220ms)
  - moving to a tab on the right uses a right-entry animation; moving to a tab on the left uses a left-entry animation
  - links from a main page into a subpage enter from the right; the in-app back action returns with a left-entry animation
  - OS/browser history cannot be forcibly cleared or the PWA forcibly closed, so Galaxy hardware-back exit behavior remains browser/OS controlled
  - reservation refresh moved beside `4. 시간 선택` and the icon/button were enlarged
  - the helper text under `3. 예약 길이 선택` was removed
  - reservation race warning moved below the submit button, reduced to small neutral text, and keeps a compact warning icon
  - redundant small-label + large-title pairs were removed from Home and obvious admin/swap sections
  - schedule `MY REHEARSALS` duplicate label was removed

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
- after the later mobile copy/eyebrow cleanup, frontend lint/test/build passed
- after the newest navigation/header/transition refinement, frontend Vitest and static build passed, but ESLint rejected direct DOM mutation in AppShell
- navigation transition state was then moved into a root React Context provider; frontend lint/test/build must be re-run after this structural fix
- after the main-tab heading/account-card refinement, frontend lint/test/build must be re-run again before merge
- after moving titles into the fixed header and removing the duplicate account menu item, frontend lint/test/build must be re-run again before merge
- after reservation-calendar state cleanup and Home action-copy change, frontend lint/test/build must be re-run again before merge

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

Verify announcement list/detail navigation (including long numeric text and direct deep link), notice-tab highlight, schedule modal position during date changes and on narrow devices, UPCOMING notice deduplication, and the larger refresh glyph. Run frontend Vitest, lint, and static build before merge.


For this commit, validate local admin/member My Teams navigation, /songs footer selection, dialog month/day and close behavior, child-to-child/parent transitions, UPCOMING dates, and refresh icon size. Run frontend Vitest, lint, and static production build before merging.


Before merge: locally validate first-visit tab transitions (Home → Reservation / Notice, then return), browser back, and repeat navigation in dev and production builds. Run `npm.cmd run test:run`, `npm.cmd run lint`, and `NEXT_STATIC_EXPORT=true npm.cmd run build`. If first-visit jitter remains, investigate Next.js dev compilation and page loading layout changes separately.


1. Pull the newest navigation/header changes.
2. Re-run frontend lint, tests, and static build.
3. Inspect the header/back-button rules and left/right transitions in browser/mobile widths.
4. Test Android popup behavior on the actual Galaxy device.
5. Fix any failures found during manual verification and push them to this branch.
6. Open/review the PR only after manual verification.
7. Merge to `main` only after approval.
8. Create the final source ZIP from verified `main` and hand it to the server administrator.

## Blockers

- Local runtime/device verification is pending.
