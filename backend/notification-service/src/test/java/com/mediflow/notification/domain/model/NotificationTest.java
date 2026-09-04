package com.mediflow.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.notification.domain.exception.NotificationAddressInvalidException;
import com.mediflow.notification.domain.exception.NotificationAlreadyFinalisedException;

class NotificationTest {

    // BR-N1 — kênh EMAIL cần địa chỉ hợp lệ
    @Test
    void create_emailChannelInvalidAddress_throwsBusinessRule() {
        assertThatThrownBy(() -> Notification.create(UUID.randomUUID(), "Tiêu đề", "Nội dung",
                NotificationChannel.EMAIL, "khong-phai-email"))
                .isInstanceOf(NotificationAddressInvalidException.class);
    }

    // BR-N2 — kênh SMS cần số điện thoại hợp lệ (0 + 9-10 chữ số)
    @Test
    void create_smsChannelBadPhone_throwsBusinessRule() {
        assertThatThrownBy(() -> Notification.create(UUID.randomUUID(), "Tiêu đề", "Nội dung",
                NotificationChannel.SMS, "123"))
                .isInstanceOf(NotificationAddressInvalidException.class);
    }

    @Test
    void create_validEmail_startsPending() {
        Notification n = Notification.create(UUID.randomUUID(), "Tiêu đề", "Nội dung",
                NotificationChannel.EMAIL, "benhnhan@example.com");

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.canSend()).isTrue();
    }

    @Test
    void create_inApp_doesNotRequireAddress() {
        Notification n = Notification.create(UUID.randomUUID(), "Tiêu đề", "Nội dung",
                NotificationChannel.IN_APP, null);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    // BR-N3/BR-N4 — đánh dấu kết quả gửi
    @Test
    void markSent_marksSentAndStampsTime() {
        Notification n = validInApp();
        Instant now = Instant.now();

        n.markSent(now);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSentAt()).isEqualTo(now);
        assertThat(n.getRetryCount()).isEqualTo(1);
    }

    @Test
    void markFailed_marksFailedWithReason() {
        Notification n = validInApp();

        n.markFailed("SMTP timeout");

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getFailureReason()).isEqualTo("SMTP timeout");
    }

    // BR-N7 — thông báo đã kết thúc thì không gửi lại được
    @Test
    void markSent_alreadySent_throwsAlreadyFinalised() {
        Notification n = validInApp();
        n.markSent(Instant.now());

        assertThatThrownBy(() -> n.markSent(Instant.now()))
                .isInstanceOf(NotificationAlreadyFinalisedException.class);
    }

    @Test
    void markFailed_alreadyFailed_throwsAlreadyFinalised() {
        Notification n = validInApp();
        n.markFailed("lần đầu thất bại");

        assertThatThrownBy(() -> n.markFailed("lần hai"))
                .isInstanceOf(NotificationAlreadyFinalisedException.class);
        assertThat(n.canSend()).isFalse();
    }

    @Test
    void isValidEmail_validatesFormat() {
        assertThat(Notification.isValidEmail("a@b.com")).isTrue();
        assertThat(Notification.isValidEmail("khong-hop-le")).isFalse();
        assertThat(Notification.isValidEmail(null)).isFalse();
    }

    @Test
    void isValidPhone_validatesVietnamesePhoneFormat() {
        assertThat(Notification.isValidPhone("0912345678")).isTrue();
        assertThat(Notification.isValidPhone("12345")).isFalse();
        assertThat(Notification.isValidPhone(null)).isFalse();
    }

    private Notification validInApp() {
        return Notification.create(UUID.randomUUID(), "Tiêu đề", "Nội dung", NotificationChannel.IN_APP, null);
    }
}
