package com.mediflow.billing.domain.exception;

import java.util.UUID;

import com.mediflow.common.exception.ResourceNotFoundException;

public class BillingAccountNotFoundException extends ResourceNotFoundException {
    public BillingAccountNotFoundException(String message) {
        super("BILLING_ACCOUNT_NOT_FOUND", message);
    }
    public static BillingAccountNotFoundException byAccountId(UUID accountId) {
        return new BillingAccountNotFoundException("Billing account not found: " + accountId);
    }
    public static BillingAccountNotFoundException byAdmission(UUID admissionId) {
        return new BillingAccountNotFoundException("No billing account was ever opened for admission: " + admissionId);
    }
}
