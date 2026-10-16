package com.mediflow.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class OutpatientEvidenceFingerprintTest {
    @Test void rowAndFieldOrderingDoNotChangeWitnessButChangedBusinessAmountDoes(){
        var a=new HashMap<String,Object>(Map.of("evidence_key","CASH:1","amount",new BigDecimal("100.00")));
        var b=Map.<String,Object>of("evidence_key","REFUND:2","amount",new BigDecimal("20.00"));
        String original=OutpatientReportReadPersistenceAdapter.evidenceFingerprint(List.of(a,b));
        assertThat(OutpatientReportReadPersistenceAdapter.evidenceFingerprint(List.of(b,a))).isEqualTo(original);
        a.put("amount",new BigDecimal("101.00"));
        assertThat(OutpatientReportReadPersistenceAdapter.evidenceFingerprint(List.of(a,b))).isNotEqualTo(original);
    }
    @Test void receiptDeliveryDoesNotBecomeAnotherOperationWitness(){
        var a=Map.<String,Object>of("evidence_key","CASH:transaction-1","amount",new BigDecimal("100.0"));
        var b=Map.<String,Object>of("amount",new BigDecimal("100.00"),"evidence_key","CASH:transaction-1");
        assertThat(OutpatientReportReadPersistenceAdapter.evidenceFingerprint(List.of(a)))
                .isEqualTo(OutpatientReportReadPersistenceAdapter.evidenceFingerprint(List.of(b)));
    }
}
