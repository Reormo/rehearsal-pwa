# AGENTS.md

> 공통 AI 개발 하네스 규칙
>
> 이 문서는 사람이 목표·정책·제약·완료 조건을 정하고,
> AI 코딩 에이전트가 저장소의 실제 상태를 확인한 뒤
> 구현·검증·보고를 반복하도록 만드는 기본 운영 규칙이다.
>
> 프로젝트별 최신 human-approved 정책/명세가 이 공통 규칙과 충돌하면
> 프로젝트 규칙을 우선하되, 충돌 사실과 영향 범위를 먼저 보고한다.

---

## 1. 핵심 원칙

AI는 임의로 코드를 생성하는 도구가 아니라 정해진 목표·제약·검증 절차 안에서 작업하는 실행자다.

항상 다음 흐름을 따른다.

1. 현재 상태 확인
2. 목표와 범위 확인
3. 관련 정책·문서·코드 확인
4. 영향 범위 분석
5. 최소 범위 구현
6. 자동 검증
7. diff 검토
8. 필요한 수정 및 재검증
9. Git 반영
10. 상태/문서 갱신
11. 배포가 포함되면 실제 배포 상태 확인

확인할 수 있는 것은 추측하지 않는다.

---

## 2. 작업 모드

한 작업 안에서 모드를 임의로 섞지 않는다.
모드를 바꿔야 하면 이유를 보고한다.

### 2.1 PATCH 모드 — 일반 ChatGPT

일반 ChatGPT는 기본적으로 개발자의 로컬 저장소를 직접 수정하지 않는다.

흐름:

1. AGENTS.md 확인
2. CURRENT_STATE 문서가 있으면 확인
3. GitHub/파일/로그/스크린샷/코드 상태 확인
4. 원인 및 영향 범위 분석
5. patch 기준 branch와 exact base commit SHA 확인
6. unified Git patch 생성
7. 개발자가 로컬에서 적용
8. IDE에서 diff 검토
9. 테스트/lint/build 실행
10. 필요하면 follow-up patch 생성
11. 검증 완료 후 commit/push/PR

적용 예시:

~~~powershell
git apply --check .\change.patch
git apply .\change.patch
~~~

PATCH 모드 규칙:

- repository-relative path 사용
- proper unified Git patch 사용
- 새 파일은 new file mode 포함
- unrelated local change 보존
- secret 포함 금지
- 동작 변경 시 테스트 포함
- 한 patch는 하나의 명확한 목적에 집중
- Set-Content 같은 whole-file rewrite를 기본 수단으로 사용하지 않음
- line-ending-only 변경 방지
- patch 전달 시 base branch와 base commit SHA를 함께 보고

GitHub 원격 변경은 사용자가 명시적으로 요청한 경우에만 수행한다.

### 2.2 DIRECT 모드 — Codex / Local Coding Agent

로컬 저장소에 연결된 coding agent는 직접 수정할 수 있다.

수정 전 최소 확인:

~~~bash
git branch --show-current
git status
git log --oneline -n 10
~~~

그 후:

- 프로젝트 기본 branch 최신화
- focused branch 사용
- 관련 파일만 수정
- targeted test 먼저 실행
- broader verification 실행
- git diff 검토
- 상태 문서 갱신
- PR 전 검증 결과 기록

---

## 3. Source of Truth

### 3.1 현재 구현 상태

"지금 실제로 무엇이 구현되어 있는가"는 다음을 직접 확인한다.

- 현재 branch
- git status
- 최근 commit
- 실제 source code
- 설정 파일
- DB migration
- 테스트
- CI
- 실제 배포 artifact/revision
- 실제 서비스 동작

문서에 기능이 적혀 있다는 이유만으로 구현 완료라고 판단하지 않는다.

### 3.2 목표 동작

"어떻게 동작해야 하는가"는 human-approved 정책/명세를 기준으로 한다.

예:

- 요구사항
- 사용자 흐름
- 정책 문서
- ERD
- API 명세
- 디자인
- architecture 문서
- 결정 기록
- CURRENT_STATE의 최근 승인 결정

코드가 문서와 다르다는 이유만으로 코드가 새로운 정책이라고 판단하지 않는다.

### 3.3 충돌 처리

문서·코드·CURRENT_STATE가 충돌하면 임의로 한쪽을 정답으로 가정하지 않는다.

다음 형식으로 정리한다.

~~~text
[Source of Truth 충돌]

문서:
- ...

현재 코드:
- ...

최근 승인 결정:
- ...

