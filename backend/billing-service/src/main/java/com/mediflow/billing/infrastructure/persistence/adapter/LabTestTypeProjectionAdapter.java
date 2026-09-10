package com.mediflow.billing.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.billing.application.port.out.LabTestTypePort;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.LabTestTypeJpaEntity;
import com.mediflow.billing.infrastructure.persistence.repository.LabTestTypeJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link LabTestTypePort} — tra {@code labType} từ bảng chiếu local {@code LAB_TEST_TYPE}.
 *
 * <p>Bảng được nạp bởi consumer {@code lab.request.created} (Phần 5/5) qua {@link #record(UUID, String)};
 * payload đó CÓ {@code labType} (04-lab.md §8), khác {@code lab.result.created}. Ở đây tuyệt đối
 * không gọi REST đồng bộ sang lab-service. Chưa có bản chiếu → {@link Optional#empty()} và
 * {@code FeeAccrualService} bỏ qua việc sinh phí LAB lần đó (không đặt giá mặc định).
 */
@Component
@RequiredArgsConstructor
public class LabTestTypeProjectionAdapter implements LabTestTypePort {

    private final LabTestTypeJpaRepository jpaRepo;

    @Override
    public Optional<String> labType(UUID labId) {
        return jpaRepo.findById(labId).map(LabTestTypeJpaEntity::getLabType);
    }

    /**
     * Ghi/đè bản chiếu {@code (labId -> labType)}. Dành cho consumer {@code lab.request.created}
     * (Phần 5/5); idempotent — nhận lại cùng {@code labId} chỉ cập nhật {@code labType}.
     */
    public void record(UUID labId, String labType) {
        LabTestTypeJpaEntity entity = jpaRepo.findById(labId)
                .orElseGet(() -> LabTestTypeJpaEntity.builder().labId(labId).build());
        entity.setLabType(labType);
        jpaRepo.save(entity);
    }
}
