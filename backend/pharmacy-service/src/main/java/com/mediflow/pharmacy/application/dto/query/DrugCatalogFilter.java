package com.mediflow.pharmacy.application.dto.query;

/** Catalogue read filters; availability is on-hand stock, not a promise of unreserved stock. */
public enum DrugCatalogFilter {
    ALL, IN_STOCK, LOW_STOCK, OUT_OF_STOCK, EXPIRING_SOON, EXPIRED
}
