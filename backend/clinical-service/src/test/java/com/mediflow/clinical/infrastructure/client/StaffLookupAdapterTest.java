package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.common.api.ApiResponse;

class StaffLookupAdapterTest {

    private final OrganizationFeignClient client = mock(OrganizationFeignClient.class);
    private final StaffLookupAdapter adapter = new StaffLookupAdapter(client);

    @Test
    void departmentOf_existingStaff_returnsDepartment() {
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(ApiResponse.ok(new StaffExistsResponse(true, departmentId)));
        assertThat(adapter.departmentOf(staffId)).contains(departmentId);
    }

    @Test
    void departmentOf_confirmedMissingStaff_returnsEmpty() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(ApiResponse.ok(new StaffExistsResponse(false, null)));
        assertThat(adapter.departmentOf(staffId)).isEmpty();
    }

    @Test
    void departmentOf_invalidEnvelope_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(null);
        assertThatThrownBy(() -> adapter.departmentOf(staffId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("invalid response");
    }

    @Test
    void departmentOf_existingStaffWithoutDepartment_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(ApiResponse.ok(new StaffExistsResponse(true, null)));
        assertThatThrownBy(() -> adapter.departmentOf(staffId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("invalid response");
    }
}
