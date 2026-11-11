package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.AddCourseVisitRequest;
import com.mediflow.clinical.application.dto.request.CloseTreatmentCourseRequest;
import com.mediflow.clinical.application.dto.request.OpenTreatmentCourseRequest;
import com.mediflow.clinical.application.dto.response.TimelineEntryDTO;
import com.mediflow.clinical.application.dto.response.TreatmentCourseDTO;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientTimelineReadPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.application.port.out.TreatmentCourseRepositoryPort;
import com.mediflow.clinical.application.port.out.TreatmentCourseRepositoryPort.CourseView;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.TreatmentCourse;
import com.mediflow.clinical.domain.model.TreatmentCourseDecision;
import com.mediflow.clinical.domain.model.TreatmentCourseStatus;
import com.mediflow.common.exception.ForbiddenOperationException;

class TreatmentCourseApplicationServiceTest {
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    final UUID patient = UUID.randomUUID();
    final UUID department = UUID.randomUUID();
    final UUID doctor = UUID.randomUUID();
    final UUID account = UUID.randomUUID();

    final TreatmentCourseRepositoryPort courses = mock(TreatmentCourseRepositoryPort.class);
    final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    final StaffLookupPort staff = mock(StaffLookupPort.class);
    final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    final RecordReadGuard guard = mock(RecordReadGuard.class);
    final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    final CorrelationIdProvider correlationIds = mock(CorrelationIdProvider.class);
    final TreatmentCourseApplicationService service = new TreatmentCourseApplicationService(courses, records, actors,
            staff, authority, guard, true, idempotency, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), correlationIds);

    final MedicalRecord first = record(patient, department);

    @BeforeEach void setUp() {
        lenient().when(actors.current()).thenReturn(new ActorIdentity(account, doctor, null, "DOCTOR"));
        lenient().when(staff.departmentOf(doctor)).thenReturn(Optional.of(department));
        lenient().when(correlationIds.currentOrCreate()).thenReturn(UUID.randomUUID());
        lenient().when(records.findById(first.getRecordId())).thenReturn(Optional.of(first));
        lenient().when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(),
                any(), any())).thenAnswer(call -> new IdempotentCommandPort.Result<>(
                        ((Supplier<?>) call.getArgument(8)).get(), call.getArgument(6), null, false));
        lenient().when(courses.view(any())).thenAnswer(call -> Optional.empty());
    }

    private MedicalRecord record(UUID patientId, UUID departmentId) {
        return MedicalRecord.openForAppointment(patientId, doctor, departmentId, LocalDate.parse("2026-11-10"), "Sốt",
                UUID.randomUUID(), NOW);
    }

    private TreatmentCourse stored() {
        TreatmentCourse course = TreatmentCourse.open(patient, department, "Tăng huyết áp", "Theo dõi", doctor,
                first.getRecordId(), NOW);
        when(courses.findByIdForUpdate(course.getCourseId())).thenReturn(Optional.of(course));
        when(courses.view(course.getCourseId())).thenReturn(Optional.of(new CourseView(course, LocalDate.parse("2026-11-10"))));
        return course;
    }

    private OpenTreatmentCourseRequest openRequest() {
        return new OpenTreatmentCourseRequest(patient, "Tăng huyết áp", "Theo dõi", first.getRecordId());
    }

    @Test void departmentComesFromFirstVisit() {
        when(courses.view(any())).thenAnswer(call -> Optional.of(new CourseView(TreatmentCourse.open(patient, department,
                "Tăng huyết áp", "Theo dõi", doctor, first.getRecordId(), NOW), LocalDate.parse("2026-11-10"))));
        var result = service.open(openRequest(), "k1");
        assertThat(result.status()).isEqualTo(201);
        verify(courses).insert(org.mockito.ArgumentMatchers.argThat(course -> course.getDepartmentId().equals(department)
                && course.getResponsibleDoctorId().equals(doctor) && course.getRecordIds().equals(List.of(first.getRecordId()))));
    }

    @Test void openRefusesWhenVisitBelongsToAnotherPatient() {
        assertThatThrownBy(() -> service.open(new OpenTreatmentCourseRequest(UUID.randomUUID(), "P", "R", first.getRecordId()), "k"))
                .extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        verify(courses, never()).insert(any());
    }

    @Test void onlyResponsibleDoctorOfFirstVisitOpens() {
        when(actors.current()).thenReturn(new ActorIdentity(account, UUID.randomUUID(), null, "DOCTOR"));
        assertThatThrownBy(() -> service.open(openRequest(), "k")).isInstanceOf(ForbiddenOperationException.class);
        verify(courses, never()).insert(any());
    }

    @Test void visitOfAnotherPatientIsRefused() {
        TreatmentCourse course = stored();
        MedicalRecord foreign = record(UUID.randomUUID(), department);
        when(records.findById(foreign.getRecordId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.addVisit(course.getCourseId(), new AddCourseVisitRequest(foreign.getRecordId()), "k"))
                .extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        verify(courses, never()).addVisit(any(), any(), any(), any());
    }

    @Test void visitFromAnotherDepartmentIsAllowed() {
        TreatmentCourse course = stored();
        MedicalRecord elsewhere = record(patient, UUID.randomUUID());
        when(records.findById(elsewhere.getRecordId())).thenReturn(Optional.of(elsewhere));
        service.addVisit(course.getCourseId(), new AddCourseVisitRequest(elsewhere.getRecordId()), "k");
        verify(guard).requireRecord(elsewhere);
        verify(courses).addVisit(course.getCourseId(), elsewhere.getRecordId(), doctor, NOW);
    }

    @Test void readerWhoCannotReadTheVisitCannotAddIt() {
        TreatmentCourse course = stored();
        MedicalRecord other = record(patient, department);
        when(records.findById(other.getRecordId())).thenReturn(Optional.of(other));
        when(guard.requireRecord(other)).thenThrow(new ForbiddenOperationException("RECORD_READ_DENIED", "no"));
        assertThatThrownBy(() -> service.addVisit(course.getCourseId(), new AddCourseVisitRequest(other.getRecordId()), "k"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(courses, never()).addVisit(any(), any(), any(), any());
    }

    @Test void oneVisitMayBelongToTwoCourses() {
        TreatmentCourse one = stored();
        TreatmentCourse two = stored();
        MedicalRecord shared = record(patient, department);
        when(records.findById(shared.getRecordId())).thenReturn(Optional.of(shared));
        service.addVisit(one.getCourseId(), new AddCourseVisitRequest(shared.getRecordId()), "a");
        service.addVisit(two.getCourseId(), new AddCourseVisitRequest(shared.getRecordId()), "b");
        verify(courses).addVisit(one.getCourseId(), shared.getRecordId(), doctor, NOW);
        verify(courses).addVisit(two.getCourseId(), shared.getRecordId(), doctor, NOW);
    }

    @Test void twoOpenCoursesForOnePatient() {
        when(courses.view(any())).thenAnswer(call -> Optional.of(new CourseView(TreatmentCourse.open(patient, department,
                "P", "R", doctor, first.getRecordId(), NOW), null)));
        service.open(openRequest(), "k1");
        service.open(new OpenTreatmentCourseRequest(patient, "Đái tháo đường", "Theo dõi", first.getRecordId()), "k2");
        verify(courses, org.mockito.Mockito.times(2)).insert(any());
    }

    @Test void onlyResponsibleDoctorOrHeadCloses() {
        TreatmentCourse course = stored();
        UUID otherDoctor = UUID.randomUUID();
        UUID otherAccount = UUID.randomUUID();
        when(actors.current()).thenReturn(new ActorIdentity(otherAccount, otherDoctor, null, "DOCTOR"));
        when(authority.authority(eq(otherAccount), eq(department), anyString())).thenReturn(snapshot(otherAccount, otherDoctor, false));
        CloseTreatmentCourseRequest close = new CloseTreatmentCourseRequest(TreatmentCourseDecision.RESOLVED, "Ổn định");
        assertThatThrownBy(() -> service.close(course.getCourseId(), close, "k")).isInstanceOf(ForbiddenOperationException.class);
        verify(courses, never()).close(any());

        when(authority.authority(eq(otherAccount), eq(department), anyString())).thenReturn(snapshot(otherAccount, otherDoctor, true));
        service.close(course.getCourseId(), close, "k2");
        verify(courses).close(course);
        assertThat(course.getStatus()).isEqualTo(TreatmentCourseStatus.CLOSED);
    }

    @Test void headOfAnotherDepartmentIsNotHead() {
        TreatmentCourse course = stored();
        UUID otherDoctor = UUID.randomUUID();
        UUID otherAccount = UUID.randomUUID();
        when(actors.current()).thenReturn(new ActorIdentity(otherAccount, otherDoctor, null, "DOCTOR"));
        when(authority.authority(eq(otherAccount), eq(department), anyString()))
                .thenReturn(snapshot(otherAccount, otherDoctor, true, UUID.randomUUID()));
        CloseTreatmentCourseRequest close = new CloseTreatmentCourseRequest(TreatmentCourseDecision.RESOLVED, "Ổn định");
        assertThatThrownBy(() -> service.close(course.getCourseId(), close, "k")).isInstanceOf(ForbiddenOperationException.class);
        verify(courses, never()).close(any());
    }

    @Test void responsibleDoctorClosesWithoutHeadLookup() {
        TreatmentCourse course = stored();
        service.close(course.getCourseId(), new CloseTreatmentCourseRequest(TreatmentCourseDecision.RESOLVED, "Ổn"), "k");
        verify(courses).close(course);
        org.mockito.Mockito.verifyNoInteractions(authority);
    }

    @Test void forPatientMarksReviewDue() {
        TreatmentCourse course = TreatmentCourse.open(patient, department, "P", "R", doctor, first.getRecordId(), NOW);
        when(courses.findByPatient(patient)).thenReturn(List.of(new CourseView(course, LocalDate.parse("2026-01-01"))));
        when(guard.requirePatient(patient)).thenReturn(com.mediflow.clinical.application.dto.authority.RecordReadDecision.allow("DOCTOR"));
        List<TreatmentCourseDTO> list = service.forPatient(patient);
        assertThat(list).singleElement().satisfies(dto -> {
            assertThat(dto.reviewDue()).isTrue();
            assertThat(dto.lastVisitDate()).isEqualTo(LocalDate.parse("2026-01-01"));
        });
    }

    @Test void nurseSeesOnlyVisitsOfHerDepartmentInACourse() {
        TreatmentCourse course = TreatmentCourse.open(patient, department, "P", "R", doctor, first.getRecordId(), NOW);
        MedicalRecord elsewhere = record(patient, UUID.randomUUID());
        course.addVisit(elsewhere.getRecordId(), doctor, NOW);
        when(records.findById(elsewhere.getRecordId())).thenReturn(Optional.of(elsewhere));
        when(courses.findByPatient(patient)).thenReturn(List.of(new CourseView(course, LocalDate.parse("2026-11-20"))));
        when(guard.requirePatient(patient)).thenReturn(
                com.mediflow.clinical.application.dto.authority.RecordReadDecision.allowDepartment("NURSE", department));
        assertThat(service.forPatient(patient)).singleElement().satisfies(dto -> {
            assertThat(dto.recordIds()).containsExactly(first.getRecordId());
            assertThat(dto.lastVisitDate()).isEqualTo(first.getExaminationDate());
        });
    }

    @Test void courseEmitsNoEvent() {
        assertThat(Arrays.stream(TreatmentCourseApplicationService.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .doesNotContain(ClinicalEventPublisherPort.class);
    }

    @Test void timelineListsCourseIds() {
        PatientTimelineReadPort timeline = mock(PatientTimelineReadPort.class);
        UUID recordId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(timeline.page(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(new PatientTimelineReadPort.TimelinePage(
                List.of(new PatientTimelineReadPort.TimelineRow(recordId, LocalDate.parse("2026-11-10"), department, doctor,
                        null, null, "OPEN", null, null)), 1));
        when(courses.courseIdsByRecord(List.of(recordId))).thenReturn(Map.of(recordId, List.of(courseId)));
        var withCourses = new PatientTimelineApplicationService(timeline, records, mock(ExternalResultRepositoryPort.class),
                guard, false, courses, true);
        TimelineEntryDTO entry = withCourses.timeline(patient, 0, 20, null, null).content().get(0);
        assertThat(entry.treatmentCourseIds()).containsExactly(courseId);

        var withoutCourses = new PatientTimelineApplicationService(timeline, records, mock(ExternalResultRepositoryPort.class),
                guard, false, courses, false);
        assertThat(withoutCourses.timeline(patient, 0, 20, null, null).content().get(0).treatmentCourseIds()).isNull();
    }

    private SchedulingAuthoritySnapshot snapshot(UUID account, UUID staffId, boolean head) {
        return snapshot(account, staffId, head, department);
    }

    private static SchedulingAuthoritySnapshot snapshot(UUID account, UUID staffId, boolean head, UUID departmentId) {
        return new SchedulingAuthoritySnapshot(account, true, true, "DOCTOR", staffId, true, departmentId, true, head, 1L, NOW);
    }
}
