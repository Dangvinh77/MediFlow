package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.time.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.clinical.application.port.out.DoctorWorkShiftRepositoryPort;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
import com.mediflow.common.api.*;
import com.mediflow.common.exception.DuplicateResourceException;

@Component
public class DoctorWorkShiftPersistenceAdapter implements DoctorWorkShiftRepositoryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public DoctorWorkShiftPersistenceAdapter(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}
    @Override @Transactional(readOnly=true)
    public Optional<DoctorWorkShift> findById(UUID id) {
        return jdbc.query("SELECT * FROM doctor_work_shift WHERE shift_id=?",this::map,id).stream().findFirst();
    }
    @Override @Transactional
    public Optional<DoctorWorkShift> findByIdForUpdate(UUID id) {
        return jdbc.query("SELECT * FROM doctor_work_shift WHERE shift_id=? FOR UPDATE",this::map,id).stream().findFirst();
    }
    @Override @Transactional
    public DoctorWorkShift insert(DoctorWorkShift shift,UUID actor,Instant at) {
        var c=shift.configuration();
        resources(c.doctorId(),c.roomId());
        noOverlap(shift);
        jdbc.update("""
                INSERT INTO doctor_work_shift(shift_id,revision,doctor_id,department_id,room_id,shift_date,
                    start_time,end_time,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,shift.shiftId(),shift.revision(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),
                c.startTime(),c.endTime(),shift.status().name(),Timestamp.from(at),Timestamp.from(at));
        audit(null,shift,actor,null,at);
        return shift;
    }
    @Override @Transactional
    public void update(DoctorWorkShift before,DoctorWorkShift after,UUID actor,String reason,Instant at) {
        // All callers hold this lock through the command receipt transaction. Re-lock defensively.
        var current=findByIdForUpdate(before.shiftId()).orElseThrow();
        if(current.revision()!=before.revision()) throw new DuplicateResourceException("REVISION_CONFLICT","Shift revision changed");
        if(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM consultation_session_work_shift l
                JOIN consultation_session s USING(session_id) WHERE l.shift_id=? AND s.status<>'CANCELLED')
                """,Boolean.class,before.shiftId())))
            throw new DuplicateResourceException("SHIFT_HAS_SESSIONS","Cancel linked consultation sessions before changing the shift");
        var c=after.configuration();
        resources(before.configuration().doctorId(),before.configuration().roomId(),c.doctorId(),c.roomId());
        noOverlap(after);
        int changed=jdbc.update("""
                UPDATE doctor_work_shift SET revision=?,doctor_id=?,department_id=?,room_id=?,shift_date=?,
                    start_time=?,end_time=?,status=?,updated_at=? WHERE shift_id=? AND revision=?
                """,after.revision(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),c.startTime(),c.endTime(),
                after.status().name(),Timestamp.from(at),after.shiftId(),before.revision());
        if(changed!=1) throw new DuplicateResourceException("REVISION_CONFLICT","Shift revision changed");
        audit(before,after,actor,reason,at);
    }
    @Override @Transactional(readOnly=true)
    public PageResult<DoctorWorkShift> search(UUID department,LocalDate from,LocalDate to,PageQuery page) {
        Long total=jdbc.queryForObject("SELECT count(*) FROM doctor_work_shift WHERE department_id=? AND shift_date BETWEEN ? AND ?",
                Long.class,department,from,to);
        var rows=jdbc.query("""
                SELECT * FROM doctor_work_shift WHERE department_id=? AND shift_date BETWEEN ? AND ?
                ORDER BY shift_date,start_time,shift_id LIMIT ? OFFSET ?
                """,this::map,department,from,to,page.size(),(long)page.page()*page.size());
        return PageResult.of(rows,total==null?0:total,page.page(),page.size());
    }
    @Override @Transactional(readOnly=true)
    public List<DoctorWorkShift> findByDoctor(UUID doctor,LocalDate from,LocalDate to) {
        return jdbc.query("SELECT * FROM doctor_work_shift WHERE doctor_id=? AND shift_date BETWEEN ? AND ? ORDER BY shift_date,start_time,shift_id",
                this::map,doctor,from,to);
    }
    @Override @Transactional
    public void linkSession(UUID shiftId,UUID sessionId) {
        jdbc.update("INSERT INTO consultation_session_work_shift(session_id,shift_id) VALUES (?,?)",sessionId,shiftId);
    }
    @Override @Transactional(readOnly=true)
    public List<UUID> linkedSessionIds(UUID shiftId) {
        return jdbc.queryForList("SELECT session_id FROM consultation_session_work_shift WHERE shift_id=? ORDER BY session_id",UUID.class,shiftId);
    }
    @Override @Transactional(readOnly=true)
    public Optional<DoctorWorkShift> findBySession(UUID sessionId) {
        return jdbc.query("""
                SELECT w.* FROM doctor_work_shift w JOIN consultation_session_work_shift l ON l.shift_id=w.shift_id
                WHERE l.session_id=?
                """,this::map,sessionId).stream().findFirst();
    }
    @Override @Transactional
    public void cancelKeepingStoppedSessions(DoctorWorkShift before,DoctorWorkShift after,UUID actor,String reason,Instant at) {
        var current=findByIdForUpdate(before.shiftId()).orElseThrow();
        if(current.revision()!=before.revision()) throw new DuplicateResourceException("REVISION_CONFLICT","Shift revision changed");
        if(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM consultation_session_work_shift l
                JOIN consultation_session s USING(session_id) WHERE l.shift_id=? AND s.status='OPEN')
                """,Boolean.class,before.shiftId())))
            throw new DuplicateResourceException("SHIFT_HAS_SESSIONS","Cancel linked consultation sessions before changing the shift");
        int changed=jdbc.update("UPDATE doctor_work_shift SET revision=?,status=?,updated_at=? WHERE shift_id=? AND revision=?",
                after.revision(),after.status().name(),Timestamp.from(at),after.shiftId(),before.revision());
        if(changed!=1) throw new DuplicateResourceException("REVISION_CONFLICT","Shift revision changed");
        audit(before,after,actor,reason,at);
    }
    private void resources(UUID... ids) {
        Stream.of(ids).distinct().sorted().forEach(id->jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                rs->{rs.next();return null;},"work-shift:"+id));
    }
    private void noOverlap(DoctorWorkShift shift) {
        if(shift.status()==DoctorWorkShift.Status.CANCELLED) return;
        var c=shift.configuration();
        if(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM doctor_work_shift WHERE shift_id<>? AND status='ACTIVE'
                AND shift_date=? AND (doctor_id=? OR room_id=?) AND start_time<? AND end_time>?)
                """,Boolean.class,shift.shiftId(),c.shiftDate(),c.doctorId(),c.roomId(),c.endTime(),c.startTime())))
            throw new DuplicateResourceException("SHIFT_OVERLAP","Doctor or exclusive consultation room has an overlapping shift");
    }
    private void audit(DoctorWorkShift before,DoctorWorkShift after,UUID actor,String reason,Instant at) {
        try {
            jdbc.update("""
                    INSERT INTO doctor_work_shift_audit(shift_id,revision,actor_account_id,before_state,after_state,reason,occurred_at)
                    VALUES (?,?,?,?::jsonb,?::jsonb,?,?)
                    """,after.shiftId(),after.revision(),actor,before==null?null:mapper.writeValueAsString(before),
                    mapper.writeValueAsString(after),reason,Timestamp.from(at));
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) {throw new IllegalStateException("Cannot serialize shift audit",failure);}
    }
    private DoctorWorkShift map(ResultSet rs,int row) throws SQLException {
        return new DoctorWorkShift(rs.getObject("shift_id",UUID.class),rs.getInt("revision"),
                new DoctorWorkShift.Configuration(rs.getObject("doctor_id",UUID.class),rs.getObject("department_id",UUID.class),
                        rs.getObject("room_id",UUID.class),rs.getObject("shift_date",LocalDate.class),
                        rs.getObject("start_time",LocalTime.class),rs.getObject("end_time",LocalTime.class)),
                DoctorWorkShift.Status.valueOf(rs.getString("status")));
    }
}
