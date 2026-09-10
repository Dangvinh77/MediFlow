package com.mediflow.billing.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.application.dto.response.FeeDTO;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;

/**
 * MapStruct mapper là code sinh tự động, nhưng {@code isPaid} là điểm dễ sai: Lombok sinh getter
 * {@code isPaid()} nên MapStruct không tự nhận ra nếu không chỉ định {@code source = "paid"} —
 * khi đó DTO luôn trả {@code false}. Test này khóa đúng hành vi mong muốn.
 */
class FeeDtoMapperTest {

    private final FeeDtoMapper mapper = new FeeDtoMapperImpl();

    @Test
    void toDto_copiesEveryClientFacingField() {
        UUID deptId = UUID.randomUUID();
        Fee fee = Fee.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), deptId,
                UUID.randomUUID(), FeeType.LAB, LocalDate.of(2026, 9, 1),
                new BigDecimal("120000.00"), true, UUID.randomUUID(),
                Instant.parse("2026-09-01T08:00:00Z"), null);

        FeeDTO dto = mapper.toDto(fee);

        assertThat(dto.feeId()).isEqualTo(fee.getFeeId());
        assertThat(dto.feeType()).isEqualTo(FeeType.LAB);
        assertThat(dto.departmentId()).isEqualTo(deptId);
        assertThat(dto.incurredDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(dto.amount()).isEqualByComparingTo("120000.00");
        assertThat(dto.isPaid()).isTrue();
    }

    @Test
    void toDto_unpaidFee_keepsIsPaidFalse() {
        Fee fee = Fee.create(UUID.randomUUID(), null, UUID.randomUUID(), null,
                FeeType.EXAM, LocalDate.now(), new BigDecimal("50000.00"));

        assertThat(mapper.toDto(fee).isPaid()).isFalse();
    }

    @Test
    void toDtoList_mapsEachElement() {
        Fee a = Fee.create(UUID.randomUUID(), null, UUID.randomUUID(), null,
                FeeType.EXAM, LocalDate.now(), new BigDecimal("50000.00"));
        Fee b = Fee.create(UUID.randomUUID(), null, UUID.randomUUID(), null,
                FeeType.SERVICE, LocalDate.now(), new BigDecimal("75000.00"));

        List<FeeDTO> dtos = mapper.toDtoList(List.of(a, b));

        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(FeeDTO::feeType)
                .containsExactly(FeeType.EXAM, FeeType.SERVICE);
    }
}
