package com.mediflow.organization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.dto.request.*;
import com.mediflow.organization.application.event.SurgeryAuthorityChangedEvent;
import com.mediflow.organization.application.mapper.SurgeryAuthorityMapper;
import com.mediflow.organization.application.port.out.*;
import com.mediflow.organization.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryAuthorityServiceTest {
    private static final UUID ROOM = UUID.fromString("01000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("02000000-0000-0000-0000-000000000001");
    private static final UUID DEPT = UUID.fromString("03000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");
    private static final Instant START = Instant.parse("2026-10-06T02:00:00Z");
    private static final Instant END = START.plusSeconds(3600);
    private final SurgeryAuthorityRepository authority = mock(SurgeryAuthorityRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final SurgeryAuthorityEventPort events = mock(SurgeryAuthorityEventPort.class);
    private final CorrelationIdProvider correlations = () -> UUID.fromString("04000000-0000-0000-0000-000000000001");
    private final SurgeryAuthorityService service = new SurgeryAuthorityService(authority, staff,
            departments, events, correlations, Clock.fixed(NOW, ZoneOffset.UTC),
            org.mapstruct.factory.Mappers.getMapper(SurgeryAuthorityMapper.class));
    private Department department;
    private Staff doctor;

    @BeforeEach
    void setup() {
        department = Department.reconstitute(DEPT, "Surgery", "SUR", DepartmentType.CLINICAL,
                null, "A", true, NOW, NOW);
        doctor = Staff.reconstitute(STAFF, "Doctor", DEPT, JobTitle.DOCTOR, null,
                "LIC-001", null, null, true, NOW, NOW);
    }

    @Test
    void lookup_noGrant_doesNotInferFromLicensedDoctor() throws Exception {
        when(staff.findById(STAFF)).thenReturn(Optional.of(doctor));
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        when(authority.findCapability(STAFF, SurgicalTeamRole.PRIMARY_SURGEON)).thenReturn(Optional.empty());
        assertFixture("eligibility.denied.json", service.lookupEligibility(STAFF, SurgicalTeamRole.PRIMARY_SURGEON, START, END));
        verifyNoInteractions(events);
    }

    @Test
    void lookup_realRoomAndExplicitGrant_matchCanonicalProducerFixtures() throws Exception {
        when(authority.findRoom(ROOM)).thenReturn(Optional.of(new OperatingRoom(ROOM,"OR-1","Room",DEPT,true,1,NOW)));
        when(staff.findById(STAFF)).thenReturn(Optional.of(doctor));
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        when(authority.findCapability(STAFF, SurgicalTeamRole.PRIMARY_SURGEON)).thenReturn(Optional.of(
                new SurgicalCapability(STAFF,SurgicalTeamRole.PRIMARY_SURGEON,DEPT,true,START,END,1,NOW)));
        assertFixture("room.active.json", service.lookupRoom(ROOM));
        assertFixture("eligibility.granted.json", service.lookupEligibility(STAFF,SurgicalTeamRole.PRIMARY_SURGEON,START,END));
    }

    @Test
    void lookup_missingAndInactive_matchCanonicalNegativeFixtures() throws Exception {
        when(authority.findRoom(ROOM)).thenReturn(Optional.empty());
        when(staff.findById(STAFF)).thenReturn(Optional.empty());
        assertFixture("room.missing.json", service.lookupRoom(ROOM));
        assertFixture("eligibility.missing.json", service.lookupEligibility(STAFF,SurgicalTeamRole.PRIMARY_SURGEON,START,END));
        when(authority.findRoom(ROOM)).thenReturn(Optional.of(new OperatingRoom(ROOM,"OR-1","Room",DEPT,false,2,NOW)));
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        assertFixture("room.inactive.json", service.lookupRoom(ROOM));
    }

    @Test
    void lookup_repositoryFailure_isNotConvertedToAbsence() {
        when(authority.findRoom(ROOM)).thenThrow(new IllegalStateException("db unavailable"));
        assertThatThrownBy(() -> service.lookupRoom(ROOM)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decide_explicitAdminDecision_persistsThenAppendsAuditEvent() {
        when(staff.findById(STAFF)).thenReturn(Optional.of(doctor));
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        UUID actor = UUID.randomUUID();
        var result = service.decideCapability(STAFF,SurgicalTeamRole.PRIMARY_SURGEON,
                new SurgicalCapabilityRequest(0L,true,START,END,"Approval reference AUTH-001"),actor);
        assertThat(result.revision()).isEqualTo(1);
        var order = inOrder(authority,events);
        order.verify(authority).saveCapability(any(),eq(0L));
        ArgumentCaptor<SurgeryAuthorityChangedEvent> event = ArgumentCaptor.forClass(SurgeryAuthorityChangedEvent.class);
        order.verify(events).append(event.capture());
        assertThat(event.getValue().payload().actorAccountId()).isEqualTo(actor);
        assertThat(event.getValue().correlationId()).isEqualTo(correlations.currentOrCreate());
        assertThat(event.getValue().payload().revision()).isEqualTo(1);
        assertThat(event.getValue().version()).isEqualTo(1);
    }

    @Test
    void roomChange_actualProducerSerializationMatchesSurgeryEventFixture() throws Exception {
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        service.saveRoom(ROOM, new OperatingRoomRequest(1L, "OR-1", "Room", DEPT, false,
                "Room maintenance decision"), UUID.fromString("06000000-0000-0000-0000-000000000001"));
        assertEventFixture("event.room.changed.json");
    }

    @Test
    void capabilityChange_actualProducerSerializationMatchesSurgeryEventFixture() throws Exception {
        when(staff.findById(STAFF)).thenReturn(Optional.of(doctor));
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        service.decideCapability(STAFF, SurgicalTeamRole.PRIMARY_SURGEON,
                new SurgicalCapabilityRequest(1L, false, START, END, "Capability revocation decision"),
                UUID.fromString("06000000-0000-0000-0000-000000000001"));
        assertEventFixture("event.staff.changed.json");
    }

    private void assertEventFixture(String name) throws Exception {
        var captured = ArgumentCaptor.forClass(SurgeryAuthorityChangedEvent.class);
        verify(events).append(captured.capture());
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var expected = mapper.readTree(getClass().getResourceAsStream("/contracts/surgery-authority-v1/" + name));
        var actual = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(captured.getValue());
        assertThat(captured.getValue().eventId()).isNotNull();
        // Only the random event identity is normalized; all actual business/envelope fields must match.
        actual.set("eventId", expected.get("eventId"));
        assertThat(mapper.readTree(mapper.writeValueAsBytes(actual))).isEqualTo(expected);
    }

    @Test
    void decide_wrongJobTitleOrMissingAudit_rejectsWithoutMutation() {
        when(staff.findById(STAFF)).thenReturn(Optional.of(doctor));
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        assertThatThrownBy(() -> service.decideCapability(STAFF,SurgicalTeamRole.OR_NURSE,
                new SurgicalCapabilityRequest(0L,true,START,END,"Reviewed"),UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class).hasFieldOrPropertyWithValue("code","ORG_STAFF_INELIGIBLE");
        assertThatThrownBy(() -> service.decideCapability(STAFF,SurgicalTeamRole.PRIMARY_SURGEON,
                new SurgicalCapabilityRequest(0L,true,START,END," "),UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(authority,events);
    }

    @Test
    void save_revisionConflict_doesNotAppendEvent() {
        when(departments.findById(DEPT)).thenReturn(Optional.of(department));
        doThrow(new DuplicateResourceException("ORG_AUTHORITY_CONFLICT","stale"))
                .when(authority).saveRoom(any(),eq(1L));
        assertThatThrownBy(() -> service.saveRoom(ROOM,
                new OperatingRoomRequest(1L,"OR-1","Room",DEPT,true,"Reviewed"),UUID.randomUUID()))
                .isInstanceOf(DuplicateResourceException.class);
        verifyNoInteractions(events);
    }

    @Test
    void save_deactivateExistingRoom_afterDepartmentChangedType_isStillAudited() {
        Department changed = Department.reconstitute(DEPT, "Administration", "ADM",
                DepartmentType.ADMINISTRATIVE, null, "A", false, NOW, NOW);
        when(departments.findById(DEPT)).thenReturn(Optional.of(changed));
        var result = service.saveRoom(ROOM,
                new OperatingRoomRequest(2L,"OR-1","Room",DEPT,false,"Department retired"),
                UUID.randomUUID());
        assertThat(result.active()).isFalse();
        assertThat(result.revision()).isEqualTo(3);
        verify(authority).saveRoom(any(),eq(2L));
        verify(events).append(any());
    }

    @Test
    void save_nonclinicalDepartment_cannotCreateOrActivateRoom() {
        Department changed = Department.reconstitute(DEPT, "Administration", "ADM",
                DepartmentType.ADMINISTRATIVE, null, "A", false, NOW, NOW);
        when(departments.findById(DEPT)).thenReturn(Optional.of(changed));
        assertThatThrownBy(() -> service.saveRoom(ROOM,
                new OperatingRoomRequest(0L,"OR-1","Room",DEPT,false,"Create room"),UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code","ORG_SURGERY_DEPARTMENT_INELIGIBLE");
        assertThatThrownBy(() -> service.saveRoom(ROOM,
                new OperatingRoomRequest(2L,"OR-1","Room",DEPT,true,"Activate room"),UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code","ORG_SURGERY_DEPARTMENT_INELIGIBLE");
        verifyNoInteractions(authority,events);
    }

    private static void assertFixture(String name,Object dto) throws Exception {
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        try (var stream = SurgeryAuthorityServiceTest.class.getResourceAsStream("/contracts/surgery-authority-v1/"+name)) {
            assertThat(stream).isNotNull();
            assertThat((com.fasterxml.jackson.databind.JsonNode)mapper.valueToTree(dto)).isEqualTo(mapper.readTree(stream).get("data"));
        }
    }
}
