package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.domain.model.*;
import com.mediflow.clinical.application.dto.response.DoctorWorkShiftDTO;
import com.mediflow.clinical.infrastructure.persistence.adapter.*;
import com.mediflow.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class DoctorWorkShiftPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static DoctorWorkShiftPersistenceAdapter shifts;
    static ConsultationSessionPersistenceAdapter sessions;
    static final UUID ACTOR=UUID.randomUUID();
    static final Instant NOW=Instant.parse("2026-11-01T00:00:00Z");
    @BeforeAll static void migrate() {
        var ds=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc=new JdbcTemplate(ds);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var mapper=new ObjectMapper().findAndRegisterModules();
        shifts=new DoctorWorkShiftPersistenceAdapter(jdbc,mapper);
        sessions=new ConsultationSessionPersistenceAdapter(jdbc,mapper);
    }
    @BeforeEach void clear() {jdbc.execute("TRUNCATE doctor_work_shift,consultation_session CASCADE");}
    @Test void concurrentDoctorAndRoomConflictsHaveExactlyOneWinner() throws Exception {
        for(boolean sameDoctor:List.of(true,false)) {
            clear(); var a=shift(); var c=a.configuration();
            var b=DoctorWorkShift.active(UUID.randomUUID(),sameDoctor?c.doctorId():UUID.randomUUID(),c.departmentId(),
                    sameDoctor?UUID.randomUUID():c.roomId(),c.shiftDate(),c.startTime(),c.endTime());
            var start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var first=pool.submit(()->insertRace(a,start)); var second=pool.submit(()->insertRace(b,start)); start.countDown();
                assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder("CREATED","SHIFT_OVERLAP");
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_work_shift_audit",Integer.class)).isEqualTo(1);
        }
    }
    @Test void touchingShiftsAllowedAndCancelledShiftReleasesResources() {
        var a=shift(); var c=a.configuration(); inTx(()->shifts.insert(a,ACTOR,NOW));
        var adjacent=DoctorWorkShift.active(UUID.randomUUID(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),c.endTime(),LocalTime.of(12,0));
        inTx(()->shifts.insert(adjacent,ACTOR,NOW));
        inTx(()->{shifts.update(a,a.cancel(1,"cancel"),ACTOR,"cancel",NOW);return null;});
        inTx(()->shifts.insert(DoctorWorkShift.active(UUID.randomUUID(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),c.startTime(),c.endTime()),ACTOR,NOW));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_work_shift_audit",Integer.class)).isEqualTo(4);
    }
    @Test void summaryCountsEntireDepartmentAndExcludesCancelledCapacity() {
        var shift=shift(); var c=shift.configuration();
        for(int i=0;i<25;i++) {
            var session=ConsultationSession.open(UUID.randomUUID(),UUID.randomUUID(),c.departmentId(),UUID.randomUUID(),c.shiftDate(),c.startTime(),c.endTime(),List.of(),4);
            inTx(()->sessions.insert(session,ACTOR,null,NOW));
        }
        var cancelled=ConsultationSession.open(UUID.randomUUID(),UUID.randomUUID(),c.departmentId(),UUID.randomUUID(),c.shiftDate(),c.startTime(),c.endTime(),List.of(),4);
        inTx(()->{sessions.insert(cancelled,ACTOR,null,NOW); sessions.update(cancelled,cancelled.changeStatus(1,ConsultationSessionStatus.CANCELLED,"cancel"),ACTOR,"STATUS_CHANGED","cancel",NOW);return null;});
        var summary=sessions.summarize(c.departmentId(),c.shiftDate(),c.shiftDate());
        assertThat(summary.openSessionCount()).isEqualTo(25);
        assertThat(summary.cancelledSessionCount()).isEqualTo(1);
        assertThat(summary.capacity()).isEqualTo(100);
        assertThat(summary.availableCount()).isEqualTo(100);
        assertThat(sessions.summarize(UUID.randomUUID(),c.shiftDate(),c.shiftDate()).capacity()).isZero();
    }
    @Test void linkedSessionMustKeepDoctorDepartmentRoomDateAndTimeAndBlocksShiftMutation() {
        var shift=shift(); inTx(()->shifts.insert(shift,ACTOR,NOW)); var session=link(shift); var c=session.configuration();
        var invalid=List.of(
                new ConsultationSession.Configuration(UUID.randomUUID(),c.departmentId(),c.roomId(),c.sessionDate(),c.startTime(),c.endTime(),List.of(),4),
                new ConsultationSession.Configuration(c.doctorId(),UUID.randomUUID(),c.roomId(),c.sessionDate(),c.startTime(),c.endTime(),List.of(),4),
                new ConsultationSession.Configuration(c.doctorId(),c.departmentId(),UUID.randomUUID(),c.sessionDate(),c.startTime(),c.endTime(),List.of(),4),
                new ConsultationSession.Configuration(c.doctorId(),c.departmentId(),c.roomId(),c.sessionDate().plusDays(1),c.startTime(),c.endTime(),List.of(),4),
                new ConsultationSession.Configuration(c.doctorId(),c.departmentId(),c.roomId(),c.sessionDate(),LocalTime.of(7,0),c.endTime(),List.of(),4),
                new ConsultationSession.Configuration(c.doctorId(),c.departmentId(),c.roomId(),c.sessionDate(),c.startTime(),LocalTime.of(12,0),List.of(),4));
        for(var config:invalid) assertThatThrownBy(()->inTx(()->{
            sessions.update(session,new ConsultationSession(session.sessionId(),2,config,session.status(),session.counts()),ACTOR,"UPDATED","edit",NOW);return null;
        })).isInstanceOf(DuplicateResourceException.class).hasMessageContaining("SHIFT_SESSION_MISMATCH");
        assertThatThrownBy(()->inTx(()->jdbc.update("UPDATE consultation_session SET doctor_id=? WHERE session_id=?",UUID.randomUUID(),session.sessionId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("SHIFT_SESSION_MISMATCH");
        assertThatThrownBy(()->inTx(()->jdbc.update("UPDATE doctor_work_shift SET status='CANCELLED',revision=2 WHERE shift_id=?",shift.shiftId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("SHIFT_HAS_SESSIONS");
        assertThatThrownBy(()->inTx(()->{shifts.update(shift,shift.cancel(1,"cancel"),ACTOR,"cancel",NOW);return null;}))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("Cancel linked");
        var cancelled=session.changeStatus(1,ConsultationSessionStatus.CANCELLED,"cancel");
        inTx(()->{sessions.update(session,cancelled,ACTOR,"STATUS_CHANGED","cancel",NOW);return null;});
        inTx(()->{shifts.update(shift,shift.cancel(1,"cancel"),ACTOR,"cancel",NOW);return null;});
        assertThat(shifts.findById(shift.shiftId()).orElseThrow().status()).isEqualTo(DoctorWorkShift.Status.CANCELLED);
        assertThatThrownBy(()->inTx(()->{sessions.update(cancelled,new ConsultationSession(cancelled.sessionId(),3,invalid.getFirst(),cancelled.status(),cancelled.counts()),ACTOR,"UPDATED","edit cancelled",NOW);return null;}))
                .isInstanceOfSatisfying(DuplicateResourceException.class,failure->assertThat(failure.getCode()).isEqualTo("SHIFT_SESSION_MISMATCH"));
        assertThatThrownBy(()->inTx(()->jdbc.update("UPDATE consultation_session SET doctor_id=? WHERE session_id=?",UUID.randomUUID(),cancelled.sessionId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("SHIFT_SESSION_MISMATCH");
    }
    @Test void cancelledShiftKeepsStoppedSessionServingItsRegistrations() {
        var shift=shift(); inTx(()->shifts.insert(shift,ACTOR,NOW)); var session=link(shift);
        var stopped=session.changeStatus(1,ConsultationSessionStatus.STOPPED,"nghỉ");
        inTx(()->{sessions.update(session,stopped,ACTOR,"STATUS_CHANGED","nghỉ",NOW);return null;});
        assertThatThrownBy(()->inTx(()->{shifts.update(shift,shift.cancel(1,"nghỉ"),ACTOR,"nghỉ",NOW);return null;}))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("Cancel linked");
        inTx(()->{shifts.cancelKeepingStoppedSessions(shift,shift.cancel(1,"nghỉ"),ACTOR,"nghỉ",NOW);return null;});
        assertThat(shifts.findById(shift.shiftId()).orElseThrow().status()).isEqualTo(DoctorWorkShift.Status.CANCELLED);
        assertThat(shifts.linkedSessionIds(shift.shiftId())).containsExactly(session.sessionId());
        assertThat(shifts.findBySession(session.sessionId())).isPresent();
        inTx(()->{sessions.updateCounts(stopped,stopped.withCounts(new ConsultationSession.Counts(1,0,0,0,stopped.configuration().capacity()-1)),NOW);return null;});
        assertThat(jdbc.queryForObject("SELECT held_count FROM consultation_session WHERE session_id=?",Integer.class,session.sessionId())).isEqualTo(1);
        assertThatThrownBy(()->inTx(()->jdbc.update("UPDATE consultation_session SET status='OPEN',revision=revision+1 WHERE session_id=?",session.sessionId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("SHIFT_SESSION_MISMATCH");
    }
    @Test void cancelKeepingStoppedSessionsRefusesAnOpenSession() {
        var shift=shift(); inTx(()->shifts.insert(shift,ACTOR,NOW)); link(shift);
        assertThatThrownBy(()->inTx(()->{shifts.cancelKeepingStoppedSessions(shift,shift.cancel(1,"nghỉ"),ACTOR,"nghỉ",NOW);return null;}))
                .isInstanceOfSatisfying(DuplicateResourceException.class,failure->assertThat(failure.getCode()).isEqualTo("SHIFT_HAS_SESSIONS"));
    }
    @Test void sessionCreationAndCancellationSerializeOnSourceShiftInBothOrders() throws Exception {
        for(boolean createFirst:List.of(true,false)) {
            clear(); var shift=shift(); inTx(()->shifts.insert(shift,ACTOR,NOW));
            var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var secondStarted=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var first=pool.submit(()->inTx(()->{
                    var current=shifts.findByIdForUpdate(shift.shiftId()).orElseThrow();
                    if(createFirst) linkLocked(current); else shifts.update(current,current.cancel(1,"cancel"),ACTOR,"cancel",NOW);
                    locked.countDown(); await(release); return true;
                }));
                assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->{
                    secondStarted.countDown();
                    try { return inTx(()->{
                        var current=shifts.findByIdForUpdate(shift.shiftId()).orElseThrow();
                        if(createFirst) shifts.update(current,current.cancel(1,"cancel"),ACTOR,"cancel",NOW); else linkLocked(current);
                        return "UNEXPECTED";
                    }); } catch(DuplicateResourceException conflict) {return "SHIFT_HAS_SESSIONS";}
                    catch(IllegalStateException inactive) {return inactive.getMessage();}
                });
                assertThat(secondStarted.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->second.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); assertThat(first.get(10,TimeUnit.SECONDS)).isTrue();
                assertThat(second.get(10,TimeUnit.SECONDS)).isEqualTo(createFirst?"SHIFT_HAS_SESSIONS":"SHIFT_INACTIVE");
            } finally {release.countDown();}
            assertThat(jdbc.queryForObject("SELECT count(*) FROM consultation_session_work_shift",Integer.class)).isEqualTo(createFirst?1:0);
        }
    }
    @Test void linkedSessionEditDoesNotDeadlockWithAnotherSessionCreation() throws Exception {
        var shift=shift(); inTx(()->shifts.insert(shift,ACTOR,NOW)); var c=shift.configuration();
        var session=ConsultationSession.open(UUID.randomUUID(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),c.startTime(),LocalTime.of(9,0),List.of(),4);
        inTx(()->{sessions.insert(session,ACTOR,null,NOW);shifts.linkSession(shift.shiftId(),session.sessionId());return null;});
        var locked=new CountDownLatch(1); var editStarted=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var creator=pool.submit(()->inTx(()->{
                shifts.findByIdForUpdate(shift.shiftId()).orElseThrow(); locked.countDown(); await(editStarted);
                // Wait until the editor is blocked on our source row, then acquire the same doctor/room locks.
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name='shift-edit-race' AND wait_event_type='Lock')",Boolean.class))) {
                    if(System.nanoTime()>deadline) throw new IllegalStateException("editor did not reach source lock");
                    Thread.yield();
                }
                var second=ConsultationSession.open(UUID.randomUUID(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),LocalTime.of(9,0),LocalTime.of(10,0),List.of(),4);
                sessions.insert(second,ACTOR,null,NOW); shifts.linkSession(shift.shiftId(),second.sessionId()); return true;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var editor=pool.submit(()->inTx(()->{
                jdbc.execute("SET LOCAL application_name='shift-edit-race'");
                var before=sessions.findByIdForUpdate(session.sessionId()).orElseThrow(); editStarted.countDown();
                sessions.update(before,before.updateConfiguration(1,before.configuration(),"edit"),ACTOR,"UPDATED","edit",NOW); return true;
            }));
            assertThat(creator.get(10,TimeUnit.SECONDS)).isTrue();
            assertThat(editor.get(10,TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test void duplicateCommandCreatesOneShiftAndAuditAndFailedCommandRollsBackEverything() throws Exception {
        var receipts=new IdempotentCommandPersistenceAdapter(jdbc,new ObjectMapper().findAndRegisterModules());
        var start=new CountDownLatch(1); String path="/api/v2/doctor-work-shifts"; String key=UUID.randomUUID().toString();
        Supplier<com.mediflow.clinical.application.port.out.IdempotentCommandPort.Result<DoctorWorkShiftDTO>> command=()->inTx(()->receipts.execute(
                ACTOR.toString(),"POST",path,key,"a".repeat(64),DoctorWorkShiftDTO.class,201,data->path+"/"+data.shiftId(),()->{
                    var saved=shifts.insert(shift(),ACTOR,NOW);var c=saved.configuration();
                    return new DoctorWorkShiftDTO(saved.shiftId(),saved.revision(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),c.startTime(),c.endTime(),saved.status());
                }));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{await(start);return command.get();});var b=pool.submit(()->{await(start);return command.get();});start.countDown();
            var first=a.get(10,TimeUnit.SECONDS);var second=b.get(10,TimeUnit.SECONDS);
            assertThat(first.data()).isEqualTo(second.data());assertThat(List.of(first.replayed(),second.replayed())).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_work_shift_audit",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->inTx(()->receipts.execute(ACTOR.toString(),"POST",path,key,"b".repeat(64),DoctorWorkShiftDTO.class,201,data->path,()->null)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("different input");
        String failedKey=UUID.randomUUID().toString();
        assertThatThrownBy(()->inTx(()->receipts.execute(ACTOR.toString(),"POST",path,failedKey,"a".repeat(64),DoctorWorkShiftDTO.class,201,data->path,()->{
            shifts.insert(shift(),ACTOR,NOW);throw new IllegalStateException("forced link failure");
        }))).hasMessage("forced link failure");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_work_shift_audit",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_work_shift",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_scheduling_command_receipt WHERE idempotency_key=?",Integer.class,failedKey)).isZero();
    }
    private String insertRace(DoctorWorkShift shift,CountDownLatch start) {
        await(start); try {inTx(()->shifts.insert(shift,ACTOR,NOW));return "CREATED";}
        catch(DuplicateResourceException conflict) {return "SHIFT_OVERLAP";}
    }
    private ConsultationSession link(DoctorWorkShift shift) {return inTx(()->linkLocked(shifts.findByIdForUpdate(shift.shiftId()).orElseThrow()));}
    private ConsultationSession linkLocked(DoctorWorkShift shift) {
        var c=shift.configuration();
        var session=ConsultationSession.open(UUID.randomUUID(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),c.startTime(),c.endTime(),List.of(),4);
        shift.requireContains(session.configuration()); sessions.insert(session,ACTOR,null,NOW); shifts.linkSession(shift.shiftId(),session.sessionId()); return session;
    }
    private static DoctorWorkShift shift() {return DoctorWorkShift.active(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),LocalDate.of(2026,11,2),LocalTime.of(8,0),LocalTime.of(11,0));}
    private static <T>T inTx(Supplier<T> command) {return tx.execute(status->command.get());}
    private static void await(CountDownLatch latch) {try {if(!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Latch timed out");} catch(InterruptedException failure) {Thread.currentThread().interrupt();throw new IllegalStateException(failure);}}
}
