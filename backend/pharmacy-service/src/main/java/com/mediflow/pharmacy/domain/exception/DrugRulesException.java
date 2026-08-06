package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.BusinessRuleException;

public class DrugRulesException extends BusinessRuleException {
  public DrugRulesException(String code, String message){
    super(code, message);
  }
}
