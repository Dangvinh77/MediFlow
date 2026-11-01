package com.mediflow.clinical.application.service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import com.mediflow.clinical.application.dto.request.*;
import com.mediflow.clinical.application.dto.response.*;
import com.mediflow.clinical.application.port.out.*;
import com.mediflow.clinical.domain.model.*;
import com.mediflow.common.api.*;
import com.mediflow.common.exception.*;

@Service
@ConditionalOnProperty(prefix="mediflow.clinical.scheduling",name="enabled",havingValue="true")
public class DoctorWorkShiftApplicationService {
    private static final String PATH = "/api/v2/doctor-work-shifts";
    private final DoctorWorkShiftRepositoryPort shifts;
    private final ConsultationSessionRepositoryPort sessions;
    private final ConsultationSessionApplicationService schedules;
    private final IdempotentCommandPort commands;
    private final Clock clock;
    public DoctorWorkShiftApplicationService(DoctorWorkShiftRepositoryPort shifts,
            ConsultationSessionRepositoryPort sessions, ConsultationSessionApplicationService schedules,
            IdempotentCommandPort commands, @Qualifier("clinicalSchedulingClock") Clock clock) {
        this.shifts=shifts; this.sessions=sessions; this.schedules=schedules; this.commands=commands; this.clock=clock;
    }
    public PageResult<DoctorWorkShiftDTO> search(UUID departmentId, LocalDate fromDate, LocalDate toDate, PageQuery page) {
        range(fromDate,toDate);
        var actor=schedules.requireHumanActor("ADMIN","MANAGER","DOCTOR");
        schedules.requireScheduleScope(actor,departmentId);
        return shifts.search(departmentId,fromDate,toDate,page).map(DoctorWorkShiftApplicationService::dto);
    }
    public List<DoctorWorkShiftDTO> mine(LocalDate fromDate, LocalDate toDate) {
        range(fromDate,toDate);
        UUID doctorId=schedules.currentDoctorId();
        return shifts.findByDoctor(doctorId,fromDate,toDate).stream().map(DoctorWorkShiftApplicationService::dto).toList();
    }
    public IdempotentCommandPort.Result<DoctorWorkShiftDTO> create(DoctorWorkShiftCreateRequest request,String key) {
        var actor=schedules.requireHumanActor("ADMIN","MANAGER","DOCTOR");
        schedules.requireScheduleScope(actor,request.departmentId());
        schedules.requireEligible(request.doctorId(),request.departmentId(),request.roomId());
        key(key);
        return commands.execute(actor.accountId().toString(),"POST",PATH,key,hash(request),DoctorWorkShiftDTO.class,
                201,value->PATH+"/"+value.shiftId(),()->dto(shifts.insert(domain(()->DoctorWorkShift.active(UUID.randomUUID(),
                        request.doctorId(),request.departmentId(),request.roomId(),request.shiftDate(),
                        request.startTime(),request.endTime())),actor.accountId(),clock.instant())));
    }
    public IdempotentCommandPort.Result<DoctorWorkShiftDTO> update(UUID id,DoctorWorkShiftUpdateRequest request,String key) {
        var visible=read(id);
        var actor=schedules.requireHumanActor("ADMIN","MANAGER","DOCTOR");
        schedules.requireScheduleScope(actor,visible.configuration().departmentId());
        if (!visible.configuration().departmentId().equals(request.departmentId()))
            throw new BusinessRuleException("SCHEDULE_INELIGIBLE","Shift department cannot change");
        schedules.requireEligible(request.doctorId(),request.departmentId(),request.roomId());
        key(key);
        return commands.execute(actor.accountId().toString(),"PUT",PATH+"/"+id,key,hash(request),DoctorWorkShiftDTO.class,
                200,value->null,()->{
                    var before=lock(id);
                    var after=domain(()->before.update(request.expectedRevision(),new DoctorWorkShift.Configuration(
                            request.doctorId(),request.departmentId(),request.roomId(),request.shiftDate(),
                            request.startTime(),request.endTime()),request.reason()));
                    shifts.update(before,after,actor.accountId(),request.reason(),clock.instant());
                    return dto(after);
                });
    }
    public IdempotentCommandPort.Result<DoctorWorkShiftDTO> cancel(UUID id,DoctorWorkShiftCancelRequest request,String key) {
        var visible=read(id);
        var actor=schedules.requireHumanActor("ADMIN","MANAGER","DOCTOR");
        schedules.requireScheduleScope(actor,visible.configuration().departmentId());
        key(key);
        return commands.execute(actor.accountId().toString(),"PUT",PATH+"/"+id+"/cancel",key,hash(request),DoctorWorkShiftDTO.class,
                200,value->null,()->{
                    var before=lock(id);
                    var after=domain(()->before.cancel(request.expectedRevision(),request.reason()));
                    shifts.update(before,after,actor.accountId(),request.reason(),clock.instant());
                    return dto(after);
                });
    }
    public IdempotentCommandPort.Result<ConsultationSessionDTO> createSession(UUID id,ConsultationSessionCreateRequest request,String key) {
        var visible=read(id);
        var actor=schedules.requireHumanActor("ADMIN","MANAGER","DOCTOR");
        schedules.requireScheduleScope(actor,visible.configuration().departmentId());
        schedules.requireEligible(request.doctorId(),request.departmentId(),request.roomId());
        key(key);
        return commands.execute(actor.accountId().toString(),"POST",PATH+"/"+id+"/consultation-sessions",key,hash(request),
                ConsultationSessionDTO.class,201,value->"/api/v2/consultation-sessions/"+value.sessionId(),()->{
                    var shift=lock(id);
                    var session=domain(()->ConsultationSession.open(UUID.randomUUID(),request.doctorId(),request.departmentId(),
                            request.roomId(),request.sessionDate(),request.startTime(),request.endTime(),
                            request.breaks().stream().map(b->new ConsultationBreak(b.startTime(),b.endTime())).toList(),request.capacity()));
                    domain(()->{shift.requireContains(session.configuration());return shift;});
                    var saved=sessions.insert(session,actor.accountId(),null,clock.instant());
                    shifts.linkSession(id,saved.sessionId());
                    return schedules.toDto(saved);
                });
    }
    private DoctorWorkShift read(UUID id) {
        return shifts.findById(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND","Shift was not found"));
    }
    private DoctorWorkShift lock(UUID id) {
        return shifts.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("RESOURCE_NOT_FOUND","Shift was not found"));
    }
    private static void range(LocalDate from,LocalDate to) {
        if(from==null||to==null||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>30)
            throw new BusinessRuleException("INVALID_REQUEST","A date range of at most 31 days is required");
    }
    private static void key(String value) {
        if(value==null||value.isEmpty()||value.length()>120||!value.chars().allMatch(c->c>=33&&c<=126))
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY","Idempotency-Key must contain 1-120 printable ASCII characters without spaces");
    }
    private static String hash(Object request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    private static <T> T domain(java.util.function.Supplier<T> command) {
        try {return command.get();}
        catch(IllegalStateException conflict) {throw new DuplicateResourceException(conflict.getMessage(),conflict.getMessage());}
        catch(IllegalArgumentException invalid) {throw new BusinessRuleException("INVALID_REQUEST",invalid.getMessage());}
    }
    private static DoctorWorkShiftDTO dto(DoctorWorkShift s) {
        var c=s.configuration();
        return new DoctorWorkShiftDTO(s.shiftId(),s.revision(),c.doctorId(),c.departmentId(),c.roomId(),c.shiftDate(),
                c.startTime(),c.endTime(),s.status());
    }
}
