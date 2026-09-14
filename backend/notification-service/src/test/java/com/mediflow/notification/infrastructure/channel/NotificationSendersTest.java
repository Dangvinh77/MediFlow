package com.mediflow.notification.infrastructure.channel;

import org.junit.jupiter.api.Test;

import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ba bộ gửi mock đều phải khai đúng kênh và luôn báo thành công (backend-spec/07-notification.md §12). */
class NotificationSendersTest {

    @Test
    void mockEmailSender_declaresEmailChannelAndAlwaysSucceeds() {
        MockEmailSender sender = new MockEmailSender();
        assertThat(sender.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(sender.send(pendingNotification(NotificationChannel.EMAIL, "a@example.com"))).isEmpty();
    }

    @Test
    void mockSmsSender_declaresSmsChannelAndAlwaysSucceeds() {
        MockSmsSender sender = new MockSmsSender();
        assertThat(sender.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(sender.send(pendingNotification(NotificationChannel.SMS, "0912345678"))).isEmpty();
    }

    @Test
    void inAppSender_declaresInAppChannelAndAlwaysSucceeds() {
        InAppSender sender = new InAppSender();
        assertThat(sender.channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(sender.send(pendingNotification(NotificationChannel.IN_APP, null))).isEmpty();
    }

    private Notification pendingNotification(NotificationChannel channel, String address) {
        return Notification.create(UUID.randomUUID(), "Tiêu đề", "Nội dung", channel, address);
    }
}
