package com.bandclub.rehearsal.mt;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.domain.ClubRole;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.util.*;

@RestController
@RequestMapping("/api/mt")
public class MtController {
    private static final List<String> DEFAULT_QUESTIONS = List.of(
        "내가 그린 기린 그림은 네가 그린 기린 그림보다 긴 기린 그림이다.",
        "경찰청 철창살은 외철창살이고 검찰청 철창살은 쌍철창살이다.",
        "저기 있는 저분은 박 법학박사이고 여기 있는 이분은 백 법학박사이다.",
        "간장 공장 공장장은 강 공장장이고 된장 공장 공장장은 장 공장장이다.",
        "신진 샹송 가수의 신춘 샹송 쇼는 새봄에 시작한다."
    );
    private final JdbcTemplate db;
    private final MembershipService membership;

    public MtController(JdbcTemplate db, MembershipService membership) {
        this.db = db;
        this.membership = membership;
    }

    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    private long club(Jwt jwt) { return membership.requireMembership(user(jwt)).getClubId(); }
    private long superAdminClub(Jwt jwt) {
        return membership.requireSuperAdmin(user(jwt)).getClubId();
    }
    private boolean isMtHost(ClubMember member) {
        if (member.getRole() == ClubRole.SUPER_ADMIN) return true;
        return first("SELECT user_id FROM mt_host_managers WHERE club_id=? AND user_id=?",
            member.getClubId(),member.getUserId()) != null;
    }
    private long hostClub(Jwt jwt) {
        var member = membership.requireMembership(user(jwt));
        if (!isMtHost(member))
            throw new AppException(HttpStatus.FORBIDDEN,"MT_HOST_REQUIRED","MT 관리자 권한이 필요합니다.");
        return member.getClubId();
    }
    private static long number(Object n) { return ((Number)n).longValue(); }
    private static boolean yes(Object b) { return Boolean.TRUE.equals(b); }
    private static AppException missing(String message) {
        return new AppException(HttpStatus.NOT_FOUND, "MT_NOT_FOUND", message);
    }
    private static AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, "MT_CONFLICT", message);
    }
    private static AppException bad(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "MT_INVALID", message);
    }
    private static String clean(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }
    private List<Map<String,Object>> rows(String sql, Object... args) {
        return db.queryForList(sql, args);
    }
    private Map<String,Object> first(String sql, Object... args) {
        var result = rows(sql, args);
        return result.isEmpty() ? null : result.get(0);
    }
    private Map<String,Object> event(long clubId) {
        return first("SELECT * FROM mt_events WHERE club_id=? AND ended_at IS NULL", clubId);
    }
    private Map<String,Object> lockEvent(long clubId) {
        var result = first("SELECT * FROM mt_events WHERE club_id=? AND ended_at IS NULL FOR UPDATE", clubId);
        if (result == null) throw missing("활성화된 MT 행사가 없습니다.");
        return result;
    }
    private long eventId(Map<String,Object> e) { return number(e.get("id")); }
    private Map<String,Object> requireTeam(long eventId, long teamId) {
        var team = first("SELECT * FROM mt_teams WHERE event_id=? AND id=? AND active=true", eventId, teamId);
        if (team == null) throw missing("해당 조가 없습니다.");
        return team;
    }
    private Map<String,Object> requireQuestion(long eventId, long id) {
        var q = first("SELECT * FROM mt_questions WHERE event_id=? AND id=? AND active=true", eventId, id);
        if (q == null) throw missing("해당 문제가 없습니다.");
        return q;
    }
    private void noRunningGames(Map<String,Object> e) {
        if (yes(e.get("buzzer_open")) || yes(e.get("typing_open")))
            throw conflict("진행 중인 게임을 먼저 종료해 주세요.");
    }
    private Map<String,Object> myTeam(long eventId, long userId) {
        return first("SELECT t.id, t.name FROM mt_team_members tm JOIN mt_teams t ON t.id=tm.team_id AND t.active=true WHERE tm.event_id=? AND tm.user_id=?",
            eventId, userId);
    }

    @GetMapping("/state")
    @Transactional(readOnly=true)
    public Map<String,Object> state(@AuthenticationPrincipal Jwt jwt) {
        var member = membership.requireMembership(user(jwt));
        var e = event(member.getClubId());
        boolean host = isMtHost(member);
        boolean superAdmin = member.getRole() == ClubRole.SUPER_ADMIN;
        if (e == null) return Map.of("exists",false,"buzzerVisible",false,"typingVisible",false,"host",host,"superAdmin",superAdmin);
        long eid = eventId(e);
        var team = myTeam(eid, user(jwt));
        var out = new LinkedHashMap<String,Object>();
        out.put("exists",true);
        out.put("eventTitle",e.get("title"));
        out.put("buzzerVisible",e.get("buzzer_enabled"));
        out.put("typingVisible",e.get("typing_enabled"));
        out.put("host",host);
        out.put("superAdmin",superAdmin);
        out.put("team",team);
        return out;
    }

    // MT permissions are independent of rehearsal ADMIN roles.
    @GetMapping("/host/managers")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mtManagers(@AuthenticationPrincipal Jwt jwt) {
        long cid=superAdminClub(jwt);
        return rows("SELECT u.id,u.name,cm.role," +
            "(cm.role='SUPER_ADMIN' OR m.user_id IS NOT NULL) AS assigned," +
            "(cm.role='SUPER_ADMIN') AS permanent " +
            "FROM club_members cm JOIN users u ON u.id=cm.user_id " +
            "LEFT JOIN mt_host_managers m ON m.club_id=cm.club_id AND m.user_id=cm.user_id " +
            "WHERE cm.club_id=? AND u.status='ACTIVE' AND u.deleted_at IS NULL " +
            "ORDER BY (cm.role='SUPER_ADMIN') DESC,(m.user_id IS NOT NULL) DESC,u.name,u.id",cid);
    }

    @PostMapping("/host/managers/{userId}")
    @Transactional
    public void grantMtHost(@AuthenticationPrincipal Jwt jwt,@PathVariable long userId) {
        long cid=superAdminClub(jwt);
        var target=first("SELECT cm.role FROM club_members cm JOIN users u ON u.id=cm.user_id " +
            "WHERE cm.club_id=? AND cm.user_id=? AND u.status='ACTIVE' AND u.deleted_at IS NULL",cid,userId);
        if(target==null)throw missing("동아리 회원을 찾을 수 없습니다.");
        if("SUPER_ADMIN".equals(target.get("role")))throw bad("슈퍼관리자는 기본 MT 관리자입니다.");
        db.update("INSERT INTO mt_host_managers(club_id,user_id,granted_by) VALUES (?,?,?) " +
            "ON CONFLICT(club_id,user_id) DO NOTHING",cid,userId,user(jwt));
    }

    @DeleteMapping("/host/managers/{userId}")
    @Transactional
    public void revokeMtHost(@AuthenticationPrincipal Jwt jwt,@PathVariable long userId) {
        long cid=superAdminClub(jwt);
        db.update("DELETE FROM mt_host_managers WHERE club_id=? AND user_id=?",cid,userId);
    }

    @GetMapping("/host")
    @Transactional(readOnly=true)
    public Map<String,Object> host(@AuthenticationPrincipal Jwt jwt) {
        var e=event(hostClub(jwt));
        if(e==null) return Map.of("exists",false);
        long id=eventId(e);
        var out=new LinkedHashMap<String,Object>();
        out.put("exists",true);
        out.put("eventTitle",e.get("title"));
        out.put("buzzerVisible",e.get("buzzer_enabled"));
        out.put("typingVisible",e.get("typing_enabled"));
        out.put("buzzerOpen",e.get("buzzer_open"));
        out.put("buzzerPaused",e.get("buzzer_paused"));
        out.put("winnerTeamId",e.get("buzzer_winner_team_id"));
        var winner=e.get("buzzer_winner_team_id")==null?null:first("SELECT name FROM mt_teams WHERE id=? AND event_id=?",e.get("buzzer_winner_team_id"),id);
        out.put("winnerTeamName",winner==null?null:winner.get("name"));
        out.put("excludedTeamIds",rows("SELECT team_id FROM mt_buzzer_exclusions WHERE event_id=? ORDER BY team_id",id)
            .stream().map(r->r.get("team_id")).toList());
        out.put("typingOpen",e.get("typing_open"));
        out.put("currentRoundId",e.get("current_round_id"));
        return out;
    }

    @PostMapping("/host/events")
    @Transactional
    public Map<String,Object> createEvent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TitleRequest input) {
        long cid=hostClub(jwt);
        if(event(cid)!=null) throw conflict("진행 중인 MT 행사가 있습니다.");
        long id=db.queryForObject("INSERT INTO mt_events(club_id,title) VALUES (?,?) RETURNING id",
            Long.class,cid,input.title().trim());
        for(int n=1;n<=8;n++)
            db.update("INSERT INTO mt_teams(event_id,name) VALUES (?,?)",id,n+"조");
        for(int n=0;n<DEFAULT_QUESTIONS.size();n++)
            db.update("INSERT INTO mt_questions(event_id,sort_order,text) VALUES (?,?,?)",id,n+1,DEFAULT_QUESTIONS.get(n));
        return host(jwt);
    }

    @PostMapping("/host/events/end")
    @Transactional
    public void endEvent(@AuthenticationPrincipal Jwt jwt) {
        var e=lockEvent(hostClub(jwt));
        noRunningGames(e);
        db.update("UPDATE mt_events SET ended_at=NOW(),buzzer_enabled=false,typing_enabled=false,buzzer_paused=true WHERE id=?",eventId(e));
    }

    @GetMapping("/host/events/history")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(@AuthenticationPrincipal Jwt jwt) {
        long cid=hostClub(jwt);
        return rows("SELECT e.id,e.title,e.created_at,e.ended_at," +
            "(SELECT COUNT(*) FROM mt_teams t WHERE t.event_id=e.id) AS team_count," +
            "(SELECT COUNT(*) FROM mt_questions q WHERE q.event_id=e.id) AS question_count," +
            "(SELECT COUNT(*) FROM mt_typing_submissions s JOIN mt_typing_rounds r ON r.id=s.round_id " +
            "WHERE r.event_id=e.id) AS submission_count " +
            "FROM mt_events e WHERE e.club_id=? AND e.ended_at IS NOT NULL " +
            "ORDER BY e.ended_at DESC,e.id DESC LIMIT 50",cid);
    }

    @GetMapping("/host/events/{eventId}/history")
    @Transactional(readOnly=true)
    public Map<String,Object> historyDetail(@AuthenticationPrincipal Jwt jwt,@PathVariable long eventId) {
        long cid=hostClub(jwt);
        var e=first("SELECT id,title,ended_at FROM mt_events " +
            "WHERE id=? AND club_id=? AND ended_at IS NOT NULL",eventId,cid);
        if(e==null)throw missing("지난 MT 기록이 없습니다.");
        var out=new LinkedHashMap<String,Object>();
        out.put("id",e.get("id"));
        out.put("title",e.get("title"));
        out.put("endedAt",e.get("ended_at"));
        out.put("teams",rows("SELECT t.id,t.name," +
            "(SELECT COUNT(*) FROM mt_team_members tm WHERE tm.team_id=t.id) AS member_count " +
            "FROM mt_teams t WHERE t.event_id=? ORDER BY t.id",eventId));
        out.put("questions",rows("SELECT id,text FROM mt_questions WHERE event_id=? ORDER BY sort_order,id",eventId));
        out.put("rounds",rows("SELECT id,answer_snapshot,started_at,ended_at " +
            "FROM mt_typing_rounds WHERE event_id=? ORDER BY id",eventId));
        out.put("submissions",rows("SELECT s.id,s.user_name,s.team_name,s.content,s.submitted_at " +
            "FROM mt_typing_submissions s JOIN mt_typing_rounds r ON r.id=s.round_id " +
            "WHERE r.event_id=? ORDER BY s.id",eventId));
        return out;
    }

    @PostMapping("/host/events/{eventId}/restore")
    @Transactional
    public void restoreEvent(@AuthenticationPrincipal Jwt jwt,@PathVariable long eventId) {
        long cid=hostClub(jwt);
        if(event(cid)!=null)throw conflict("진행 중인 MT 행사를 먼저 종료해 주세요.");
        var archived=first("SELECT id FROM mt_events " +
            "WHERE id=? AND club_id=? AND ended_at IS NOT NULL FOR UPDATE",eventId,cid);
        if(archived==null)throw missing("복구할 MT 행사를 찾을 수 없습니다.");
        db.update("UPDATE mt_events SET ended_at=NULL,buzzer_enabled=false,typing_enabled=false," +
            "buzzer_open=false,buzzer_paused=true,buzzer_winner_team_id=NULL,typing_open=false " +
            "WHERE id=?",eventId);
        db.update("DELETE FROM mt_buzzer_exclusions WHERE event_id=?",eventId);
    }

    // Delete only an ended MT event belonging to the caller's club.
    // PostgreSQL foreign keys have NO ACTION, so children must be cleared in dependency order.
    @DeleteMapping("/host/events/{eventId}")
    @Transactional
    public void deleteArchivedEvent(@AuthenticationPrincipal Jwt jwt,@PathVariable long eventId) {
        long cid=hostClub(jwt);
        var archived=first("SELECT id FROM mt_events " +
            "WHERE id=? AND club_id=? AND ended_at IS NOT NULL FOR UPDATE",eventId,cid);
        if(archived==null)throw missing("삭제할 지난 MT 행사를 찾을 수 없습니다.");

        // The event points back to a winner team and a typing round.
        db.update("UPDATE mt_events SET buzzer_winner_team_id=NULL,current_round_id=NULL WHERE id=?",eventId);
        db.update("DELETE FROM mt_typing_submissions WHERE round_id IN " +
            "(SELECT id FROM mt_typing_rounds WHERE event_id=?)",eventId);
        db.update("DELETE FROM mt_typing_rounds WHERE event_id=?",eventId);
        db.update("DELETE FROM mt_buzzer_exclusions WHERE event_id=?",eventId);
        db.update("DELETE FROM mt_team_members WHERE event_id=?",eventId);
        db.update("DELETE FROM mt_questions WHERE event_id=?",eventId);
        db.update("DELETE FROM mt_teams WHERE event_id=?",eventId);
        db.update("DELETE FROM mt_events WHERE id=? AND club_id=? AND ended_at IS NOT NULL",eventId,cid);
    }

    @GetMapping("/host/members")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> members(@AuthenticationPrincipal Jwt jwt) {
        return rows("SELECT u.id, u.name FROM users u JOIN club_members cm ON cm.user_id=u.id WHERE cm.club_id=? AND u.status='ACTIVE' AND u.deleted_at IS NULL ORDER BY u.name,u.id",hostClub(jwt));
    }

    @GetMapping("/host/teams")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> teams(@AuthenticationPrincipal Jwt jwt) {
        var e=event(hostClub(jwt));
        if(e==null) return List.of();
        long id=eventId(e);
        var teamRows=rows("SELECT id,name FROM mt_teams WHERE event_id=? AND active=true ORDER BY id",id);
        var members=rows("SELECT tm.team_id,u.id,u.name FROM mt_team_members tm JOIN users u ON u.id=tm.user_id WHERE tm.event_id=? ORDER BY u.name",id);
        var result=new ArrayList<Map<String,Object>>();
        for(var t:teamRows){
            var copy=new LinkedHashMap<String,Object>(t);
            copy.put("members",members.stream().filter(m->number(m.get("team_id"))==number(t.get("id")))
                .map(m->Map.of("id",m.get("id"),"name",m.get("name"))).toList());
            result.add(copy);
        }
        return result;
    }

    @PostMapping("/host/teams")
    @Transactional
    public void createTeam(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody NameRequest input) {
        var e=lockEvent(hostClub(jwt)); noRunningGames(e);
        String name=input.name().trim();
        if(first("SELECT id FROM mt_teams WHERE event_id=? AND LOWER(name)=LOWER(?) AND active=true",eventId(e),name)!=null)
            throw conflict("이미 사용 중인 조 이름입니다.");
        db.update("INSERT INTO mt_teams(event_id,name) VALUES (?,?)",eventId(e),name);
    }

    @PatchMapping("/host/teams/{teamId}")
    @Transactional
    public void renameTeam(@AuthenticationPrincipal Jwt jwt,@PathVariable long teamId,@Valid @RequestBody NameRequest input) {
        var e=lockEvent(hostClub(jwt)); noRunningGames(e);
        requireTeam(eventId(e),teamId);
        String name=input.name().trim();
        if(first("SELECT id FROM mt_teams WHERE event_id=? AND LOWER(name)=LOWER(?) AND active=true AND id<>?",
            eventId(e),name,teamId)!=null) throw conflict("이미 사용 중인 조 이름입니다.");
        db.update("UPDATE mt_teams SET name=? WHERE id=?",name,teamId);
    }

    @DeleteMapping("/host/teams/{teamId}")
    @Transactional
    public void deleteTeam(@AuthenticationPrincipal Jwt jwt,@PathVariable long teamId) {
        var e=lockEvent(hostClub(jwt)); noRunningGames(e);
        requireTeam(eventId(e),teamId);
        if(e.get("buzzer_winner_team_id")!=null && number(e.get("buzzer_winner_team_id"))==teamId)
            db.update("UPDATE mt_events SET buzzer_winner_team_id=NULL WHERE id=?",eventId(e));
        db.update("DELETE FROM mt_buzzer_exclusions WHERE event_id=? AND team_id=?",eventId(e),teamId);
        db.update("DELETE FROM mt_team_members WHERE event_id=? AND team_id=?",eventId(e),teamId);
        db.update("UPDATE mt_teams SET active=false WHERE id=?",teamId);
    }

    @PutMapping("/host/teams/{teamId}/members/{userId}")
    @Transactional
    public void assign(@AuthenticationPrincipal Jwt jwt,@PathVariable long teamId,@PathVariable long userId) {
        long cid=hostClub(jwt); var e=lockEvent(cid);noRunningGames(e);
        requireTeam(eventId(e),teamId);
        if(first("SELECT u.id FROM club_members cm JOIN users u ON u.id=cm.user_id WHERE cm.club_id=? AND cm.user_id=? AND u.status='ACTIVE' AND u.deleted_at IS NULL",cid,userId)==null)
            throw bad("해당 회원을 찾을 수 없습니다.");
        db.update("INSERT INTO mt_team_members(event_id,user_id,team_id) VALUES (?,?,?) ON CONFLICT(event_id,user_id) DO UPDATE SET team_id=EXCLUDED.team_id",
            eventId(e),userId,teamId);
    }

    @DeleteMapping("/host/teams/{teamId}/members/{userId}")
    @Transactional
    public void unassign(@AuthenticationPrincipal Jwt jwt,@PathVariable long teamId,@PathVariable long userId) {
        var e=lockEvent(hostClub(jwt)); noRunningGames(e); requireTeam(eventId(e),teamId);
        db.update("DELETE FROM mt_team_members WHERE event_id=? AND team_id=? AND user_id=?",eventId(e),teamId,userId);
    }

    @GetMapping("/host/questions")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> questions(@AuthenticationPrincipal Jwt jwt) {
        var e=event(hostClub(jwt));
        return e==null?List.of():rows("SELECT id,sort_order,text FROM mt_questions WHERE event_id=? AND active=true ORDER BY sort_order,id",eventId(e));
    }

    @PostMapping("/host/questions")
    @Transactional
    public void addQuestion(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody TextRequest input) {
        var e=lockEvent(hostClub(jwt));
        Integer order=db.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM mt_questions WHERE event_id=? AND active=true",Integer.class,eventId(e));
        db.update("INSERT INTO mt_questions(event_id,sort_order,text) VALUES (?,?,?)",eventId(e),order,input.text());
    }

    private void questionNotRunning(Map<String,Object> e,long questionId) {
        if(yes(e.get("typing_open")) && e.get("current_round_id")!=null &&
            first("SELECT id FROM mt_typing_rounds WHERE id=? AND event_id=? AND question_id=?",
                e.get("current_round_id"),eventId(e),questionId)!=null)
            throw conflict("진행 중인 문제는 수정할 수 없어요.");
    }

    @PatchMapping("/host/questions/{questionId}")
    @Transactional
    public void editQuestion(@AuthenticationPrincipal Jwt jwt,@PathVariable long questionId,@Valid @RequestBody TextRequest input) {
        var e=lockEvent(hostClub(jwt)); requireQuestion(eventId(e),questionId);questionNotRunning(e,questionId);
        db.update("UPDATE mt_questions SET text=? WHERE id=?",input.text(),questionId);
    }

    @DeleteMapping("/host/questions/{questionId}")
    @Transactional
    public void deleteQuestion(@AuthenticationPrincipal Jwt jwt,@PathVariable long questionId) {
        var e=lockEvent(hostClub(jwt));requireQuestion(eventId(e),questionId);questionNotRunning(e,questionId);
        db.update("UPDATE mt_questions SET active=false WHERE id=?",questionId);
    }

    @PostMapping("/host/questions/{questionId}/move")
    @Transactional
    public void moveQuestion(@AuthenticationPrincipal Jwt jwt,@PathVariable long questionId,@Valid @RequestBody DirectionRequest input) {
        var e=lockEvent(hostClub(jwt)); long eid=eventId(e);
        var question=requireQuestion(eid,questionId);
        int order=((Number)question.get("sort_order")).intValue();
        boolean up="UP".equals(input.direction());
        if(!up && !"DOWN".equals(input.direction())) throw bad("이동 방향을 확인해 주세요.");
        var other=first("SELECT id,sort_order FROM mt_questions WHERE event_id=? AND active=true AND sort_order "+(up?"<":">")+"? ORDER BY sort_order "+(up?"DESC":"ASC")+" LIMIT 1",eid,order);
        if(other==null)return;
        db.update("UPDATE mt_questions SET sort_order=? WHERE id=?",other.get("sort_order"),questionId);
        db.update("UPDATE mt_questions SET sort_order=? WHERE id=?",order,other.get("id"));
    }

    @PostMapping("/host/visibility")
    @Transactional
    public void visibility(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody VisibilityRequest input) {
        var e=lockEvent(hostClub(jwt));long eid=eventId(e);
        if("BUZZER".equals(input.game())) {
            db.update("UPDATE mt_events SET buzzer_enabled=?,buzzer_open=CASE WHEN ? THEN buzzer_open ELSE false END,buzzer_paused=CASE WHEN ? THEN buzzer_paused ELSE true END WHERE id=?",
                input.enabled(),input.enabled(),input.enabled(),eid);
        } else if("TYPING".equals(input.game())) {
            if(!input.enabled()&&yes(e.get("typing_open"))){
                db.update("UPDATE mt_typing_rounds SET ended_at=NOW() WHERE id=? AND ended_at IS NULL",e.get("current_round_id"));
            }
            db.update("UPDATE mt_events SET typing_enabled=?,typing_open=CASE WHEN ? THEN typing_open ELSE false END WHERE id=?",
                input.enabled(),input.enabled(),eid);
        } else throw bad("지원하지 않는 게임입니다.");
    }

    @GetMapping("/buzzer")
    @Transactional(readOnly=true)
    public Map<String,Object> buzzer(@AuthenticationPrincipal Jwt jwt) {
        var e=event(club(jwt));
        if(e==null||!yes(e.get("buzzer_enabled")))return Map.of("visible",false);
        long eid=eventId(e);
        var team=myTeam(eid,user(jwt));
        var out=new LinkedHashMap<String,Object>();
        out.put("visible",true);
        out.put("open",e.get("buzzer_open"));
        out.put("paused",e.get("buzzer_paused"));
        out.put("myTeam",team);
        out.put("winnerTeamId",e.get("buzzer_winner_team_id"));
        var w=e.get("buzzer_winner_team_id")==null?null:first("SELECT name FROM mt_teams WHERE id=? AND event_id=?",e.get("buzzer_winner_team_id"),eid);
        out.put("winnerTeamName",w==null?null:w.get("name"));
        out.put("excludedTeamIds",rows("SELECT team_id FROM mt_buzzer_exclusions WHERE event_id=?",eid).stream().map(r->r.get("team_id")).toList());
        return out;
    }

    @PostMapping("/buzzer/press")
    @Transactional
    public Map<String,Object> press(@AuthenticationPrincipal Jwt jwt) {
        var e=lockEvent(club(jwt)); long eid=eventId(e);
        if(!yes(e.get("buzzer_enabled"))) throw conflict("잠겨있는 기능입니다.");
        var team=myTeam(eid,user(jwt));
        if(team==null)throw bad("배정된 조가 없습니다.");
        long teamId=number(team.get("id"));
        boolean excluded=first("SELECT team_id FROM mt_buzzer_exclusions WHERE event_id=? AND team_id=?",eid,teamId)!=null;
        if(yes(e.get("buzzer_open"))&&!excluded&&e.get("buzzer_winner_team_id")==null){
            db.update("UPDATE mt_events SET buzzer_winner_team_id=?,buzzer_open=false WHERE id=?",teamId,eid);
            return Map.of("won",true);
        }
        return Map.of("won",false);
    }

    @PostMapping("/host/buzzer")
    @Transactional
    public void buzzerAction(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ActionRequest input) {
        var e=lockEvent(hostClub(jwt));long eid=eventId(e);
        if(!yes(e.get("buzzer_enabled")))throw conflict("버저 기능이 잠겨있습니다.");
        switch(input.action()){
            case "OPEN" -> {
                if(e.get("buzzer_winner_team_id")!=null)throw conflict("먼저 다음 조에 기회를 주세요.");
                db.update("UPDATE mt_events SET buzzer_open=true,buzzer_paused=false WHERE id=?",eid);
            }
            case "CLOSE" -> db.update("UPDATE mt_events SET buzzer_open=false,buzzer_paused=true WHERE id=?",eid);
            case "OTHER" -> {
                if(e.get("buzzer_winner_team_id")==null)throw conflict("먼저 도전한 조가 없습니다.");
                db.update("INSERT INTO mt_buzzer_exclusions(event_id,team_id) VALUES (?,?) ON CONFLICT DO NOTHING",eid,e.get("buzzer_winner_team_id"));
                db.update("UPDATE mt_events SET buzzer_winner_team_id=NULL,buzzer_open=true,buzzer_paused=false WHERE id=?",eid);
            }
            case "RETRY" -> {
                db.update("DELETE FROM mt_buzzer_exclusions WHERE event_id=?",eid);
                db.update("UPDATE mt_events SET buzzer_winner_team_id=NULL,buzzer_open=true,buzzer_paused=false WHERE id=?",eid);
            }
            case "RESET" -> {
                db.update("DELETE FROM mt_buzzer_exclusions WHERE event_id=?",eid);
                db.update("UPDATE mt_events SET buzzer_winner_team_id=NULL,buzzer_open=false,buzzer_paused=true WHERE id=?",eid);
            }
            default -> throw bad("지원하지 않는 동작입니다.");
        }
    }

    @GetMapping("/typing")
    @Transactional(readOnly=true)
    public Map<String,Object> typing(@AuthenticationPrincipal Jwt jwt) {
        var e=event(club(jwt));
        if(e==null||!yes(e.get("typing_enabled")))return Map.of("visible",false);
        long eid=eventId(e),uid=user(jwt);
        var team=myTeam(eid,uid);
        var out=new LinkedHashMap<String,Object>();
        out.put("visible",true);
        out.put("open",e.get("typing_open"));
        out.put("team",team);
        out.put("roundId",e.get("current_round_id"));
        List<Map<String,Object>> messages=e.get("current_round_id")==null?List.of():
            rows("SELECT id,content,submitted_at FROM mt_typing_submissions WHERE round_id=? AND user_id=? ORDER BY id",e.get("current_round_id"),uid);
        out.put("messages",messages);
        return out;
    }

    @PostMapping("/typing")
    @Transactional
    public void submit(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SubmissionRequest input) {
        var e=lockEvent(club(jwt));long eid=eventId(e);
        if(!yes(e.get("typing_enabled"))||!yes(e.get("typing_open"))||e.get("current_round_id")==null
            ||number(e.get("current_round_id"))!=input.roundId())throw conflict("지금은 전송할 수 없습니다.");
        var team=myTeam(eid,user(jwt));
        if(team==null)throw bad("배정된 조가 없습니다.");
        var userRow=first("SELECT name FROM users WHERE id=? AND status='ACTIVE'",user(jwt));
        if(userRow==null)throw bad("사용자 정보를 확인할 수 없습니다.");
        db.update("INSERT INTO mt_typing_submissions(round_id,user_id,team_id,user_name,team_name,content) VALUES (?,?,?,?,?,?)",
            input.roundId(),user(jwt),team.get("id"),userRow.get("name"),team.get("name"),input.text());
    }

    @PostMapping("/host/typing/start")
    @Transactional
    public void startTyping(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody StartRequest input) {
        var e=lockEvent(hostClub(jwt));long eid=eventId(e);
        if(!yes(e.get("typing_enabled")))throw conflict("문자 게임이 잠겨있습니다.");
        if(yes(e.get("typing_open")))throw conflict("이미 진행 중인 문제입니다.");
        var q=requireQuestion(eid,input.questionId());
        long rid=db.queryForObject("INSERT INTO mt_typing_rounds(event_id,question_id,answer_snapshot) VALUES (?,?,?) RETURNING id",
            Long.class,eid,input.questionId(),q.get("text"));
        db.update("UPDATE mt_events SET typing_open=true,current_round_id=? WHERE id=?",rid,eid);
    }

    @PostMapping("/host/typing/close")
    @Transactional
    public void closeTyping(@AuthenticationPrincipal Jwt jwt) {
        var e=lockEvent(hostClub(jwt));
        if(e.get("current_round_id")!=null){
            db.update("UPDATE mt_typing_rounds SET ended_at=NOW() WHERE id=? AND ended_at IS NULL",e.get("current_round_id"));
        }
        db.update("UPDATE mt_events SET typing_open=false WHERE id=?",eventId(e));
    }

    @GetMapping("/host/typing/results")
    @Transactional(readOnly=true)
    public Map<String,Object> results(@AuthenticationPrincipal Jwt jwt) {
        var e=event(hostClub(jwt));
        if(e==null||e.get("current_round_id")==null)return Map.of("roundId",0,"submissions",List.of(),"ranking",List.of());
        long rid=number(e.get("current_round_id"));
        var round=first("SELECT answer_snapshot,question_id FROM mt_typing_rounds WHERE id=? AND event_id=?",rid,eventId(e));
        if(round==null)return Map.of("roundId",0,"submissions",List.of(),"ranking",List.of());
        String answer=clean((String)round.get("answer_snapshot"));
        var submissions=rows("SELECT id,user_id,user_name,team_name,content,submitted_at FROM mt_typing_submissions WHERE round_id=? ORDER BY id",rid);
        var latest=new LinkedHashMap<Long,Map<String,Object>>();
        for(var s:submissions)latest.put(number(s.get("user_id")),s);
        var all=new ArrayList<Map<String,Object>>();
        for(var s:submissions){
            var copy=new LinkedHashMap<String,Object>(s);
            copy.put("correct",answer.equals(clean((String)s.get("content"))));
            copy.put("latest",latest.get(number(s.get("user_id")))==s);
            all.add(copy);
        }
        var ranking=latest.values().stream()
            .filter(s->answer.equals(clean((String)s.get("content"))))
            .sorted(Comparator.comparingLong(s->number(s.get("id")))).limit(5)
            .map(s->Map.of("name",s.get("user_name"),"teamName",s.get("team_name"))).toList();
        var out=new LinkedHashMap<String,Object>();
        out.put("roundId",rid);
        out.put("questionId",round.get("question_id"));
        out.put("submissions",all);
        out.put("ranking",ranking);
        out.put("open",e.get("typing_open"));
        return out;
    }

    public record TitleRequest(@NotBlank @Size(max=120) String title) {}
    public record NameRequest(@NotBlank @Size(max=80) String name) {}
    public record TextRequest(@NotBlank @Size(max=500) String text) {}
    public record DirectionRequest(@NotBlank String direction) {}
    public record VisibilityRequest(@NotBlank String game, boolean enabled) {}
    public record ActionRequest(@NotBlank String action) {}
    public record StartRequest(@NotNull Long questionId) {}
    public record SubmissionRequest(@NotNull Long roundId,@NotBlank @Size(max=500) String text) {}
}
