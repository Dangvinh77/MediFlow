package com.mediflow.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.notification.domain.exception.NotificationAddressInvalidException;
import com.mediflow.notification.domain.exception.NotificationAlreadyFinalisedException;

class ThongBaoTest {

    // BR-N1 — kênh EMAIL cần địa chỉ hợp lệ
    @Test
    void taoMoi_emailChannelInvalidAddress_throwsBusinessRule() {
        assertThatThrownBy(() -> ThongBao.taoMoi(UUID.randomUUID(), "Tiêu đề", "Nội dung",
                LoaiThongBao.EMAIL, "khong-phai-email"))
                .isInstanceOf(NotificationAddressInvalidException.class);
    }

    // BR-N2 — kênh SMS cần số điện thoại hợp lệ (0 + 9-10 chữ số)
    @Test
    void taoMoi_smsChannelBadPhone_throwsBusinessRule() {
        assertThatThrownBy(() -> ThongBao.taoMoi(UUID.randomUUID(), "Tiêu đề", "Nội dung",
                LoaiThongBao.SMS, "123"))
                .isInstanceOf(NotificationAddressInvalidException.class);
    }

    @Test
    void taoMoi_validEmail_startsPending() {
        ThongBao tb = ThongBao.taoMoi(UUID.randomUUID(), "Tiêu đề", "Nội dung",
                LoaiThongBao.EMAIL, "benhnhan@example.com");

        assertThat(tb.getTrangThai()).isEqualTo(TrangThaiThongBao.PENDING);
        assertThat(tb.coTheGui()).isTrue();
    }

    @Test
    void taoMoi_inApp_doesNotRequireAddress() {
        ThongBao tb = ThongBao.taoMoi(UUID.randomUUID(), "Tiêu đề", "Nội dung", LoaiThongBao.IN_APP, null);

        assertThat(tb.getTrangThai()).isEqualTo(TrangThaiThongBao.PENDING);
    }

    // BR-N3/BR-N4 — đánh dấu kết quả gửi
    @Test
    void danhDauDaGui_marksSentAndStampsTime() {
        ThongBao tb = validInApp();
        Instant now = Instant.now();

        tb.danhDauDaGui(now);

        assertThat(tb.getTrangThai()).isEqualTo(TrangThaiThongBao.SENT);
        assertThat(tb.getNgayGui()).isEqualTo(now);
        assertThat(tb.getSoLanThu()).isEqualTo(1);
    }

    @Test
    void danhDauThatBai_marksFailedWithReason() {
        ThongBao tb = validInApp();

        tb.danhDauThatBai("SMTP timeout");

        assertThat(tb.getTrangThai()).isEqualTo(TrangThaiThongBao.FAILED);
        assertThat(tb.getLyDoThatBai()).isEqualTo("SMTP timeout");
    }

    // BR-N7 — thông báo đã kết thúc thì không gửi lại được
    @Test
    void danhDauDaGui_alreadySent_throwsAlreadyFinalised() {
        ThongBao tb = validInApp();
        tb.danhDauDaGui(Instant.now());

        assertThatThrownBy(() -> tb.danhDauDaGui(Instant.now()))
                .isInstanceOf(NotificationAlreadyFinalisedException.class);
    }

    @Test
    void danhDauThatBai_alreadyFailed_throwsAlreadyFinalised() {
        ThongBao tb = validInApp();
        tb.danhDauThatBai("lần đầu thất bại");

        assertThatThrownBy(() -> tb.danhDauThatBai("lần hai"))
                .isInstanceOf(NotificationAlreadyFinalisedException.class);
        assertThat(tb.coTheGui()).isFalse();
    }

    @Test
    void emailHopLe_validatesFormat() {
        assertThat(ThongBao.emailHopLe("a@b.com")).isTrue();
        assertThat(ThongBao.emailHopLe("khong-hop-le")).isFalse();
        assertThat(ThongBao.emailHopLe(null)).isFalse();
    }

    @Test
    void sdtHopLe_validatesVietnamesePhoneFormat() {
        assertThat(ThongBao.sdtHopLe("0912345678")).isTrue();
        assertThat(ThongBao.sdtHopLe("12345")).isFalse();
        assertThat(ThongBao.sdtHopLe(null)).isFalse();
    }

    private ThongBao validInApp() {
        return ThongBao.taoMoi(UUID.randomUUID(), "Tiêu đề", "Nội dung", LoaiThongBao.IN_APP, null);
    }
}