차이:
- ...

이번 작업에서 따라야 할 기준:
- ...
~~~

기준이 확정되어 있으면 따른다.
정책 결정이 새로 필요하면 관련 없는 구현을 먼저 진행하지 않는다.

---

## 4. 작업 시작 절차

### 4.1 Git 상태

~~~bash
git branch --show-current
git status
git log --oneline -5
git fetch
~~~

### 4.2 기본 branch

프로젝트별 rules에 기본 branch가 있으면 그것을 사용한다.
없으면 원격 default branch를 확인한다.
main이라고 임의 가정하지 않는다.

일반 흐름:

~~~bash
git switch <base>
git pull --ff-only origin <base>
git switch -c <type>/<short-description>
~~~

이미 진행 중인 branch라면 새 branch를 만들기 전에 상태를 확인한다.

### 4.3 기존 변경 보호

사용자 작업을 임의 삭제하거나 덮어쓰지 않는다.

사용자 승인 없이 다음을 실행하지 않는다.

~~~bash
git reset --hard
git clean -fd
git checkout -- .
git restore .
~~~

---

## 5. 요청 해석

구현 전에 내부적으로 다음을 정리한다.

~~~text
목표:
현재 상태:
작업 범위:
제외 범위:
관련 정책:
영향 파일:
완료 조건:
검증 방법:
~~~

요청이 명확하면 불필요한 재질문 없이 진행한다.
새 정책 결정이 필요한 부분만 별도로 사용자에게 제시한다.

---

## 6. 구현 원칙

### 6.1 최소 변경

금지:

- unrelated refactor
- 대규모 formatting
- 임의 폴더 구조 변경
- 필요 없는 dependency 추가
- API 계약의 무단 변경
- 이름만 바꾸는 광범위한 변경
- 요청하지 않은 architecture 재설계

### 6.2 기존 패턴 우선

새 코드를 만들기 전에 다음을 찾는다.

- 같은 계층의 기존 구현
- DTO
- ErrorCode
- 응답 포맷
- 인증/인가 방식
- 테스트 패턴
- 설정 방식
- DB naming convention

### 6.3 계층 책임

프로젝트 architecture를 우선한다.

별도 규칙이 없으면 최소한:

- Controller: 요청/응답/입력 검증
- Service/Application: 비즈니스 로직
- Repository: 데이터 접근
- Entity/Domain: 도메인 상태/규칙
- DTO: 외부 계약
- Config/Security: 공통 설정/보안

Entity를 API 응답으로 직접 반환하지 않는다.

### 6.4 API 계약 보호

기존 API 변경 전 확인:

- HTTP method
- path
- request
- response
- status code
- error code
- cookie/header
- 인증/인가
- nullable
- field name

프론트가 사용하는 계약을 근거 없이 변경하지 않는다.

---

## 7. 데이터베이스

DB migration 도구가 정해져 있으면 반드시 사용한다.

### 7.1 적용된 migration

공유/운영 환경에 적용되었거나 적용 가능성이 있는 migration은 수정하지 않는다.
변경은 새 forward migration으로 추가한다.

### 7.2 파괴적 작업

명시적 승인과 복구 계획 없이 금지:

- 운영 DB 삭제
- 운영 volume 삭제
- schema 전체 drop
- 데이터 전체 초기화
- migration history 강제 조작
- migration 우회를 위한 임의 SQL

### 7.3 스키마/코드 동기화

함께 확인:

- type
- nullable
- default
- unique
- index
- foreign key
- enum 저장 방식
- 기존 데이터 migration

---

## 8. Git 규칙

기본 흐름:

~~~text
base 최신화
→ 작업 branch
→ 구현
→ 테스트
→ diff 검토
→ 명시적 stage
→ commit
→ push
→ PR
→ CI/review
→ merge
~~~

규칙:

- 기본 branch 직접 feature 개발 금지
- 가능하면 git add . 보다 파일 단위 stage
- commit은 논리 단위
- 검증 전 완료 선언 금지
- 기본 branch force-push 금지
- history rewrite가 필요하면 feature branch에서만 이유를 설명하고 --force-with-lease 사용

commit 예시:

~~~text
feat: OAuth 로그인 추가
fix: 예약 충돌 검증 수정
chore: 환경변수 구성 정리
docs: API 계약 갱신
test: 통합 테스트 추가
~~~

---

## 9. 테스트와 검증

설명보다 검증 결과를 우선한다.

권장 순서:

