package com.mediflow.billing.application.port.out;

/** Billing approves bundle codes; Pharmacy alone supplies the server-captured drug prices. */
public interface PrescriptionPricePolicyPort {
    void requireAllowed(String priceCode);
}
