package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest.SourceProof;
import com.mediflow.report.application.exception.*;
import com.mediflow.report.application.port.out.*;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort.*;
import com.mediflow.report.domain.model.OutpatientCoverage;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutpatientCoverageAcceptanceServiceTest {
    private final OutpatientCoverageEvidencePort evidence=mock(OutpatientCoverageEvidencePort.class);
    private final OutpatientCoverageAcceptancePort store=mock(OutpatientCoverageAcceptancePort.class);
    private final LocalDate date=LocalDate.of(2026,10,1);
    private final Instant now=Instant.parse("2026-10-03T00:00:00Z");
    private final UUID actor=UUID.randomUUID();
    private final OutpatientCoverageAcceptanceService service=new OutpatientCoverageAcceptanceService(evidence,store,
            ZoneId.of("Asia/Bangkok"),Clock.fixed(now,ZoneOffset.UTC));

    private AcceptOutpatientCoverageRequest request(Map<String,SourceProof> sources,String zone,Instant observed,Instant reconciled) {
        return new AcceptOutpatientCoverageRequest(UUID.randomUUID(),date,date,null,zone,"VND",sources,
                observed,reconciled,"REVIEW-TEST-ONLY","a".repeat(64));
    }
    private Map<String,SourceProof> proofs() {
        var result=new HashMap<String,SourceProof>();
        OutpatientCoverage.REQUIRED_SOURCES.forEach(s->result.put(s,new SourceProof("TEST-EXPORT:"+s,"b".repeat(64),0,"c".repeat(64))));
        return result;
    }
    private Snapshot snapshot(boolean invalid) {
        var sources=new HashMap<String,SourceWitness>();
        OutpatientCoverage.REQUIRED_SOURCES.forEach(s->sources.put(s,new SourceWitness(0,"c".repeat(64))));
        return new Snapshot("a".repeat(64),invalid,sources);
    }
    private AcceptOutpatientCoverageRequest valid() { return request(proofs(),"Asia/Bangkok",now.minusSeconds(60),now.minusSeconds(30)); }
    @Test void signedActorServerTimeAndAllReviewedExportsPersistTogether() {
        var request=valid(); when(evidence.inspect(date,date,null,"Asia/Bangkok","VND")).thenReturn(snapshot(false));
        var coverage=service.accept(request,actor);
        assertThat(coverage.acceptedBy()).isEqualTo(actor); assertThat(coverage.acceptedAt()).isEqualTo(now);
        assertThat(coverage.sourceReferences()).hasSize(5);
        var ordered=inOrder(store,evidence); ordered.verify(store).lock(); ordered.verify(store).find(request.requestId());
        ordered.verify(evidence).inspect(date,date,null,"Asia/Bangkok","VND"); ordered.verify(store).save(request,actor,coverage.toModel());
    }
    @Test void exactRetryReturnsOriginalReceiptWithoutReacceptingDriftedInputs() {
        var request=valid(); when(evidence.inspect(any(),any(),any(),any(),any())).thenReturn(snapshot(false));
        var original=service.accept(request,actor); clearInvocations(evidence,store);
        when(store.find(request.requestId())).thenReturn(Optional.of(new OutpatientCoverageAcceptancePort.Saved(request,actor,original.toModel())));
        assertThat(service.accept(request,actor)).isEqualTo(original); verifyNoInteractions(evidence);
        verify(store,never()).save(any(),any(),any());
        assertThatThrownBy(()->service.accept(request,UUID.randomUUID())).isInstanceOf(OutpatientCoverageConflictException.class);
    }
    @Test void changedRequestWithSameIdCannotOverwriteAcceptance() {
        var request=valid(); when(store.find(request.requestId())).thenReturn(Optional.of(
                new OutpatientCoverageAcceptancePort.Saved(valid(),actor,null)));
        assertThatThrownBy(()->service.accept(request,actor)).isInstanceOf(OutpatientCoverageConflictException.class);
        verifyNoInteractions(evidence); verify(store,never()).save(any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"CLINICAL","LAB","PHARMACY","BILLING_CASH","BILLING_REFUND"})
    void missingSourceNeverEstablishesZeroCoverage(String missing) {
        var proofs=proofs(); proofs.remove(missing);
        assertThatThrownBy(()->service.accept(request(proofs,"Asia/Bangkok",now,now),actor)).isInstanceOf(ReportPeriodValidationException.class);
        verifyNoInteractions(store,evidence);
    }
    @Test void wrongZoneFutureReconciliationAndIncompleteFinalDayAreDenied() {
        assertThatThrownBy(()->service.accept(request(proofs(),"UTC",now,now),actor)).isInstanceOf(ReportPeriodValidationException.class);
        assertThatThrownBy(()->service.accept(request(proofs(),"Asia/Bangkok",now,now.plusSeconds(1)),actor)).isInstanceOf(ReportPeriodValidationException.class);
        Instant incomplete=Instant.parse("2026-10-01T16:59:59Z");
        assertThatThrownBy(()->service.accept(request(proofs(),"Asia/Bangkok",incomplete,incomplete),actor)).isInstanceOf(ReportPeriodValidationException.class);
        verifyNoInteractions(store,evidence);
    }
    @Test void pendingRefundAndStalePreviewAndWrongSourceCountCannotBeAccepted() {
        when(evidence.inspect(any(),any(),any(),any(),any())).thenReturn(snapshot(true));
        assertThatThrownBy(()->service.accept(valid(),actor)).isInstanceOf(OutpatientCoverageConflictException.class);
        when(evidence.inspect(any(),any(),any(),any(),any())).thenReturn(new Snapshot("d".repeat(64),false,snapshot(false).sources()));
        assertThatThrownBy(()->service.accept(valid(),actor)).isInstanceOf(OutpatientCoverageConflictException.class);
        var proofs=proofs(); proofs.put("LAB",new SourceProof("TEST-EXPORT:LAB","b".repeat(64),1,"c".repeat(64)));
        when(evidence.inspect(any(),any(),any(),any(),any())).thenReturn(snapshot(false));
        assertThatThrownBy(()->service.accept(request(proofs,"Asia/Bangkok",now,now),actor)).isInstanceOf(OutpatientCoverageConflictException.class);
        verify(store,never()).save(any(),any(),any());
    }
    @Test void invalidExportDigestAndInvalidPeriodDoNotTouchPersistence() {
        var proofs=proofs(); proofs.put("LAB",new SourceProof("ref","not-a-sha256",0,"c".repeat(64)));
        assertThatThrownBy(()->service.accept(request(proofs,"Asia/Bangkok",now,now),actor)).isInstanceOf(ReportPeriodValidationException.class);
        assertThatThrownBy(()->service.preview(date,date.plusDays(366),null)).isInstanceOf(ReportPeriodValidationException.class);
        verifyNoInteractions(store,evidence);
    }
}
