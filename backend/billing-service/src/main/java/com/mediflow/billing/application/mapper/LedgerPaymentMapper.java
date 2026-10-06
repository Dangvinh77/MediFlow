package com.mediflow.billing.application.mapper;

import org.mapstruct.Mapper;
import com.mediflow.billing.application.dto.response.LedgerPaymentDTO;
import com.mediflow.billing.domain.model.PaymentTransaction;

@Mapper(componentModel = "spring")
public interface LedgerPaymentMapper {
    LedgerPaymentDTO toDTO(PaymentTransaction transaction);
}
