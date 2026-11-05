package com.mediflow.organization.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SecurityMailStoreAdapter implements SecurityMailStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 12;
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String encryptionKey;
    private final int contentRetentionDays;

    public SecurityMailStoreAdapter(@Qualifier("organizationDataSource") DataSource source,
            ObjectMapper mapper, @Value("${mediflow.security-mail.encryption-key:}") String encryptionKey,
            @Value("${mediflow.demo-mailbox.retention-days:30}") int contentRetentionDays) {
        this.jdbc = new JdbcTemplate(source);
        this.mapper = mapper;
        this.encryptionKey = encryptionKey;
        this.contentRetentionDays = Math.max(1, Math.min(contentRetentionDays, 90));
    }

    @Override
    public void lockOperation(String operationKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { rs.next(); return null; },
                "security-mail:" + operationKey);
    }

    @Override
    public MailDeliveryDTO enqueue(NewMessage message) {
        lockOperation(message.operationKey());
        var existing = byOperationKey(message.operationKey());
        if (existing.isPresent()) return existing.get();

        boolean demo = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM account a JOIN local_demo_seed_receipt r ON r.entity_id=a.account_id " +
                        "JOIN local_demo_mailbox_account d ON d.account_id=a.account_id AND d.seed_key=r.seed_key " +
                        "AND d.synthetic_email=? WHERE a.account_id=? AND a.role<>'SYSTEM' AND a.email_migrated=true)",
                Boolean.class, message.recipient(), message.accountId()));
        UUID id = UUID.randomUUID();
        byte[] encrypted = encrypt(new MailPayload(message.from(), message.recipient(), message.subject(),
                message.textBody(), message.htmlBody()));
        String status = demo ? "SENT" : "PENDING";
        jdbc.update("""
                INSERT INTO security_mail_message
                    (message_id,operation_key,account_id,sender_email,recipient_email,message_kind,subject,
                     encrypted_content,content_expires_at,demo_mailbox,status,attempts,next_attempt_at,created_at,sent_at)
                VALUES (?,?,?,?,?,?,?,?,now()+(? * interval '1 second'),?,?,0,now(),now(),CASE WHEN ? THEN now() ELSE NULL END)
                """, id, message.operationKey(), message.accountId(), message.from(), message.recipient(), message.kind(),
                message.subject(), encrypted, contentLifetimeSeconds(message.kind()), demo, status, demo);
        return new MailDeliveryDTO(id, demo ? MailDeliveryDTO.Status.SENT : MailDeliveryDTO.Status.PENDING);
    }

    @Override
    public Optional<MailDeliveryDTO> byOperationKey(String operationKey) {
        return jdbc.query("SELECT message_id,status FROM security_mail_message WHERE operation_key=?",
                (rs, index) -> new MailDeliveryDTO(rs.getObject("message_id", UUID.class), status(rs.getString("status"))),
                operationKey).stream().findFirst();
    }

    @Override
    public Optional<MailDeliveryDTO> latestForAccount(UUID accountId) {
        return jdbc.query("""
                SELECT message_id,status FROM security_mail_message WHERE account_id=?
                ORDER BY created_at DESC,message_id DESC LIMIT 1
                """, (rs, index) -> new MailDeliveryDTO(rs.getObject("message_id", UUID.class), status(rs.getString("status"))),
                accountId).stream().findFirst();
    }

    @Override
    public MailDeliveryDTO retry(UUID accountId, UUID messageId) {
        jdbc.update("""
                UPDATE security_mail_message SET status='PENDING',attempts=0,next_attempt_at=now(),
                    lease_until=NULL,last_failure_code=NULL
                WHERE account_id=? AND message_id=? AND status='FAILED' AND encrypted_content IS NOT NULL
                  AND content_expires_at>now()
                """, accountId, messageId);
        return jdbc.query("SELECT message_id,status FROM security_mail_message WHERE account_id=? AND message_id=?",
                (rs, index) -> new MailDeliveryDTO(rs.getObject("message_id", UUID.class), status(rs.getString("status"))),
                accountId, messageId).stream().findFirst()
                .orElseThrow(() -> new com.mediflow.common.exception.ResourceNotFoundException(
                        "MAIL_DELIVERY_NOT_FOUND", "Mail delivery record was not found"));
    }

    @Override
    public MailDeliveryDTO retry(UUID accountId, UUID messageId, String expectedRecipient) {
        jdbc.update("""
                UPDATE security_mail_message SET status='PENDING',attempts=0,next_attempt_at=now(),
                    lease_until=NULL,last_failure_code=NULL
                WHERE account_id=? AND message_id=? AND recipient_email=? AND status='FAILED'
                  AND encrypted_content IS NOT NULL AND content_expires_at>now()
                """, accountId, messageId, expectedRecipient);
        return jdbc.query("""
                SELECT message_id,status FROM security_mail_message
                WHERE account_id=? AND message_id=? AND recipient_email=?
                """, (rs, index) -> new MailDeliveryDTO(rs.getObject("message_id", UUID.class), status(rs.getString("status"))),
                accountId, messageId, expectedRecipient).stream().findFirst()
                .orElseThrow(() -> new com.mediflow.common.exception.ResourceNotFoundException(
                        "MAIL_DELIVERY_NOT_FOUND", "Mail delivery record was not found"));
    }

    @Override
    public List<QueuedMessage> claimDue(int limit) {
        jdbc.update("""
                UPDATE security_mail_message SET encrypted_content=NULL,
                    status=CASE WHEN status IN ('PENDING','SENDING') THEN 'FAILED' ELSE status END,
                    lease_until=NULL,last_failure_code=CASE WHEN status IN ('PENDING','SENDING') THEN 'CONTENT_EXPIRED' ELSE last_failure_code END
                WHERE encrypted_content IS NOT NULL AND content_expires_at<=now()
                """);
        var rows = jdbc.query("""
                WITH batch AS (
                    SELECT message_id FROM security_mail_message
                    WHERE ((status='PENDING' AND next_attempt_at <= now())
                        OR (status='SENDING' AND lease_until < now()))
                      AND (lease_until IS NULL OR lease_until < now())
                    ORDER BY created_at,message_id
                    FOR UPDATE SKIP LOCKED LIMIT ?
                )
                UPDATE security_mail_message m SET status='SENDING',attempts=m.attempts+1,
                    lease_until=now()+interval '90 seconds'
                FROM batch WHERE m.message_id=batch.message_id
                RETURNING m.message_id,m.demo_mailbox,m.encrypted_content
                """, (rs, index) -> new QueuedCiphertext(rs.getObject("message_id", UUID.class),
                rs.getBoolean("demo_mailbox"), rs.getBytes("encrypted_content")), limit);
        var available = new java.util.ArrayList<QueuedMessage>();
        for (var row : rows) {
            try {
                available.add(new QueuedMessage(row.id(), row.demo(), decrypt(row.bytes())));
            } catch (RuntimeException unusablePayload) {
                markFailed(row.id(), "SECURITY_MAIL_CONTENT_UNAVAILABLE");
            }
        }
        return List.copyOf(available);
    }

    @Override
    public void markSent(UUID messageId) {
        jdbc.update("""
                UPDATE security_mail_message SET status='SENT',sent_at=now(),lease_until=NULL,
                    encrypted_content=CASE WHEN demo_mailbox THEN encrypted_content ELSE NULL END,
                    last_failure_code=NULL
                WHERE message_id=?
                """, messageId);
    }

    @Override
    public void markFailed(UUID messageId, String failureCode) {
        jdbc.update("""
                UPDATE security_mail_message SET
                    status=CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at=now()+(least(300, greatest(10, attempts*10)) * interval '1 second'),
                    lease_until=NULL,last_failure_code=?
                WHERE message_id=? AND status='SENDING'
                """, MAX_ATTEMPTS, failureCode, messageId);
    }

    @Override
    public List<DemoAccount> demoAccounts() {
        return jdbc.query("""
                SELECT a.account_id,a.patient_id,a.role,coalesce(s.full_name,a.username) AS display_name,a.username AS email
                FROM account a JOIN local_demo_seed_receipt r ON r.entity_id=a.account_id
                JOIN local_demo_mailbox_account d ON d.account_id=a.account_id AND d.seed_key=r.seed_key
                LEFT JOIN staff s ON s.staff_id=a.staff_id
                WHERE a.role <> 'SYSTEM' AND a.email_migrated=true AND a.username=d.synthetic_email
                ORDER BY a.role,display_name,a.username
                LIMIT 500
                """, (rs, index) -> new DemoAccount(rs.getObject("account_id", UUID.class),
                rs.getObject("patient_id", UUID.class), rs.getString("role"), rs.getString("display_name"), rs.getString("email")));
    }

    @Override
    public com.mediflow.common.api.PageResult<DemoMessage> demoMessages(UUID accountId, com.mediflow.common.api.PageQuery page) {
        long total = jdbc.queryForObject("SELECT count(*) FROM security_mail_message m " +
                        "JOIN account a ON a.account_id=m.account_id " +
                        "JOIN local_demo_seed_receipt r ON r.entity_id=a.account_id " +
                        "JOIN local_demo_mailbox_account d ON d.account_id=a.account_id AND d.seed_key=r.seed_key " +
                        "AND d.synthetic_email=a.username WHERE m.account_id=? AND m.demo_mailbox=true",
                Long.class, accountId);
        var messages = jdbc.query("""
                SELECT m.message_id,m.subject,m.sender_email,m.recipient_email,m.created_at,
                       CASE WHEN m.content_expires_at<=now() THEN NULL ELSE m.encrypted_content END AS encrypted_content
                FROM security_mail_message m
                JOIN account a ON a.account_id=m.account_id
                JOIN local_demo_seed_receipt r ON r.entity_id=a.account_id
                JOIN local_demo_mailbox_account d ON d.account_id=a.account_id AND d.seed_key=r.seed_key AND d.synthetic_email=a.username
                WHERE m.account_id=? AND m.demo_mailbox=true
                ORDER BY m.created_at DESC,m.message_id DESC LIMIT ? OFFSET ?
                """, (rs, index) -> demoMessage(rs.getObject("message_id", UUID.class), rs.getString("subject"),
                rs.getString("sender_email"), rs.getString("recipient_email"), rs.getTimestamp("created_at").toInstant(),
                decryptOrExpired(rs.getBytes("encrypted_content"))),
                accountId, page.size(), (long) page.page() * page.size());
        return com.mediflow.common.api.PageResult.of(messages, total, page.page(), page.size());
    }

    @Override
    public Optional<DemoMessage> demoMessage(UUID accountId, UUID messageId) {
        return jdbc.query("""
                SELECT m.message_id,m.subject,m.sender_email,m.recipient_email,m.created_at,
                       CASE WHEN m.content_expires_at<=now() THEN NULL ELSE m.encrypted_content END AS encrypted_content
                FROM security_mail_message m
                JOIN account a ON a.account_id=m.account_id
                JOIN local_demo_seed_receipt r ON r.entity_id=a.account_id
                JOIN local_demo_mailbox_account d ON d.account_id=a.account_id AND d.seed_key=r.seed_key AND d.synthetic_email=a.username
                WHERE m.account_id=? AND m.message_id=? AND m.demo_mailbox=true
                """, (rs, index) -> demoMessage(rs.getObject("message_id", UUID.class), rs.getString("subject"),
                rs.getString("sender_email"), rs.getString("recipient_email"), rs.getTimestamp("created_at").toInstant(),
                decryptOrExpired(rs.getBytes("encrypted_content"))), accountId, messageId)
                .stream().findFirst();
    }

    @Override
    public int incrementRecoveryRequest(String emailHash, Instant windowStart) {
        return jdbc.queryForObject("""
                INSERT INTO security_mail_rate_limit(email_hash,window_start,request_count)
                VALUES (?,?,1)
                ON CONFLICT(email_hash,window_start) DO UPDATE
                    SET request_count=security_mail_rate_limit.request_count+1
                RETURNING request_count
                """, Integer.class, emailHash, Timestamp.from(windowStart));
    }

    private DemoMessage demoMessage(UUID id, String subject, String from, String to, Instant createdAt, MailContent content) {
        String preview = content.textBody().replaceAll("\\s+", " ").trim();
        if (preview.length() > 180) preview = preview.substring(0, 177) + "...";
        return new DemoMessage(id, subject, from, to, createdAt, preview,
                content.textBody(), content.htmlBody());
    }

    private MailContent decryptOrExpired(byte[] encrypted) {
        if (encrypted != null) return decrypt(encrypted);
        String text = "Nội dung email demo đã hết thời hạn lưu trữ.";
        String html = "<div style=\"font-family:Arial,sans-serif;padding:24px;color:#334155\">" +
                "<h2 style=\"color:#1d4ed8\">MediFlow</h2><p>Nội dung email demo đã hết thời hạn lưu trữ.</p></div>";
        return new MailContent("", "", "", text, html);
    }

    private byte[] encrypt(MailPayload payload) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] body = cipher.doFinal(mapper.writeValueAsBytes(payload));
            return ByteBuffer.allocate(nonce.length + body.length).put(nonce).put(body).array();
        } catch (Exception failure) {
            throw new IllegalStateException("Security mail encryption failed", failure);
        }
    }

    private MailContent decrypt(byte[] encrypted) {
        if (encrypted == null || encrypted.length <= NONCE_BYTES) throw new IllegalStateException("Mail content is unavailable");
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] body = new byte[buffer.remaining()];
            buffer.get(body);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            MailPayload payload = mapper.readValue(cipher.doFinal(body), MailPayload.class);
            return new MailContent(payload.from(), payload.recipient(), payload.subject(), payload.textBody(), payload.htmlBody());
        } catch (Exception failure) {
            throw new IllegalStateException("Security mail content could not be decrypted", failure);
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptionKey);
            if (decoded.length != 32) throw new IllegalArgumentException("Expected 32 bytes");
            return new SecretKeySpec(decoded, "AES");
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("MEDIFLOW_SECURITY_MAIL_KEY must be a Base64-encoded 32-byte key", invalid);
        }
    }

    private static MailDeliveryDTO.Status status(String value) {
        return "SENT".equals(value) ? MailDeliveryDTO.Status.SENT
                : "FAILED".equals(value) ? MailDeliveryDTO.Status.FAILED : MailDeliveryDTO.Status.PENDING;
    }

    private long contentLifetimeSeconds(String kind) {
        return switch (kind) {
            case "ACCOUNT_ISSUED", "ADMIN_RESET" -> 24L * 60 * 60;
            case "PASSWORD_RECOVERY" -> 30L * 60;
            default -> contentRetentionDays * 24L * 60 * 60;
        };
    }

    private record QueuedCiphertext(UUID id, boolean demo, byte[] bytes) { }
    private record MailPayload(String from, String recipient, String subject, String textBody, String htmlBody) { }
}
