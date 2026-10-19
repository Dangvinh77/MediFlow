package com.mediflow.notification.infrastructure.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.dto.command.CarePaymentReceiptCommand;
import com.mediflow.notification.messaging.consumer.CarePaymentReceiptWireHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Offline Notification-store-only import. No Spring context, listener or business effect is started. */
public final class CareReceiptByteIdentityBackfill {
    private CareReceiptByteIdentityBackfill() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || !args[0].startsWith("--archive-dir=")
                || args.length == 2 && !"--apply".equals(args[1]))
            throw new IllegalArgumentException("Usage: --archive-dir=<original-body-directory> [--apply]");
        var directory = Path.of(args[0].substring("--archive-dir=".length()));
        var bodies = new ArrayList<byte[]>();
        try (var files = Files.list(directory)) {
            for (var file : files.sorted().toList()) {
                if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".json"))
                    throw new IllegalArgumentException("Archive must contain only original receipt .json files");
                if (Files.size(file) > 1_048_576) throw new IllegalArgumentException("Archive body exceeds receipt limit");
                bodies.add(Files.readAllBytes(file));
            }
        }
        try (var connection = DriverManager.getConnection(requiredEnv("MEDIFLOW_NOTIFICATION_DB_URL"),
                requiredEnv("MEDIFLOW_NOTIFICATION_DB_USER"), requiredEnv("MEDIFLOW_NOTIFICATION_DB_PASSWORD"))) {
            int count = backfill(connection, bodies, args.length == 2);
            System.out.println((args.length == 2 ? "Applied" : "Dry-run verified") + " receipt identities: " + count);
        }
    }

    /** Requires a dedicated connection closed by the caller; leaves it in manual-commit mode. */
    public static int backfill(Connection connection, List<byte[]> originalBodies, boolean apply) throws SQLException {
        var commands = new ArrayList<CarePaymentReceiptCommand>();
        var decoder = new CarePaymentReceiptWireHandler(new ObjectMapper(), commands::add, true);
        for (byte[] body : originalBodies) decoder.receive("payment.completed", body);
        Map<UUID, CarePaymentReceiptCommand> archive = new HashMap<>();
        for (var command : commands)
            if (archive.putIfAbsent(command.eventId(), command) != null)
                throw new IllegalArgumentException("Archive contains duplicate event identities");
        if (!connection.getAutoCommit()) throw new IllegalArgumentException("A dedicated auto-commit connection is required");
        connection.setAutoCommit(false);
        try {
            // Excludes all concurrent processed-event writers while checking coverage and importing.
            try (var statement = connection.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '10s'");
                statement.execute("LOCK TABLE PROCESSED_EVENT IN SHARE ROW EXCLUSIVE MODE");
            }
            var pending = new ArrayList<CarePaymentReceiptCommand>();
            var seen = new HashSet<UUID>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                    SELECT event_id,payload_hash,receipt_raw_hash FROM PROCESSED_EVENT
                    WHERE routing_key='payment.completed' AND payload_hash IS NOT NULL
                    """)) {
                while (rows.next()) {
                    UUID eventId = rows.getObject("event_id", UUID.class);
                    String raw = rows.getString("receipt_raw_hash");
                    var command = archive.get(eventId);
                    if (command == null) {
                        if (raw == null) throw new IllegalArgumentException("Archive does not cover all historical receipts");
                        continue;
                    }
                    seen.add(eventId);
                    if (!command.legacyFingerprint().equals(rows.getString("payload_hash"))
                            || raw != null && !command.fingerprint().equals(raw))
                        throw new IllegalArgumentException("Archive conflicts with persisted receipt evidence");
                    if (raw == null) pending.add(command);
                }
            }
            if (seen.size() != archive.size())
                throw new IllegalArgumentException("Archive includes unknown or non-V1 receipt identities");
            if (apply) {
                try (var update = connection.prepareStatement("""
                        UPDATE PROCESSED_EVENT SET receipt_raw_hash=?
                        WHERE event_id=? AND payload_hash=? AND receipt_raw_hash IS NULL
                        """); var audit = connection.prepareStatement("""
                        INSERT INTO care_receipt_byte_backfill(event_id,legacy_hash,raw_hash) VALUES (?,?,?)
                        """)) {
                    for (var command : pending) {
                        update.setString(1, command.fingerprint()); update.setObject(2, command.eventId());
                        update.setString(3, command.legacyFingerprint());
                        if (update.executeUpdate() != 1) throw new SQLException("Receipt identity changed during import");
                        audit.setObject(1, command.eventId()); audit.setString(2, command.legacyFingerprint());
                        audit.setString(3, command.fingerprint()); audit.executeUpdate();
                    }
                }
                connection.commit();
            } else connection.rollback();
            return pending.size();
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }
}
