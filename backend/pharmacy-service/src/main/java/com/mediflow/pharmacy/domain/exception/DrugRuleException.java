package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

public class DrugRuleException extends BusinessRuleException {
  public DrugRuleException(String code, String message){
    super(code, message);
  }
}
