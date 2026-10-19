package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.service.CarePaymentReceiptService;
import com.mediflow.notification.infrastructure.persistence.adapter.CareNotificationRepositoryAdapter;
import com.mediflow.notification.messaging.consumer.CarePaymentReceiptWireHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the operator command from the just-packaged executable jar against an upgraded V4 store. */
@Testcontainers
class CareReceiptByteIdentityBackfillCliIT {
    private static final String MAIN_CLASS =
            "com.mediflow.notification.infrastructure.recovery.CareReceiptByteIdentityBackfill";
    private static final String BOOT_PROPERTIES_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private JdbcTemplate jdbc;
    private byte[] originalBody;

    @TempDir
    Path tempDir;

    @BeforeEach
    void seedV4ReceiptAndUpgradeToV5() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .target("4")
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("4")
                .load()
                .migrate();

        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        originalBody = Files.readAllBytes(Path.of(System.getProperty("notification.billing.fixture")));
        seedLegacyV4Receipt(source, originalBody);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
    }

    @Test
    void run_dryRunWithVerifiedArchive_receiptAndEffectsUnchanged() throws Exception {
        Path archive = archiveDirectory(originalBody);
        StoreSnapshot before = snapshot();

        CliResult result = runCli(archive, false);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("Dry-run verified receipt identities: 1");
        assertThat(receiptRawHash()).isNull();
        assertThat(backfillCount()).isZero();
        assertBusinessStateUnchanged(before);
    }

    @Test
    void run_applyThenReplay_rawIdentityAndLedgerWrittenOnce() throws Exception {
        Path archive = archiveDirectory(originalBody);
        StoreSnapshot before = snapshot();

        CliResult applied = runCli(archive, true);

        assertThat(applied.exitCode()).isZero();
        assertThat(applied.output()).contains("Applied receipt identities: 1");
        String expectedRawHash = sha256(originalBody);
        assertThat(receiptRawHash()).isEqualTo(expectedRawHash);
        assertThat(backfillCount()).isOne();
        assertThat(jdbc.queryForList("SELECT event_id,legacy_hash,raw_hash FROM care_receipt_byte_backfill"))
                .containsExactly(Map.of(
                        "event_id", UUID.fromString("00000000-0000-0000-0000-000000000057"),
                        "legacy_hash", before.legacyHash(),
                        "raw_hash", expectedRawHash));
        assertBusinessStateUnchanged(before);

        CliResult replayed = runCli(archive, true);

        assertThat(replayed.exitCode()).isZero();
        assertThat(replayed.output()).contains("Applied receipt identities: 0");
        assertThat(receiptRawHash()).isEqualTo(expectedRawHash);
        assertThat(backfillCount()).isOne();
        assertBusinessStateUnchanged(before);
    }

    @Test
    void run_applyWithIncompleteArchive_nonzeroAndStoreUnchanged() throws Exception {
        Path archive = Files.createDirectory(tempDir.resolve("incomplete-archive"));
        StoreSnapshot before = snapshot();

        CliResult result = runCli(archive, true);

        assertThat(result.exitCode()).isNotZero();
        assertThat(receiptRawHash()).isNull();
        assertThat(backfillCount()).isZero();
        assertBusinessStateUnchanged(before);
    }

    @Test
    void run_applyWithInvalidArchive_nonzeroAndStoreUnchanged() throws Exception {
        Path archive = Files.createDirectory(tempDir.resolve("invalid-archive"));
        Files.writeString(archive.resolve("receipt.json"), "not-json", StandardCharsets.UTF_8);
        StoreSnapshot before = snapshot();

        CliResult result = runCli(archive, true);

        assertThat(result.exitCode()).isNotZero();
        assertThat(receiptRawHash()).isNull();
        assertThat(backfillCount()).isZero();
        assertBusinessStateUnchanged(before);
    }

    @Test
    void receive_v0EventIdCollision_reportsConflictWithoutClaimingBackfillIsPossible() {
        jdbc.update("UPDATE PROCESSED_EVENT SET payload_hash=NULL WHERE routing_key='payment.completed'");
        StoreSnapshot before = snapshot();

        Throwable failure = catchThrowable(() -> currentHandler().receive("payment.completed", originalBody));

        assertThat(failure).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(failure.getCause())
                .isInstanceOf(com.mediflow.notification.domain.exception.NotificationEventConflictException.class)
                .hasMessage("Notification event identity was reused with another payload/version");

        assertThat(receiptRawHash()).isNull();
        assertThat(backfillCount()).isZero();
        assertBusinessStateUnchanged(before);
    }

    private void seedLegacyV4Receipt(DriverManagerDataSource source, byte[] body) {
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var repository = new CareNotificationRepositoryAdapter(jdbc, mapper) {
            @Override
            public boolean claimPaymentReceipt(UUID eventId, String rawFingerprint, String legacyFingerprint) {
                return claim(eventId, "payment.completed", legacyFingerprint);
            }
        };
        var service = new CarePaymentReceiptService(repository);
        var handler = new CarePaymentReceiptWireHandler(mapper,
                command -> transactions.executeWithoutResult(status -> service.receive(command)), true);
        handler.receive("payment.completed", body);
    }

    private CarePaymentReceiptWireHandler currentHandler() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var service = new CarePaymentReceiptService(new CareNotificationRepositoryAdapter(jdbc, mapper));
        return new CarePaymentReceiptWireHandler(mapper,
                command -> transactions.executeWithoutResult(status -> service.receive(command)), true);
    }

    private Path archiveDirectory(byte[] body) throws IOException {
        Path archive = Files.createDirectory(tempDir.resolve("archive"));
        Files.write(archive.resolve("00000000-0000-0000-0000-000000000057.json"), body);
        return archive;
    }

    private CliResult runCli(Path archive, boolean apply) throws Exception {
        Path jar = requireFreshPackagedJar();
        Path outputFile = tempDir.resolve("cli-" + UUID.randomUUID() + ".log");
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString());
        command.add("-Dloader.main=" + MAIN_CLASS);
        command.add("-cp");
        command.add(jar.toString());
        command.add(BOOT_PROPERTIES_LAUNCHER);
        command.add("--archive-dir=" + archive);
        if (apply) command.add("--apply");

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile());
        Map<String, String> environment = processBuilder.environment();
        environment.put("MEDIFLOW_NOTIFICATION_DB_URL", POSTGRES.getJdbcUrl());
        environment.put("MEDIFLOW_NOTIFICATION_DB_USER", POSTGRES.getUsername());
        environment.put("MEDIFLOW_NOTIFICATION_DB_PASSWORD", POSTGRES.getPassword());

        Process process = processBuilder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new AssertionError("Packaged receipt backfill CLI exceeded its 60-second timeout");
        }
        String output = Files.readString(outputFile);
        if (output.contains(POSTGRES.getUsername()) || output.contains(POSTGRES.getPassword())) {
            throw new AssertionError("Packaged receipt backfill CLI output included database credential material");
        }
        return new CliResult(process.exitValue(), output);
    }

    private Path requireFreshPackagedJar() throws Exception {
        Path jar = Path.of(System.getProperty("notification.packaged.jar"));
        Path classFile = Path.of(System.getProperty("notification.classes.dir"),
                MAIN_CLASS.replace('.', '/') + ".class");
        assertThat(Files.isRegularFile(classFile)).isTrue();
        assertThat(Files.isRegularFile(jar)).isTrue();
        assertThat(Files.getLastModifiedTime(jar).toMillis())
                .isGreaterThanOrEqualTo(Files.getLastModifiedTime(classFile).toMillis());
        try (ZipFile executableJar = new ZipFile(jar.toFile())) {
            assertThat(executableJar.getEntry("BOOT-INF/classes/" + MAIN_CLASS.replace('.', '/') + ".class"))
                    .isNotNull();
            assertThat(executableJar.getEntry("org/springframework/boot/loader/launch/PropertiesLauncher.class"))
                    .isNotNull();
        }
        return jar;
    }

    private StoreSnapshot snapshot() {
        return new StoreSnapshot(
                jdbc.queryForObject("SELECT payload_hash FROM PROCESSED_EVENT WHERE routing_key='payment.completed'", String.class),
                jdbc.queryForList("SELECT * FROM NOTIFICATION"),
                jdbc.queryForList("SELECT * FROM NOTIFICATION_EVENT_OUTBOX"));
    }

    private void assertBusinessStateUnchanged(StoreSnapshot before) {
        assertThat(jdbc.queryForObject(
                "SELECT payload_hash FROM PROCESSED_EVENT WHERE routing_key='payment.completed'", String.class))
                .isEqualTo(before.legacyHash());
        assertThat(jdbc.queryForList("SELECT * FROM NOTIFICATION")).isEqualTo(before.notifications());
        assertThat(jdbc.queryForList("SELECT * FROM NOTIFICATION_EVENT_OUTBOX")).isEqualTo(before.outbox());
    }

    private String receiptRawHash() {
        return jdbc.queryForObject(
                "SELECT receipt_raw_hash FROM PROCESSED_EVENT WHERE routing_key='payment.completed'", String.class);
    }

    private int backfillCount() {
        return jdbc.queryForObject("SELECT count(*) FROM care_receipt_byte_backfill", Integer.class);
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private record CliResult(int exitCode, String output) { }

    private record StoreSnapshot(String legacyHash,
                                 List<Map<String, Object>> notifications,
                                 List<Map<String, Object>> outbox) { }
}
