package com.mediflow.pharmacy.domain.model.enums;

public enum DispenseStatus {
  PENDING, DISPENSED, FAILED;

  
  @Deprecated
  public static final DispenseStatus FAIL = FAILED;
}
