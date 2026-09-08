package com.mediflow.billing.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.application.dto.response.RevenueByDeptDTO;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort.DepartmentRevenue;

/** RevenueMapper: projection tổng hợp → DTO (yêu cầu ở THELOC-INTEGRATION-FOLLOWUP.md mục 3). */
class RevenueMapperTest {

    private final RevenueMapper mapper = new RevenueMapperImpl();

    @Test
    void toDto_copiesDepartmentTotalAndCount() {
        UUID dept = UUID.randomUUID();
        RevenueByDeptDTO dto = mapper.toDto(new DepartmentRevenue(dept, new BigDecimal("1250000.00"), 7));

        assertThat(dto.departmentId()).isEqualTo(dept);
        assertThat(dto.totalRevenue()).isEqualByComparingTo("1250000.00");
        assertThat(dto.invoiceCount()).isEqualTo(7);
    }

    @Test
    void toDtoList_mapsEveryRow() {
        List<DepartmentRevenue> rows = List.of(
                new DepartmentRevenue(UUID.randomUUID(), new BigDecimal("100.00"), 1),
                new DepartmentRevenue(UUID.randomUUID(), new BigDecimal("200.00"), 2));

        assertThat(mapper.toDtoList(rows))
                .hasSize(2)
                .extracting(RevenueByDeptDTO::invoiceCount)
                .containsExactly(1L, 2L);
    }
}
