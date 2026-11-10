package com.mediflow.clinical.application.dto.request;

/** Why the doctor takes back an order Lab never received. */
public record WithdrawLabOrderRequest(String reason) {
}
