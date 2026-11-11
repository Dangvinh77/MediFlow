package com.mediflow.lab.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.response.LabCriticalNoticeDTO;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.LabCriticalNotice;

/**
 * Critical value notices. Lab keeps no ordering doctor, so the responsible doctors are those of the
 * test's requesting department.
 */
@Service
public class LabCriticalNoticeService {
    private final LabCriticalNoticeRepositoryPort notices;
    private final CurrentReaderPort readers;
    private final Clock clock;
    private final boolean enabled;

    public LabCriticalNoticeService(LabCriticalNoticeRepositoryPort notices, CurrentReaderPort readers,
            @Qualifier("labClock") Clock clock, @Value("${mediflow.v2.lab-review:false}") boolean enabled) {
        this.notices = notices;
        this.readers = readers;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Transactional(readOnly = true)
    public List<LabCriticalNoticeDTO> list(String status) {
        requireEnabled();
        String wanted = status == null ? "OPEN" : status.strip().toUpperCase(Locale.ROOT);
        if (!wanted.equals("OPEN") && !wanted.equals("ACKNOWLEDGED")) {
            throw new LabRuleException("LAB_CRITICAL_STATUS_INVALID", "Trạng thái phải là OPEN hoặc ACKNOWLEDGED");
        }
        ReaderIdentity reader = reader();
        UUID department = "DOCTOR".equals(reader.role()) ? requiredDepartment(reader) : null;
        return notices.find(wanted, department).stream().map(LabCriticalNoticeDTO::of).toList();
    }

    @Transactional
    public LabCriticalNoticeDTO acknowledge(UUID testId, UUID noticeId) {
        requireEnabled();
        ReaderIdentity reader = reader();
        LabCriticalNotice notice = notices.findForUpdate(noticeId).filter(n -> n.getTestId().equals(testId))
                .orElseThrow(() -> new ResourceNotFoundException("LAB_CRITICAL_NOTICE_NOT_FOUND",
                        "Không tìm thấy cảnh báo giá trị nguy cấp"));
        if (!"DOCTOR".equals(reader.role()) || reader.staffId() == null
                || !notice.getDepartmentId().equals(reader.departmentId())) {
            throw new ForbiddenOperationException("LAB_CRITICAL_NOT_RESPONSIBLE",
                    "Chỉ bác sĩ của khoa chỉ định được xác nhận giá trị nguy cấp");
        }
        if (notice.acknowledge(reader.staffId(), clock.instant())) {
            notices.update(notice);
        }
        return LabCriticalNoticeDTO.of(notice);
    }

    private ReaderIdentity reader() {
        return readers.current().orElseThrow(() -> new ForbiddenOperationException("LAB_CRITICAL_NOT_RESPONSIBLE",
                "Không xác định được người dùng"));
    }

    private static UUID requiredDepartment(ReaderIdentity reader) {
        if (reader.departmentId() == null) {
            throw new ForbiddenOperationException("LAB_CRITICAL_NOT_RESPONSIBLE", "Bác sĩ chưa thuộc khoa nào");
        }
        return reader.departmentId();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResourceNotFoundException("LAB_REVIEW_DISABLED", "Quy trình duyệt kết quả chưa được bật");
        }
    }
}
