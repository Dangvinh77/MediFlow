package com.mediflow.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;

/**
 * Slice test cho {@link NotificationPersistenceAdapter}: lưu được cả bản ghi {@code PENDING} lẫn
 * bản ghi đã kết thúc (BR-N3/BR-N4), cập nhật giữ nguyên {@code created_at}, và phân trang mới
 * nhất trước. Flyway chạy {@code V1__init.sql} trên Postgres container thật.
 */
@DataJpaTest
@Import(NotificationPersistenceAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class NotificationPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificationPersistenceAdapter adapter;

    @Test
    void save_pendingThenFinalised_preservesCreatedAtAndPersistsOutcome() {
        Notification pending = adapter.save(Notification.create(
                UUID.randomUUID(), "Nhắc lịch khám", "Bạn có lịch khám ngày 10/09.",
                NotificationChannel.EMAIL, "an.nguyen@example.com"));

        assertThat(pending.getNotificationId()).isNotNull();
        assertThat(pending.getCreatedAt()).isNotNull();
        assertThat(pending.getStatus()).isEqualTo(NotificationStatus.PENDING);

        pending.markSent(Instant.parse("2026-09-08T03:00:00Z"));
        Notification sent = adapter.save(pending);

        assertThat(sent.getCreatedAt()).isEqualTo(pending.getCreatedAt());
        Notification reloaded = adapter.findById(sent.getNotificationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(reloaded.getSentAt()).isEqualTo(Instant.parse("2026-09-08T03:00:00Z"));
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getRecipientAddress()).isEqualTo("an.nguyen@example.com");
    }

    @Test
    void save_failedRecordWithReason_isPersisted() {
        Notification n = Notification.create(UUID.randomUUID(), "Kết quả xét nghiệm đã có",
                "Kết quả của bạn đã sẵn sàng.", NotificationChannel.IN_APP, null);
        n.markFailed("kênh IN_APP tạm gián đoạn");

        Notification saved = adapter.save(n);

        Notification reloaded = adapter.findById(saved.getNotificationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(reloaded.getFailureReason()).isEqualTo("kênh IN_APP tạm gián đoạn");
    }

    @Test
    void findByPatient_returnsNewestFirstWithPagingMetadata() {
        UUID patient = UUID.randomUUID();
        Notification a = adapter.save(Notification.create(patient, "A", "A", NotificationChannel.IN_APP, null));
        sleepABit();
        Notification b = adapter.save(Notification.create(patient, "B", "B", NotificationChannel.IN_APP, null));
        sleepABit();
        Notification c = adapter.save(Notification.create(patient, "C", "C", NotificationChannel.IN_APP, null));
        adapter.save(Notification.create(UUID.randomUUID(), "other", "other", NotificationChannel.IN_APP, null));

        PageResult<Notification> page = adapter.findByPatient(patient, new PageQuery(0, 2));

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).extracting(Notification::getNotificationId)
                .containsExactly(c.getNotificationId(), b.getNotificationId());
        assertThat(page.content()).extracting(Notification::getNotificationId)
                .doesNotContain(a.getNotificationId());
    }

    private static void sleepABit() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
