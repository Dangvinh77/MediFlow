package com.mediflow.lab.domain.model;

/**
 * An orderable Lab service. The service code, the template's field codes and the Billing price code are
 * separate identities; Lab never derives a price from the service name.
 */
public record LabServiceCatalogItem(String serviceCode, String name, String displayGroup, String specimenType,
        String preparation, Integer turnaroundHours, String priceCode, String templateCode, boolean panel,
        boolean active) {
}
