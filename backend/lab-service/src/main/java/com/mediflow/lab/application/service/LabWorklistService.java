package com.mediflow.lab.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.response.LabWorklistItemDTO;
import com.mediflow.lab.application.port.out.LabWorklistPort;

/** LAB-WF-01 worklist of the review workflow. */
@Service
public class LabWorklistService {
    private final LabWorklistPort worklist;
    private final Clock clock;
    private final boolean enabled;

    public LabWorklistService(LabWorklistPort worklist, @Qualifier("labClock") Clock clock,
            @Value("${mediflow.v2.lab-review:false}") boolean enabled) {
        this.worklist = worklist;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Transactional(readOnly = true)
    public List<LabWorklistItemDTO> worklist() {
        if (!enabled) {
            throw new ResourceNotFoundException("LAB_REVIEW_DISABLED", "Quy trình duyệt kết quả chưa được bật");
        }
        Instant now = clock.instant();
        return worklist.open().stream().map(r -> new LabWorklistItemDTO(r.testId(), r.patientId(), r.labType(),
                r.testStatus(), r.serviceCode(), r.serviceName(), r.displayGroup(), r.specimenStatus(),
                r.reportStatus(), r.reportVersion(), r.dueAt(), r.dueAt() != null && r.dueAt().isBefore(now)))
                .toList();
    }
}
