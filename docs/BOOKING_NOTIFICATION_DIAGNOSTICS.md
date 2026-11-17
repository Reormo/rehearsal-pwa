# 예약 오픈 알림: 기본/무대별 별도 설정 진단

관찰된 문제: **버스킹 별도 예약 오픈 설정**이 있었는데 별도 시각 알림이 누락되고
회차 기본 시각 알림으로 간 것으로 보임. 현재 원인을 확정하지 않았다.

정상 규칙: 축제(기본 설정)는 기본 시각, 버스킹(별도 설정)는 별도 시각에
각각 10분 전과 오픈 알림을 보낸다. 각 무대 종류의 활성 곡 팀장만 수신한다.
양쪽 팀장을 겸임한 회원은 두 종류의 알림을 받을 수 있다.

운영 PostgreSQL 관리자에게 아래 **읽기 전용 SQL만** 실행하도록 요청한다.
실제 예약 대상 주간에 속하는 날짜로 `2026-11-09`를 바꾼다.
DB 접속 비밀번호, JWT/VAPID 키, Push 구독 정보는 전달하지 않는다.

## 1. 버스킹 별도 설정이 **해당 회차에 저장됐는지** 확인

```sql
select
 br.id as round_id, br.start_date, br.end_date,
 st.id as stage_type_id, st.name as stage_type,
 case when w.id is null then 'DEFAULT' else 'CUSTOM' end as policy,
 br.booking_open_at at time zone 'Asia/Seoul' as default_open_kst,
 w.booking_open_at at time zone 'Asia/Seoul' as custom_open_kst,
 coalesce(w.booking_open_at, br.booking_open_at)
   at time zone 'Asia/Seoul' as effective_open_kst,
 w.updated_at at time zone 'Asia/Seoul' as override_saved_kst,
 count(distinct s.id) as active_songs,
 count(distinct sm.user_id) as stage_leaders
from booking_rounds br
join stage_types st on st.club_id = br.club_id
left join booking_round_stage_windows w
 on w.booking_round_id = br.id and w.stage_type_id = st.id
left join songs s
 on s.club_id = br.club_id and s.stage_type_id = st.id and s.status = 'ACTIVE'
left join song_members sm on sm.song_id = s.id and sm.is_leader = true
where date '2026-11-09' between br.start_date and br.end_date
 and st.name in ('축제', '버스킹')
group by br.id, br.start_date, br.end_date, br.booking_open_at,
 st.id, st.name, w.id, w.booking_open_at, w.updated_at
order by br.id, st.name;
```

- 버스킹 `DEFAULT`: 해당 회차에는 별도 설정이 없다. 다른 회차에 저장했는지,
  별도 설정 저장 실패나 무대 종류 ID 불일치가 있었는지 확인해야 한다.
- 버스킹 `CUSTOM`: `effective_open_kst`를 발송 알림의 기준 시각과 비교한다.
- `stage_leaders=0`: 버스킹 무대에 활성 곡 팀장 수신자가 없다.
- `override_saved_kst`가 잘못된 기본 알림의 생성 시각 **이후**라면
  이전 설정을 기준으로 이미 발송됐을 가능성이 있다.

## 2. 실제로 생성된 알림의 무대 종류와 오픈 시각

```sql
select
 n.type, st.name as stage_type,
 to_timestamp(split_part(n.dedupe_key, ':', 4)::bigint)
   at time zone 'Asia/Seoul' as notification_open_kst,
 min(n.created_at at time zone 'Asia/Seoul') as first_created_kst,
 max(n.created_at at time zone 'Asia/Seoul') as last_created_kst,
 count(distinct n.user_id) as recipients
from notifications n
join booking_rounds br on br.id = split_part(n.dedupe_key, ':', 2)::bigint
join stage_types st on st.id = split_part(n.dedupe_key, ':', 3)::bigint
where date '2026-11-09' between br.start_date and br.end_date
 and n.type in ('BOOKING_OPEN_10_MIN','BOOKING_OPEN')
 and (n.dedupe_key like 'booking-open:%'
      or n.dedupe_key like 'booking-open-10:%')
 and st.name in ('축제', '버스킹')
group by n.type, st.name, split_part(n.dedupe_key, ':', 4)
order by notification_open_kst, stage_type, n.type;
```

- 버스킹 알림이 **기본 시각**을 가리키면, 잘못된 시각으로 생성된 알림
  이력이 확인되는 것이다. 설정 저장 시각과 실제 배포 백엔드 버전을 비교한다.
- 버스킹 알림이 **별도 시각**으로 저장됐는데 휴대폰에만 안 떴다면
  Web Push 전송 및 기기 구독/전달 경로를 확인한다.
- 버스킹 알림 자체가 없다면 수신 자격, 스케줄러 실행/오류 로그를 확인한다.

조회용 SQL은 운영 DB 데이터를 변경하지 않는다. 개인정보와 비밀키는 공유하지 않는다.

## 3. 변경 후 로컬 검증

```powershell
cd C:\dev\rehearsal-pwa\backend
.\gradlew.bat test --tests "com.bandclub.rehearsal.notification.NotificationScheduleIntegrationTests"
```

실제 관리자 저장 서비스와 알림 발송 서비스의 연결을 검증하는 테스트다.
테스트 통과는 운영에서 발생한 누락의 원인 규명을 대신하지 않는다.
