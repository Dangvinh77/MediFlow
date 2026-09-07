package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.pharmacy.domain.exception.PrescriptionCancellationForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void forbiddenOperation_returns403AndStableErrorCode() {
        PrescriptionCancellationForbiddenException exception =
                new PrescriptionCancellationForbiddenException(
                        "Bác sĩ chỉ được hủy đơn do chính mình kê");

        ResponseEntity<ApiResponse<Void>> response =
                handler.cancellationForbidden(exception);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().success())
                .isFalse();

        assertThat(response.getBody().error().code())
                .isEqualTo(
                        PrescriptionCancellationForbiddenException.CODE);

        assertThat(response.getBody().error().message())
                .isEqualTo(
                        "Bác sĩ chỉ được hủy đơn do chính mình kê");
    }
}