1. 변경 대상 정적 확인
2. 관련 unit test
3. 관련 integration test
4. 전체 test
5. lint/format
6. build/package
7. 필요 시 실제 실행
8. API/UI/DB 동작 확인
9. git diff 최종 검토
10. git diff --check

정확한 명령은 Project Specific Rules에 기록한다.

정확한 명령이 없으면 build file/package script/CI를 확인하고
실제로 존재하는 명령만 사용한다.
추측한 명령을 "프로젝트 공식 명령"이라고 부르지 않는다.

CI가 구성되어 있으면 merge 전 확인한다.
CI가 없으면 "CI 통과"라고 말하지 않고 실제 local verification만 보고한다.

### 실패 시

구분한다.

- 이번 변경으로 새로 발생했는가
- 기존 실패인가
- 환경 문제인가
- 테스트 자체 문제인가

실패를 숨기지 않는다.

---

## 10. AI 검증 루프

~~~text
Inspect
→ Plan
→ Implement
→ Test
→ Review Diff
→ Fix
→ Re-test
→ Report
~~~

구현 후 스스로 확인:

- 요구사항을 모두 충족했는가
- 범위를 넘었는가
- 기존 동작을 깨뜨렸는가
- 테스트가 실제 변경을 검증하는가
- 보안 문제가 생겼는가
- 문서/코드 drift가 새로 생겼는가

---

## 11. 디버깅

문제 발생 시 바로 코드를 바꾸지 않는다.

순서:

1. 증상 재현
2. 정확한 error/request/response 확보
3. 로그 확인
4. 관련 코드 확인
5. 설정 확인
6. 배포 revision 확인
7. 원인 후보 제거
8. root cause 확인
9. 최소 수정
10. regression test
11. broader verification

401/403/500을 없애기 위해 security를 약화하지 않는다.

GitHub 최신 코드와 실행 중인 배포 코드를 동일하다고 가정하지 않는다.

---

## 12. 보안

금지:

- password commit
- API key commit
- OAuth secret commit
- DB password commit
- JWT secret commit
- private key commit
- 운영 access token commit
- secret 전체값을 chat/log에 출력

환경변수 또는 프로젝트가 정한 secret 관리 방식을 사용한다.

secret 확인은 값이 아니라 SET/NOT_SET 정도만 출력한다.

인증/인가 방식은 명시적 정책 변경 없이 바꾸지 않는다.

---

## 13. 배포 안전

로컬 build 성공은 배포 성공이 아니다.

배포 변경 전 확인:

- 대상 branch/commit
- build artifact
- 환경변수 존재
- prod profile
- DB 연결
- migration
- domain/DNS
- HTTPS
- CORS
- OAuth redirect
- cookie
- reverse proxy

실제 배포 검증:

- startup log
- health endpoint
- 핵심 API
- 로그인/인증
- 주요 사용자 흐름
- 실제 외부 URL 동작

배포 architecture 변경은 별도 승인 없이 side effect로 수행하지 않는다.

예:

- cloud/provider 변경
- reverse proxy 교체
- port 변경
- Docker 도입/제거
- DB 교체
- frontend/backend origin topology 변경
- cookie strategy 변경
- persistent volume 구조 변경

---

## 14. 문서 동기화

이번 변경으로 직접 달라진 계약·구조·운영 방법은 관련 문서에 반영한다.

단, 이번 작업 이전부터 존재하던 unrelated documentation drift를
기능 작업에 섞어서 대규모로 고치지 않는다.

기존 drift는:

1. 정확한 차이 기록
2. CURRENT_STATE 또는 issue/별도 docs task에 남김
3. 별도 범위로 정리

---

## 15. CURRENT_STATE

장기 프로젝트나 agent handoff가 있는 프로젝트는 docs/CURRENT_STATE.md 사용을 권장한다.

Project Specific Rules에서 필수로 지정할 수 있다.

기록 항목 예:

~~~text
## Current task
## Current branch
## Base commit
## Completed
## Important decisions
## Changed files
## Verification
## Known issues / drift
## Remaining work
## Blockers
~~~

목적은 다음 세션이 이전 채팅 없이 저장소만 보고 이어서 작업하게 만드는 것이다.

secret은 기록하지 않는다.

---

## 16. AI 보고 규칙

작업 보고 시:

- 무엇을 변경했는지
- 무엇을 실제 검증했는지
- 무엇을 검증하지 못했는지
- fact와 assumption 구분
- 실패한 시도 숨기지 않기
- 사람이 해야 할 다음 명령 정확히 제공

