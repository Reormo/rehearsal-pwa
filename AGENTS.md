# AGENTS.md — 무혼 합주 예약 PWA 전용 규칙

> 이 파일은 무혼 저장소의 프로젝트 전용 규칙만 포함한다.
> 범용 AI 개발 하네스는 저장소 밖에서 별도로 관리하며, 이 저장소에는 프로젝트 차이만 기록한다.

## 1. Repository / Base Branch

Repository:

~~~text
Reormo/rehearsal-pwa
~~~

기본 target/base branch:

~~~text
main
~~~

새 작업은 최신 main에서 시작한다.
이미 merge된 feature branch를 새 작업 base로 사용하지 않는다.

PATCH 모드에서는 patch 생성 전 exact main commit SHA를 기록한다.

---

## 2. Source of Truth Documents

목적별 기준:

- AGENTS.md: AI 개발 workflow와 safety rule
- docs/CURRENT_STATE.md: 최근 handoff, 최근 human-approved decision, known drift
- docs/REHEARSAL_PWA_POLICY.md: 제품/운영 정책
- docs/REHEARSAL_PWA_ERD.md: 데이터 모델/영속성 설계
- docs/RAILWAY_DEPLOYMENT.md: 저장소에 기록된 Railway 배포 구조
- source code + Flyway: 현재 실제 구현

문서와 구현이 다르면 silent reconciliation 금지.
CURRENT_STATE에 더 최근 human-approved decision이 명시되어 있는지 확인하고,
불명확하면 사용자에게 충돌을 보고한다.

docs/CURRENT_STATE.md는 이 프로젝트에서 필수 handoff checkpoint다.

---

## 3. Stack

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
- production static export
- PWA

### Packaging / Runtime

- repository root Dockerfile
- Next.js static export
- Spring Boot JAR
- Caddy
- PostgreSQL persistent data

이 구조를 unrelated fix의 side effect로 변경하지 않는다.

---

## 4. Exact Verification Commands

### Backend behavior change — Windows PowerShell

~~~powershell
cd backend
.\gradlew.bat test
~~~

### Backend behavior change — POSIX

~~~bash
cd backend
./gradlew test
~~~

### Frontend behavior change — Windows PowerShell

~~~powershell
cd frontend
npm.cmd run test:run
npm.cmd run lint
$env:NEXT_STATIC_EXPORT="true"
npm.cmd run build
~~~

### Frontend behavior change — POSIX

~~~bash
cd frontend
npm run test:run
npm run lint
NEXT_STATIC_EXPORT=true npm run build
~~~

### Full-stack behavior change

Backend + Frontend 검증을 모두 실행한다.

### Documentation-only change

최소:

~~~bash
git diff --check
~~~

그리고 Markdown/diff를 직접 검토한다.

### PWA / Service Worker change

Frontend 검증에 추가:

- static export에 필요한 PWA asset 확인
- Service Worker install/update 동작 확인
- mobile/standalone 동작 수동 확인 when practical

### DB / Flyway change

- backend test 실행
- migration ordering 확인
- PostgreSQL/Testcontainers 적용 확인
- production data 영향 기록

---

## 5. Authentication / Authorization Invariants

현재 인증은 HttpOnly cookie 기반 Access/Refresh 구조다.

금지:

- auth token을 localStorage로 이동
- 401/403 제거를 위한 route protection 약화
- Secure production cookie 임의 해제
- 인증 실패와 CORS/endpoint/config 오류 혼동

SUPER_ADMIN은 보안 민감 영역이다.

관련 변경은 반드시:

- 삭제 방지
- 강등 방지
- 권한 경계
- allowed/denied regression test

를 확인한다.

---

## 6. Database / Flyway Invariants

현재 production data는 반드시 보존한다.

규칙:

- 적용된 migration 수정 금지
- 항상 다음 forward migration 추가
- 현재 checkpoint 기준 latest migration은 V15
- 새 schema 변경 전 repository의 실제 latest migration을 다시 확인
- production DB reset 금지
- production PostgreSQL volume 삭제 금지
- docker compose down -v를 production data에 사용 금지
- Flyway 우회를 위한 manual SQL 금지
- hard delete 구현 전 FK/history 영향 확인

migration 실패 시 DB를 지우지 말고 exact Flyway/PostgreSQL error를 먼저 조사한다.

---

## 7. Scheduling / Reservation Invariants

명시적 정책 변경이 없는 한 유지:

- 원자 예약 slot = 30분
- 기본 운영시간 = 10:00~22:00
- 날짜별 운영시간/사용 불가 시간 예외 가능
- 예약 시간은 지원되는 30분 배수
- operational round와 UI calendar layout은 별개
- 예약 무결성에 필요한 transaction/lock 유지
- admin 기능도 무결성 제약을 임의 bypass하지 않음
- 과거 참조를 깨는 destructive delete 금지

현재 구현에는 stage/song/round 단위 최대 예약 횟수 정책이 있으며
관리 범위는 1~99다.

이 정책을 단순 Boolean 복수예약 정책으로 되돌리지 않는다.

---

## 8. Song / Historical Data Rules

현재 구현은 archived song을 무조건 영구 보존하지 않는다.

reservation reference가 삭제를 막지 않는 경우에 한해
보관 곡 hard delete가 가능하도록 구현되어 있다.

정책 문서의 오래된 "곡은 물리 삭제하지 않는다" 문구와 충돌하므로
관련 작업 시 CURRENT_STATE의 known drift를 확인하고
임의로 예전 정책으로 되돌리지 않는다.

회원 삭제는 별도 정책에 따라 soft delete/익명화 구조를 유지한다.

---

## 9. Frontend / UX Invariants

- mobile PWA는 first-class target
- admin/schedule 화면 horizontal overflow 방지
- custom monthly calendar는 Sunday-first
- Sunday/Saturday 시각 구분 유지
- native browser/OS picker는 app이 완전한 styling을 제어할 수 있다고 가정하지 않음
- custom control 도입 시 keyboard/accessibility 고려
- user-visible UI 변경은 mobile width에서 수동 확인 when practical

production은 same-origin API/WebSocket 구조를 전제로 한다.
배포 architecture 변경 없이 frontend/backend origin을 분리하지 않는다.

---

## 10. Deployment Rules

운영 서비스 도메인:

~~~text
https://bandmuhon.cloud
~~~

저장소에는 Railway deployment 문서가 존재하지만,
실제 운영 배포는 서버 관리자에게 source archive를 전달하는 방식도 사용한다.

따라서 deployment 관련 작업 전 실제 운영 경로를 먼저 확인한다.

서버 관리자 전달용 archive 예:

~~~powershell
git switch main
git pull --ff-only origin main
$desktop = [Environment]::GetFolderPath("Desktop")
git archive --format=zip --output="$desktop\rehearsal-pwa-server.zip" main
~~~

배포 시 보존:

- PostgreSQL data
- persistent volume
- environment variables
- domain/routing
- secret values
- existing production configuration

배포 후 확인:

1. intended revision
2. startup log
3. Flyway success
4. /health
5. login/auth cookie
6. 핵심 reservation flow
7. WebSocket/realtime when affected
8. PWA/service worker when affected
9. 외부 domain에서 실제 동작

실서비스 확인 전 "배포 완료"라고 단정하지 않는다.

---

## 11. CURRENT_STATE Policy

docs/CURRENT_STATE.md는 다음 시점에 갱신한다.

- meaningful implementation 완료 후
- 중단/인계 전
- PR 생성 전
- blocker 발생 시
- architecture/security/database/deployment 결정 후
- 다른 AI agent로 넘기기 전

trivial typo만 고친 경우에는 불필요하게 갱신하지 않아도 된다.

CURRENT_STATE에는 사실과 검증 결과만 기록한다.
secret은 기록하지 않는다.

---

## 12. Known Documentation Drift Policy

이번 기능과 직접 관련 없는 기존 drift를 기능 PR에 섞어서 전부 수정하지 않는다.

현재 checkpoint에서 알려진 drift 예:

- POLICY의 곡 물리 삭제 금지 문구 vs archived-song conditional hard delete 구현
- Boolean 중심 복수 예약 설명 vs 현재 1~99 최대 예약 횟수 구현
- Railway 문서의 Flyway V12 언급 vs 현재 V15
- Railway 중심 문서 vs 실제 server-admin archive handoff 가능성

관련 기능을 수정할 때는 먼저 drift를 보고하고,
별도 docs-sync 범위가 승인되면 문서를 동기화한다.

---

## 13. 무혼 프로젝트 금지 사항

사용자 명시 승인 없이 금지:

- main 직접 feature commit
- merged feature branch 재사용
- production DB reset
- production volume 삭제
- 적용된 Flyway migration 수정
- auth token localStorage 저장
- SUPER_ADMIN 보호 약화
- same-origin deployment 구조 변경
- unrelated dependency upgrade
- unrelated policy rewrite
- 기존 사용자 작업 삭제
- 테스트하지 않은 상태를 "완료"라고 보고
- GitHub main과 실제 production artifact가 동일하다고 가정
