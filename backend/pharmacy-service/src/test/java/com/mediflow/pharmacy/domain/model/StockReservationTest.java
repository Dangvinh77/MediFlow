package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

/** Quy tắc vòng đời của một dòng giữ chỗ tồn kho (domain — thuần Java, không Spring). */
class StockReservationTest {

    private static final UUID DRUG = UUID.randomUUID();
    private static final UUID RX = UUID.randomUUID();
    private static final Instant EXPIRY = Instant.now().plusSeconds(3600);

    @Test
    void create_reservation_isReserved() {
        StockReservation r = StockReservation.create(DRUG, RX, 5, EXPIRY);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(r.isReserved()).isTrue();
        assertThat(r.getQuantity()).isEqualTo(5);
        assertThat(r.getExpiresAt()).isEqualTo(EXPIRY);
    }

    @Test
    void create_quantityZeroOrNegative_throws() {
        assertThatThrownBy(() -> StockReservation.create(DRUG, RX, 0, EXPIRY))
                .isInstanceOf(StockReservationRuleException.class)
                .hasMessageContaining("Số lượng giữ chỗ");
        assertThatThrownBy(() -> StockReservation.create(DRUG, RX, -1, EXPIRY))
                .isInstanceOf(StockReservationRuleException.class);
    }

    @Test
    void create_missingExpiry_throws() {
        assertThatThrownBy(() -> StockReservation.create(DRUG, RX, 3, null))
                .isInstanceOf(StockReservationRuleException.class)
                .hasMessageContaining("phải có hạn hết hiệu lực");
    }

    @Test
    void markFulfilled_reservedToFulfilled() {
        StockReservation r = StockReservation.create(DRUG, RX, 5, EXPIRY);
        r.markFulfilled();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(r.isReserved()).isFalse();
    }

    @Test
    void release_reservedToReleased() {
        StockReservation r = StockReservation.create(DRUG, RX, 5, EXPIRY);
        r.release();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void expire_reservedToExpired() {
        StockReservation r = StockReservation.create(DRUG, RX, 5, EXPIRY);
        r.expire();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void transitionFromFulfilled_throws() {
        StockReservation r = StockReservation.create(DRUG, RX, 5, EXPIRY);
        r.markFulfilled();
        assertThatThrownBy(r::release).isInstanceOf(StockReservationRuleException.class)
                .hasMessageContaining("không còn ở trạng thái RESERVED");
        assertThatThrownBy(r::expire).isInstanceOf(StockReservationRuleException.class);
        assertThatThrownBy(r::markFulfilled).isInstanceOf(StockReservationRuleException.class);
    }
}
