package com.bandclub.rehearsal.mt;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class MtIntegrationTests {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired MtController mt;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired MembershipService membership;

    private Jwt superAdmin() {
        long id=users.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin").orElseThrow().getId();
        Instant now=Instant.now();
        return new Jwt("test", now, now.plusSeconds(300),
            Map.of("alg","HS256"), Map.of("sub",Long.toString(id)));
    }

    private Jwt token(long uid) {
        Instant now=Instant.now();
        return new Jwt("test",now,now.plusSeconds(300),Map.of("alg","HS256"),
            Map.of("sub",Long.toString(uid)));
    }

    @Test
    @Transactional
    void designatedMtHostIsNotRehearsalAdminAndRevocationBlocksAccess() {
        Jwt owner=superAdmin();
        long ownerId=Long.parseLong(owner.getSubject());
        long cid=db.queryForObject("SELECT club_id FROM club_members WHERE user_id=?",Long.class,ownerId);
        long uid=db.queryForObject("INSERT INTO users(login_id,name,status) VALUES ('mt_role_test','MT 테스트 회원','ACTIVE') RETURNING id",Long.class);
        db.update("INSERT INTO club_members(club_id,user_id,role) VALUES (?,?,?)",cid,uid,"MEMBER");
        Jwt member=token(uid);

        assertEquals(true,mt.state(owner).get("host"));
        assertEquals(true,mt.state(owner).get("superAdmin"));
        assertEquals(false,mt.state(member).get("host"));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->mt.host(member)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->mt.mtManagers(member)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->mt.grantMtHost(member,uid)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->mt.revokeMtHost(member,uid)).getStatus());

        mt.grantMtHost(owner,uid);
        assertEquals(true,mt.state(member).get("host"));
        assertEquals(false,mt.state(member).get("superAdmin"));
        assertEquals(Map.of("exists",false),mt.host(member));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->membership.requireAdmin(uid)).getStatus());
        assertTrue(mt.mtManagers(owner).stream().anyMatch(r ->
            ((Number)r.get("id")).longValue()==uid && Boolean.TRUE.equals(r.get("assigned"))));

        mt.revokeMtHost(owner,uid);
        assertEquals(false,mt.state(member).get("host"));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->mt.host(member)).getStatus());

        db.update("UPDATE club_members SET role='ADMIN' WHERE user_id=?",uid);
        assertEquals(false,mt.state(member).get("host"));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->mt.host(member)).getStatus());
    }

    @Test
    @Transactional
    void cannotGrantMtHostToForeignClubMember() {
        Jwt owner=superAdmin();
        long clubId=db.queryForObject("INSERT INTO clubs(name) VALUES ('다른 동아리') RETURNING id",Long.class);
        long uid=db.queryForObject("INSERT INTO users(login_id,name,status) VALUES ('mt_foreign_test','다른 동아리 회원','ACTIVE') RETURNING id",Long.class);
        db.update("INSERT INTO club_members(club_id,user_id,role) VALUES (?,?,?)",clubId,uid,"MEMBER");
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(AppException.class,()->mt.grantMtHost(owner,uid)).getStatus());
    }

    @Test
    @Transactional
    void eventTeamsBuzzerAndTypingSubmissionUseLastAttempt() {
        Jwt admin=superAdmin();
        long userId=Long.parseLong(admin.getSubject());
        assertEquals(false, mt.state(admin).get("exists"));
        mt.createEvent(admin,new MtController.TitleRequest("MT 테스트"));
        var teams=mt.teams(admin);
        assertEquals(8,teams.size());
        assertEquals(5,mt.questions(admin).size());
        assertFalse((boolean)mt.state(admin).get("buzzerVisible"));

        long firstTeam=((Number)teams.get(0).get("id")).longValue();
        mt.assign(admin,firstTeam,userId);
        assertEquals("1조",((Map<?,?>)mt.state(admin).get("team")).get("name"));

        mt.visibility(admin,new MtController.VisibilityRequest("BUZZER",true));
        mt.buzzerAction(admin,new MtController.ActionRequest("OPEN"));
        assertEquals(true,mt.press(admin).get("won"));
        assertEquals(false,mt.press(admin).get("won"));
        assertEquals(firstTeam,((Number)mt.buzzer(admin).get("winnerTeamId")).longValue());
        mt.buzzerAction(admin,new MtController.ActionRequest("OTHER"));
        assertEquals(false,mt.press(admin).get("won"));
        mt.buzzerAction(admin,new MtController.ActionRequest("RETRY"));
        assertEquals(true,mt.press(admin).get("won"));
        mt.buzzerAction(admin,new MtController.ActionRequest("RESET"));

        mt.visibility(admin,new MtController.VisibilityRequest("TYPING",true));
        mt.addQuestion(admin,new MtController.TextRequest("🇰🇷 가"));
        var questions=mt.questions(admin);
        long questionId=((Number)questions.get(questions.size()-1).get("id")).longValue();
        mt.startTyping(admin,new MtController.StartRequest(questionId));
        long rid=((Number)mt.typing(admin).get("roundId")).longValue();
        mt.submit(admin,new MtController.SubmissionRequest(rid,"🇰🇷 가"));
        assertEquals(1,((java.util.List<?>)mt.results(admin).get("ranking")).size());

        mt.submit(admin,new MtController.SubmissionRequest(rid,"🇯🇵 가"));
        assertEquals(0,((java.util.List<?>)mt.results(admin).get("ranking")).size());

        mt.submit(admin,new MtController.SubmissionRequest(rid,"🇰🇷 가"));
        assertEquals(1,((java.util.List<?>)mt.results(admin).get("ranking")).size());
        assertEquals(3,((java.util.List<?>)mt.typing(admin).get("messages")).size());
        mt.closeTyping(admin);
        mt.editQuestion(admin,questionId,new MtController.TextRequest("바뀐 문장"));
        assertEquals(1,((java.util.List<?>)mt.results(admin).get("ranking")).size());
    }

    @Test
    @Transactional
    void mcCloseAfterWinningResetsParticipantToWaitingWithoutLosingWinner() {
        Jwt admin=superAdmin();
        mt.createEvent(admin,new MtController.TitleRequest("버저 종료 검증"));
        long uid=Long.parseLong(admin.getSubject());
        long tid=((Number)mt.teams(admin).get(0).get("id")).longValue();
        mt.assign(admin,tid,uid);
        mt.visibility(admin,new MtController.VisibilityRequest("BUZZER",true));
        mt.buzzerAction(admin,new MtController.ActionRequest("OPEN"));
        assertEquals(true,mt.press(admin).get("won"));
        assertEquals(false,mt.buzzer(admin).get("paused"));
        assertEquals(tid,((Number)mt.buzzer(admin).get("winnerTeamId")).longValue());
        mt.buzzerAction(admin,new MtController.ActionRequest("CLOSE"));
        assertEquals(true,mt.buzzer(admin).get("paused"));
        assertEquals(false,mt.buzzer(admin).get("open"));
        assertEquals(tid,((Number)mt.buzzer(admin).get("winnerTeamId")).longValue());
        mt.buzzerAction(admin,new MtController.ActionRequest("OTHER"));
        assertEquals(false,mt.buzzer(admin).get("paused"));
        assertEquals(true,mt.buzzer(admin).get("open"));
    }

    @Test
    @Transactional
    void endedMtIsListedAndCanBeRestoredWithoutLosingSubmissionHistory() {
        Jwt admin=superAdmin();
        mt.createEvent(admin,new MtController.TitleRequest("기록 보관 검증"));
        long uid=Long.parseLong(admin.getSubject());
        long tid=((Number)mt.teams(admin).get(0).get("id")).longValue();
        mt.assign(admin,tid,uid);
        mt.visibility(admin,new MtController.VisibilityRequest("TYPING",true));
        long q=((Number)mt.questions(admin).get(0).get("id")).longValue();
        mt.startTyping(admin,new MtController.StartRequest(q));
        long rid=((Number)mt.typing(admin).get("roundId")).longValue();
        mt.submit(admin,new MtController.SubmissionRequest(rid,"테스트 전송 기록"));
        mt.closeTyping(admin);
        mt.endEvent(admin);

        var archived=mt.history(admin);
        assertEquals(1,archived.size());
        long eid=((Number)archived.get(0).get("id")).longValue();
        assertEquals(1L,((Number)archived.get(0).get("submission_count")).longValue());
        assertEquals(1,((java.util.List<?>)mt.historyDetail(admin,eid).get("submissions")).size());

        mt.createEvent(admin,new MtController.TitleRequest("새 행사"));
        assertEquals(HttpStatus.CONFLICT,
            assertThrows(AppException.class,()->mt.restoreEvent(admin,eid)).getStatus());
        mt.endEvent(admin);
        mt.restoreEvent(admin,eid);
        assertEquals(true,mt.state(admin).get("exists"));
        assertEquals(false,mt.state(admin).get("typingVisible"));
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM mt_typing_submissions WHERE round_id=?",Long.class,rid));
        assertEquals(HttpStatus.NOT_FOUND,
            assertThrows(AppException.class,()->mt.historyDetail(admin,eid)).getStatus());
    }

    @Test
    @Transactional
    void archivedMtDeletionRemovesOnlyThatEventsChildren() {
        Jwt admin=superAdmin();
        mt.createEvent(admin,new MtController.TitleRequest("삭제 대상 MT"));
        long uid=Long.parseLong(admin.getSubject());
        long tid=((Number)mt.teams(admin).get(0).get("id")).longValue();
        mt.assign(admin,tid,uid);
        mt.visibility(admin,new MtController.VisibilityRequest("TYPING",true));
        long q=((Number)mt.questions(admin).get(0).get("id")).longValue();
        mt.startTyping(admin,new MtController.StartRequest(q));
        long rid=((Number)mt.typing(admin).get("roundId")).longValue();
        mt.submit(admin,new MtController.SubmissionRequest(rid,"삭제될 메시지"));
        mt.closeTyping(admin);
        mt.endEvent(admin);
        long archivedId=((Number)mt.history(admin).get(0).get("id")).longValue();

        mt.createEvent(admin,new MtController.TitleRequest("보존할 활성 MT"));
        assertEquals(HttpStatus.NOT_FOUND,
            assertThrows(AppException.class,()->mt.deleteArchivedEvent(admin,
                ((Number)db.queryForMap("SELECT id FROM mt_events WHERE ended_at IS NULL AND club_id=(SELECT club_id FROM mt_events WHERE id=?)",archivedId).get("id")).longValue()))
                .getStatus());
        mt.deleteArchivedEvent(admin,archivedId);
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mt_events WHERE id=?",Long.class,archivedId));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mt_typing_rounds WHERE id=?",Long.class,rid));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mt_typing_submissions WHERE round_id=?",Long.class,rid));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mt_teams WHERE event_id=?",Long.class,archivedId));
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM mt_questions WHERE event_id=?",Long.class,archivedId));
        assertEquals(true,mt.state(admin).get("exists"));
        assertEquals(HttpStatus.NOT_FOUND,
            assertThrows(AppException.class,()->mt.deleteArchivedEvent(admin,archivedId)).getStatus());
    }

    @Test
    @Transactional
    void cannotDeleteOtherClubsArchivedMtOrAsOrdinaryMember() {
        Jwt admin=superAdmin();
        long cid=db.queryForObject("INSERT INTO clubs(name) VALUES ('MT 삭제 격리') RETURNING id",Long.class);
        long id=db.queryForObject("INSERT INTO mt_events(club_id,title,ended_at) " +
            "VALUES (?,'다른 동아리 기록',NOW()) RETURNING id",Long.class,cid);
        assertEquals(HttpStatus.NOT_FOUND,
            assertThrows(AppException.class,()->mt.deleteArchivedEvent(admin,id)).getStatus());
        long uid=db.queryForObject("INSERT INTO users(login_id,name,status) " +
            "VALUES ('mt_delete_test','비관리자','ACTIVE') RETURNING id",Long.class);
        long homeCid=db.queryForObject("SELECT club_id FROM club_members WHERE user_id=?",Long.class,
            Long.parseLong(admin.getSubject()));
        db.update("INSERT INTO club_members(club_id,user_id,role) VALUES (?,?,?)",homeCid,uid,"MEMBER");
        assertEquals(HttpStatus.FORBIDDEN,
            assertThrows(AppException.class,()->mt.deleteArchivedEvent(token(uid),id)).getStatus());
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM mt_events WHERE id=?",Long.class,id));
    }

    @Test
    @Transactional
    void hiddenFeaturesNeverExposeGameData() {
        Jwt admin=superAdmin();
        mt.createEvent(admin,new MtController.TitleRequest("비공개 MT"));
        assertEquals(Map.of("visible",false),mt.buzzer(admin));
        assertEquals(Map.of("visible",false),mt.typing(admin));
        assertThrows(RuntimeException.class,()->mt.press(admin));
        assertThrows(RuntimeException.class,()->mt.startTyping(admin,new MtController.StartRequest(1L)));
    }
}
