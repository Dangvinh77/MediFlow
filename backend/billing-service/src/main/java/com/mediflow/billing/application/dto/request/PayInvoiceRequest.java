package com.mediflow.billing.application.dto.request;

import com.mediflow.billing.domain.model.PaymentMethod;

import jakarta.validation.constraints.NotNull;

/**
 * Request thanh toán một hóa đơn (backend-spec/06-billing.md §8).
 * Id hóa đơn nằm trên đường dẫn URL, không nằm trong body.
 *
 * @param paymentMethod hình thức thanh toán: {@code CASH | TRANSFER | INSURANCE}
 */
public record PayInvoiceRequest(
        @NotNull PaymentMethod paymentMethod
) {}
