package com.mediflow.lab.application.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.request.LabSpecimenRequests.CollectSpecimenRequest;
import com.mediflow.lab.application.dto.request.LabSpecimenRequests.SpecimenDecisionRequest;
import com.mediflow.lab.application.dto.response.LabSpecimenDTO;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;
import com.mediflow.lab.domain.model.LabSpecimen;
import com.mediflow.lab.domain.model.LabSpecimenDecision;
import com.mediflow.lab.domain.model.LabTest;

/** Specimen collection and reception (review workflow). Never creates a test nor publishes an event. */
@Service
public class LabSpecimenApplicationService {
    private final LabTestRepositoryPort tests;
    private final LabSpecimenRepositoryPort specimens;
    private final AuthenticatedStaffContextPort staff;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public LabSpecimenApplicationService(LabTestRepositoryPort tests, LabSpecimenRepositoryPort specimens,
            AuthenticatedStaffContextPort staff, @Qualifier("labClock") Clock clock,
            @Value("${mediflow.v2.lab-review:false}") boolean enabled) {
        this.tests = tests;
        this.specimens = specimens;
        this.staff = staff;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Transactional(readOnly = true)
    public List<LabSpecimenDTO> forTest(UUID testId) {
        requireEnabled();
        test(testId);
        return specimens.forTest(testId).stream().map(LabSpecimenDTO::of).toList();
    }

    @Transactional
    public LabSpecimenDTO collect(UUID testId, CollectSpecimenRequest request) {
        requireEnabled();
        UUID actor = actor();
        LabTest primary = test(testId);
        List<UUID> testIds = new ArrayList<>(List.of(primary.getTestId()));
        if (request.additionalTestIds() != null) {
            for (UUID other : request.additionalTestIds()) {
                if (other == null || testIds.contains(other)) {
                    continue;
                }
                LabTest test = test(other);
                if (!test.getPatientId().equals(primary.getPatientId())) {
                    throw new LabRuleException("LAB_SPECIMEN_PATIENT_MISMATCH",
                            "Một mẫu chỉ dùng cho các xét nghiệm của cùng một bệnh nhân");
                }
                testIds.add(other);
            }
        }
        if (request.replacesSpecimenId() != null) {
            LabSpecimen replaced = specimens.findById(request.replacesSpecimenId())
                    .orElseThrow(() -> new ResourceNotFoundException("LAB_SPECIMEN_NOT_FOUND", "Không tìm thấy mẫu"));
            if (replaced.getDecision() != LabSpecimenDecision.REJECTED) {
                throw new LabRuleException("LAB_SPECIMEN_NOT_REJECTED", "Chỉ lấy lại mẫu cho mẫu đã bị từ chối");
            }
            if (!replaced.getPatientId().equals(primary.getPatientId())
                    || !replaced.getTestIds().contains(primary.getTestId())) {
                throw new LabRuleException("LAB_SPECIMEN_PATIENT_MISMATCH", "Mẫu lấy lại phải cho cùng xét nghiệm");
            }
        }
        LabSpecimen specimen = LabSpecimen.collect(primary.getPatientId(), request.specimenType(), testIds,
                request.replacesSpecimenId(), actor, clock.instant());
        specimens.insert(specimen);
        return LabSpecimenDTO.of(specimen);
    }

    @Transactional
    public LabSpecimenDTO decide(UUID specimenId, SpecimenDecisionRequest request) {
        requireEnabled();
        UUID actor = actor();
        LabSpecimen specimen = specimens.findByIdForUpdate(specimenId)
                .orElseThrow(() -> new ResourceNotFoundException("LAB_SPECIMEN_NOT_FOUND", "Không tìm thấy mẫu"));
        LabSpecimenDecision decision;
        try {
            decision = LabSpecimenDecision.valueOf(request.decision());
        } catch (RuntimeException invalid) {
            throw new LabRuleException("LAB_SPECIMEN_DECISION_INVALID", "Quyết định phải là ACCEPTED hoặc REJECTED");
        }
        specimen.decide(decision, request.reason(), actor, clock.instant());
        specimens.update(specimen);
        return LabSpecimenDTO.of(specimen);
    }

    private LabTest test(UUID testId) {
        LabTest test = tests.findById(testId).orElseThrow(() -> new LabTestNotFoundException(testId));
        if (test.getCareContractVersion() != 1) {
            throw new LabRuleException("LAB_SPECIMEN_TEST_UNSUPPORTED", "Chỉ quản lý mẫu cho xét nghiệm hợp đồng v1");
        }
        return test;
    }

    private UUID actor() {
        return staff.currentStaff().map(AuthenticatedStaffContext::staffId)
                .orElseThrow(() -> new LabRuleException("LAB_STAFF_REQUIRED", "Người thao tác phải là nhân viên đã xác thực"));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResourceNotFoundException("LAB_REVIEW_DISABLED", "Quy trình duyệt kết quả chưa được bật");
        }
    }
}