테스트를 실행하지 않았으면 "통과"라고 하지 않는다.
실서비스를 확인하지 않았으면 "배포 완료"라고 하지 않는다.

---

## 17. Definition of Done

완료 전 확인:

- [ ] 현재 Git 상태 확인
- [ ] 올바른 base/branch 사용
- [ ] 목표 구현
- [ ] 범위 외 변경 없음
- [ ] 정책/API 계약 준수
- [ ] 필요한 migration 포함
- [ ] 관련 테스트 통과
- [ ] 필요한 broader test/lint/build 통과
- [ ] git diff 검토
- [ ] git diff --check 통과
- [ ] secret 없음
- [ ] 직접 영향받는 문서 동기화
- [ ] CURRENT_STATE 갱신(프로젝트에서 요구 시)
- [ ] PR/CI 검토(해당 시)
- [ ] 배포 실검증(배포 작업인 경우)
- [ ] 남은 문제 명시

확인하지 못한 항목은 완료라고 단정하지 않는다.

보고 형식:

~~~text
완료:
- ...

확인 완료:
- ...

미확인:
- ...

남은 작업:
- ...
~~~

---

## 18. 새 프로젝트 시작 권장 흐름

~~~text
문제 정의
↓
사용자 시나리오
↓
User Flow / UX
↓
기능 및 정책 명세
↓
AI Harness / 개발 규칙
↓
UI / Design
↓
Architecture / ERD / API
↓
Frontend / Backend 구현
↓
자동 테스트 및 검증
↓
Frontend ↔ Backend 연동
↓
배포
↓
실사용 검증
↓
운영 / 개선
~~~

항상 완전히 순차적일 필요는 없다.
목표는 정책/계약이 정해지기 전에 대규모 구현부터 시작하지 않도록 하는 것이다.

---

## 19. Project Specific Rules 작성 규칙

공통 규칙 아래에 프로젝트 차이만 추가한다.

최소 권장 항목:

~~~text
# Project Specific Rules

## Repository / Base Branch
## Stack
## Source of Truth Documents
## Exact Commands
## Architecture Invariants
## Authentication / Authorization
## Database / Migration
## Domain Invariants
## Frontend / UX
## Deployment
## CURRENT_STATE Policy
## Prohibited Changes
~~~

공통 규칙을 프로젝트마다 새로 해석하지 않는다.
프로젝트에 필요한 구체적 값과 예외만 적는다.

---

## 핵심 원칙 한 줄

> 사람은 목표·정책·제약·완료 조건을 설계하고,
> AI는 저장소 안의 실제 상태를 확인한 뒤 구현·검증 루프를 수행한다.


---

# Project Specific Rules — 무혼 합주 예약 PWA

## 20. Repository / Base Branch

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

## 21. Source of Truth Documents

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

## 22. Stack

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

## 23. Exact Verification Commands

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

## 24. Authentication / Authorization Invariants

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

## 25. Database / Flyway Invariants

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

## 26. Scheduling / Reservation Invariants

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

## 27. Song / Historical Data Rules

현재 구현은 archived song을 무조건 영구 보존하지 않는다.

reservation reference가 삭제를 막지 않는 경우에 한해
보관 곡 hard delete가 가능하도록 구현되어 있다.

정책 문서의 오래된 "곡은 물리 삭제하지 않는다" 문구와 충돌하므로
관련 작업 시 CURRENT_STATE의 known drift를 확인하고
임의로 예전 정책으로 되돌리지 않는다.

회원 삭제는 별도 정책에 따라 soft delete/익명화 구조를 유지한다.

---

## 28. Frontend / UX Invariants

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

## 29. Deployment Rules

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

## 30. CURRENT_STATE Policy

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

## 31. Known Documentation Drift Policy

이번 기능과 직접 관련 없는 기존 drift를 기능 PR에 섞어서 전부 수정하지 않는다.

현재 checkpoint에서 알려진 drift 예:

- POLICY의 곡 물리 삭제 금지 문구 vs archived-song conditional hard delete 구현
- Boolean 중심 복수 예약 설명 vs 현재 1~99 최대 예약 횟수 구현
- Railway 문서의 Flyway V12 언급 vs 현재 V15
- Railway 중심 문서 vs 실제 server-admin archive handoff 가능성

관련 기능을 수정할 때는 먼저 drift를 보고하고,
별도 docs-sync 범위가 승인되면 문서를 동기화한다.

---

## 32. 무혼 프로젝트 금지 사항

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
