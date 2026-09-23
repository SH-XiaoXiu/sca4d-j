package com.sca4d.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.resource.ResourceReference;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.api.setting.LogicSetting;
import com.sca4d.core.api.setting.Setting;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.api.setting.SettingValues;
import com.sca4d.core.api.setting.Settings;
import com.sca4d.core.api.setting.SystemSetting;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileService;
import com.sca4d.core.api.storage.FileStatus;
import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.api.storage.FileUpload;
import com.sca4d.core.api.storage.FileUploadIntent;
import com.sca4d.core.api.storage.FileUse;
import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.core.api.storage.StorageObjectMetadata;
import com.sca4d.core.api.storage.StorageUpload;
import com.sca4d.core.api.storage.StoredFile;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.security.ConfiguredSecretKeyring;
import com.sca4d.core.spring.security.SecretKeyringProperties;
import com.sca4d.storage.file.lifecycle.FileCleanupService;
import com.sca4d.storage.file.persistence.FileRecord;
import com.sca4d.storage.file.persistence.FileRepository;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** 使用 PostgreSQL 17 验证文件状态、对象位置和多实例删除领取。 */
@Testcontainers
@SpringBootTest(classes = FileServiceIntegrationTest.TestApplication.class, properties = "spring.autoconfigure.exclude="
        + "com.sca4d.core.spring.config.CoreSpringAutoConfiguration,"
        + "com.sca4d.core.spring.gateway.GatewaySpringAutoConfiguration,"
        + "com.sca4d.core.spring.openapi.CoreOpenApiConfiguration,"
        + "com.sca4d.core.spring.setting.SettingsAutoConfiguration,"
        + "com.sca4d.core.spring.dictionary.DictionaryAutoConfiguration")
@SuppressWarnings({
        "SqlNoDataSourceInspection",
        "SqlDialectInspection",
        "SpringJavaInjectionPointsAutowiringInspection"
}) // Testcontainers 和 SpringBootTest 在测试启动时提供数据源及测试 Bean，IDEA 无法静态解析。
class FileServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private FileService files;

    @Autowired
    private FileRepository repository;

    @Autowired
    private InMemoryFileStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FileCleanupService cleanup;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.application.name", () -> "storage-test");
        registry.add("sca4dj.storage.default-backend", () -> "test");
        registry.add("sca4dj.storage.cleanup-interval", () -> "PT24H");
    }

    @BeforeEach
    void resetState() {
        Boolean updatedAtFunctionExists = jdbc.queryForObject(
                "SELECT to_regprocedure('sca4dj_set_updated_at()') IS NOT NULL",
                Boolean.class);
        if (!Boolean.TRUE.equals(updatedAtFunctionExists)) {
            ResourceDatabasePopulator coreSchema = new ResourceDatabasePopulator(
                    new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"));
            coreSchema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
            coreSchema.execute(Objects.requireNonNull(jdbc.getDataSource()));
        }
        jdbc.execute("DROP TABLE IF EXISTS storage_file");
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/storage-file.sql"));
        schema.execute(Objects.requireNonNull(jdbc.getDataSource()));
        storage.reset();
    }

    @Test
    @DisplayName("用应用命名空间对象键完成上传，并保持完成、绑定和释放幂等")
    void completesBindsAndReleasesIdempotently() throws Exception {
        Principal uploader = principal(11L);
        FileUse use = use("11");
        FileUpload upload = files.createUpload(
                uploader,
                new FileUploadIntent(use, "avatar.png", "image/png", 12));
        byte[] content = png();

        assertThat(upload.fileId()).isPositive();
        assertThat(storage.preparedObject().objectKey()).startsWith("storage-test:sca4dj:storage/uploads/");
        storage.write(storage.preparedObject(), "image/png", content.length, new ByteArrayInputStream(content));

        StoredFile ready = files.completeUpload(upload.fileId(), uploader);
        assertThat(ready.status()).isEqualTo(FileStatus.READY);
        assertThat(ready.uploaderPrincipalId()).isEqualTo(uploader.principalId());
        assertThat(files.completeUpload(upload.fileId(), uploader).status()).isEqualTo(FileStatus.READY);
        assertThat(files.bind(upload.fileId(), use).status()).isEqualTo(FileStatus.IN_USE);
        assertThat(files.bind(upload.fileId(), use).status()).isEqualTo(FileStatus.IN_USE);
        assertThat(files.completeUpload(upload.fileId(), uploader).status()).isEqualTo(FileStatus.IN_USE);
        assertThat(files.createAccessUrls(List.of(upload.fileId()))).hasSize(1);

        files.release(upload.fileId(), use);
        files.release(upload.fileId(), use);
        assertThat(files.loadById(upload.fileId()).status()).isEqualTo(FileStatus.DELETE_PENDING);
    }

    @Test
    @DisplayName("SYSTEM 服务端上传不签发客户端请求，并返回 READY 文件")
    void uploadsServerContentForSystemPrincipal() throws Exception {
        FileUse use = use("system-generated");
        byte[] content = png();

        StoredFile ready = files.upload(
                SystemPrincipal.INSTANCE,
                new FileUploadIntent(use, "generated.png", "image/png", content.length),
                new ByteArrayInputStream(content));

        assertThat(ready.status()).isEqualTo(FileStatus.READY);
        assertThat(ready.uploaderPrincipalId()).isEqualTo(SystemPrincipal.ID);
        assertThat(storage.preparedUploadCount()).isZero();
        FileRecord record = repository.find(ready.fileId()).orElseThrow();
        assertThat(storage.contains(record.containerName(), record.uploadObjectKey())).isFalse();
        assertThat(storage.contains(record.containerName(), Objects.requireNonNull(record.objectKey())))
                .isTrue();
        assertThat(files.bind(ready.fileId(), use).status()).isEqualTo(FileStatus.IN_USE);
    }

    @Test
    @DisplayName("服务端上传区分内容长度、流读取和存储运行故障")
    void distinguishesServerUploadFailures() {
        FileUploadIntent intent = new FileUploadIntent(use("system-failure"), "generated.png", "image/png", 12);

        assertError(
                () -> assertThat(
                        files.upload(
                                SystemPrincipal.INSTANCE,
                                intent,
                                new ByteArrayInputStream(new byte[] {1, 2, 3})))
                        .isNotNull(),
                FileErrorCode.CONTENT_MISMATCH);

        storage.failWritesWithIOException();
        assertThatThrownBy(() -> files.upload(SystemPrincipal.INSTANCE, intent, new ByteArrayInputStream(png())))
                .isInstanceOf(IOException.class);

        storage.failWritesWithBackendError();
        assertError(
                () -> assertThat(
                        files.upload(
                                SystemPrincipal.INSTANCE,
                                intent,
                                new ByteArrayInputStream(png())))
                        .isNotNull(),
                FileErrorCode.BACKEND_UNAVAILABLE);
    }

    @Test
    @DisplayName("拒绝上传 Principal、实际内容和业务用途不匹配，并隐藏批量读取中的未知标识")
    void rejectsMismatchedUploaderContentAndUse() throws Exception {
        Principal uploader = principal(21L);
        FileUse use = use("21");
        FileUpload upload = files.createUpload(
                uploader,
                new FileUploadIntent(use, "avatar.png", "image/png", 12));
        storage.write(
                storage.preparedObject(),
                "image/jpeg",
                12,
                new ByteArrayInputStream(
                        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0}));

        assertError(() -> files.completeUpload(upload.fileId(), principal(22L)), FileErrorCode.NOT_FOUND);
        assertError(() -> files.completeUpload(upload.fileId(), uploader), FileErrorCode.CONTENT_MISMATCH);

        storage.write(storage.preparedObject(), "image/png", 12, new ByteArrayInputStream(png()));
        files.completeUpload(upload.fileId(), uploader);
        assertError(() -> files.bind(upload.fileId(), use("22")), FileErrorCode.STATE_CONFLICT);
        assertError(
                () -> assertThat(files.createAccessUrls(List.of(upload.fileId(), 999999L))).isEmpty(),
                FileErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("区分未上传对象和存储实现故障")
    void distinguishesMissingUploadsFromStorageFailures() {
        Principal uploader = principal(25L);
        FileUse use = use("25");
        FileUpload missing = files.createUpload(
                uploader,
                new FileUploadIntent(use, "missing.png", "image/png", 12));

        assertError(() -> files.completeUpload(missing.fileId(), uploader), FileErrorCode.INVALID);

        FileUpload unavailable = files.createUpload(
                uploader,
                new FileUploadIntent(use, "unavailable.png", "image/png", 12));
        storage.failInspection();
        assertError(
                () -> files.completeUpload(unavailable.fileId(), uploader),
                FileErrorCode.BACKEND_UNAVAILABLE);
    }

    @Test
    @DisplayName("对象已经转正但数据库仍为 PENDING 时可以重复完成")
    void recoversCompletionAfterTheObjectWasPromoted() throws Exception {
        Principal uploader = principal(31L);
        FileUse use = use("31");
        FileUpload upload = files.createUpload(
                uploader,
                new FileUploadIntent(use, "avatar.png", "image/png", 12));
        byte[] content = png();
        storage.write(storage.preparedObject(), "image/png", content.length, new ByteArrayInputStream(content));

        FileRecord pending = repository.find(upload.fileId()).orElseThrow();
        StorageObject storedObject = new StorageObject(
                pending.containerName(),
                Objects.requireNonNull(pending.objectKey()));
        storage.promote(storage.preparedObject(), storedObject);

        StoredFile completed = files.completeUpload(upload.fileId(), uploader);
        assertThat(completed.status()).isEqualTo(FileStatus.READY);
        assertThat(repository.find(upload.fileId()).orElseThrow().objectKey())
                .isEqualTo(storedObject.objectKey());
    }

    @Test
    @DisplayName("拒绝绑定或读取已经超过保留期限的 READY 文件")
    void rejectsExpiredReadyFilesBeforeCleanupRuns() throws Exception {
        Principal uploader = principal(35L);
        FileUse use = use("35");
        FileUpload upload = files.createUpload(
                uploader,
                new FileUploadIntent(use, "avatar.png", "image/png", 12));
        storage.write(storage.preparedObject(), "image/png", 12, new ByteArrayInputStream(png()));
        files.completeUpload(upload.fileId(), uploader);
        jdbc.update(
                "UPDATE storage_file SET ready_expires_at = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(1)),
                upload.fileId());

        assertError(() -> files.bind(upload.fileId(), use), FileErrorCode.STATE_CONFLICT);
        assertError(
                () -> assertThat(files.createAccessUrls(List.of(upload.fileId()))).isEmpty(),
                FileErrorCode.STATE_CONFLICT);
    }

    @Test
    @DisplayName("两个短事务领取不同记录，租约到期后可重新领取")
    void claimsDifferentRowsAndReclaimsExpiredLeases() {
        for (long id = 1; id <= 51; id++) {
            repository.createPending(record(id, NOW.minusSeconds(1)));
        }
        repository.requestExpiredDeletion(NOW);
        TransactionTemplate firstTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate secondTransaction = new TransactionTemplate(transactionManager);
        List<FileRecord> initial = firstTransaction.execute(
                status -> repository.claimDeletion(NOW, NOW.plusSeconds(120), "first"));
        List<FileRecord> second = secondTransaction.execute(
                status -> repository.claimDeletion(NOW, NOW.plusSeconds(120), "second"));

        assertThat(initial).hasSize(50);
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().id()).isNotIn(initial.stream().map(FileRecord::id).toList());

        List<FileRecord> beforeExpiry = secondTransaction.execute(
                status -> repository.claimDeletion(NOW.plusSeconds(119), NOW.plusSeconds(239), "third"));
        List<FileRecord> afterExpiry = secondTransaction.execute(
                status -> repository.claimDeletion(NOW.plusSeconds(121), NOW.plusSeconds(241), "third"));
        assertThat(beforeExpiry).isEmpty();
        assertThat(afterExpiry).hasSize(50);

        long reclaimedId = afterExpiry.getFirst().id();
        Boolean staleResult = secondTransaction
                .execute(status -> repository.saveDeletionResult(reclaimedId, "first", FileStatus.DELETED, null));
        Boolean currentResult = secondTransaction
                .execute(status -> repository.saveDeletionResult(reclaimedId, "third", FileStatus.DELETED, null));
        assertThat(staleResult).isFalse();
        assertThat(currentResult).isTrue();
        assertThat(repository.find(reclaimedId).orElseThrow().status()).isEqualTo(FileStatus.DELETED);
    }

    @Test
    @DisplayName("清理到期 PENDING 的临时和正式对象，并登记删除失败重试")
    void cleansExpiredObjectsAndSchedulesDeleteFailures() throws Exception {
        FileRecord recoverable = record(401L, NOW.minusSeconds(1));
        FileRecord failing = record(402L, NOW.minusSeconds(1));
        repository.createPending(recoverable);
        repository.createPending(failing);
        storage.write(
                new StorageObject(recoverable.containerName(), recoverable.uploadObjectKey()),
                "application/octet-stream",
                1L,
                new ByteArrayInputStream(new byte[] {1}));
        storage.write(
                new StorageObject(
                        recoverable.containerName(),
                        Objects.requireNonNull(recoverable.objectKey())),
                "application/octet-stream",
                1L,
                new ByteArrayInputStream(new byte[] {1}));
        storage.write(
                new StorageObject(failing.containerName(), failing.uploadObjectKey()),
                "application/octet-stream",
                1L,
                new ByteArrayInputStream(new byte[] {1}));

        storage.failingDeleteFileId = 402L;
        cleanup.cleanup();

        assertThat(repository.find(401L).orElseThrow().status()).isEqualTo(FileStatus.DELETED);
        assertThat(storage.contains(recoverable.containerName(), recoverable.uploadObjectKey())).isFalse();
        assertThat(
                storage.contains(
                        recoverable.containerName(),
                        Objects.requireNonNull(recoverable.objectKey())))
                .isFalse();
        assertThat(repository.find(402L).orElseThrow().status()).isEqualTo(FileStatus.DELETE_FAILED);
        assertThat(
                jdbc.queryForObject(
                        "SELECT delete_attempts FROM storage_file WHERE id = 402",
                        Integer.class))
                .isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                        "SELECT next_delete_at FROM storage_file WHERE id = 402",
                        Instant.class))
                .isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("服务端读取正式文件，丢弃参与业务事务且拒绝已绑定文件")
    void readsAndDiscardsUnboundFilesTransactionally() throws Exception {
        FileUse use = use("server-read");
        StoredFile ready = files.upload(
                SystemPrincipal.INSTANCE,
                new FileUploadIntent(use, "file.png", "image/png", png().length),
                new ByteArrayInputStream(png()));
        try (var content = files.openForServer(ready.fileId())) {
            assertThat(content.stream().readAllBytes()).isEqualTo(png());
            assertThat(content.contentType()).isEqualTo("image/png");
        }
        assertError(() -> files.discard(ready.fileId(), use("another")), FileErrorCode.STATE_CONFLICT);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            files.discard(ready.fileId(), use);
            status.setRollbackOnly();
        });
        assertThat(files.loadById(ready.fileId()).status()).isEqualTo(FileStatus.READY);
        files.bind(ready.fileId(), use);
        assertError(() -> files.discard(ready.fileId(), use), FileErrorCode.STATE_CONFLICT);
        try (var content = files.openForServer(ready.fileId())) {
            assertThat(content.sizeBytes()).isEqualTo(png().length);
        }
    }

    @Test
    @DisplayName("丢弃未完成上传后不能完成或读取，重复丢弃保持幂等")
    void discardsPendingAndReadyUploads() throws Exception {
        FileUse use = use("discard");
        FileUpload pending = files.createUpload(
                SystemPrincipal.INSTANCE,
                new FileUploadIntent(use, "file.png", "image/png", png().length));
        files.discard(pending.fileId(), use);
        files.discard(pending.fileId(), use);
        assertThat(files.loadById(pending.fileId()).status()).isEqualTo(FileStatus.DELETE_PENDING);
        assertError(
                () -> files.completeUpload(pending.fileId(), SystemPrincipal.INSTANCE),
                FileErrorCode.STATE_CONFLICT);
        assertError(() -> {
            try (var content = files.openForServer(pending.fileId())) {
                assertThat(content.sizeBytes()).isPositive();
            }
        }, FileErrorCode.NOT_FOUND);
        StoredFile ready = files.upload(
                SystemPrincipal.INSTANCE,
                new FileUploadIntent(use, "file.png", "image/png", png().length),
                new ByteArrayInputStream(png()));
        files.discard(ready.fileId(), use);
        files.discard(ready.fileId(), use);
        assertError(() -> files.bind(ready.fileId(), use), FileErrorCode.STATE_CONFLICT);
        assertError(() -> {
            try (var content = files.openForServer(ready.fileId())) {
                assertThat(content.sizeBytes()).isPositive();
            }
        }, FileErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("服务端不能读取已经到期且尚未清理的 READY 文件")
    void rejectsExpiredServerRead() throws Exception {
        StoredFile ready = files.upload(
                SystemPrincipal.INSTANCE,
                new FileUploadIntent(use("expired-read"), "file.png", "image/png", png().length),
                new ByteArrayInputStream(png()));
        jdbc.update("UPDATE storage_file SET ready_expires_at = ? WHERE id = ?", Timestamp.from(NOW), ready.fileId());
        assertError(() -> {
            try (var content = files.openForServer(ready.fileId())) {
                assertThat(content.sizeBytes()).isPositive();
            }
        }, FileErrorCode.NOT_FOUND);
    }

    private static FileRecord record(long id, Instant uploadExpiresAt) {
        return new FileRecord(
                id,
                1L,
                "test",
                "test:target",
                Long.toString(id),
                "test",
                "files",
                "storage-test:sca4dj:storage/uploads/" + id,
                "storage-test:sca4dj:storage/objects/" + id,
                "test.bin",
                "application/octet-stream",
                1L,
                null,
                null,
                FileStatus.PENDING,
                uploadExpiresAt,
                null,
                false);
    }

    private static FileUse use(String targetId) {
        return new FileUse(
                "PLATFORM:USERS:PROFILES:AVATARS",
                new ResourceReference("PLATFORM:USERS:PROFILES", targetId));
    }

    /** 返回使用指定正数标识的测试 Principal。 */
    private static @NotNull Principal principal(long principalId) {
        return () -> principalId;
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
    }

    private static void assertError(ThrowingAction action, FileErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
    }

    /** 表示测试中预期抛出业务异常的文件操作。 */
    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    /** 为 PostgreSQL 集成测试提供固定时钟、标识生成器、密钥和内存对象存储。 */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        SqlBatches sqlBatches() {
            return new SqlBatches(new SqlBatchProperties());
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        IdGenerator idGenerator() {
            AtomicLong values = new AtomicLong(1000L);
            return () -> Id.of(values.incrementAndGet());
        }

        @Bean
        JobExecutor jobExecutor() {
            return mock(JobExecutor.class);
        }

        @Bean
        JobScheduler jobScheduler() {
            return schedules -> {
            };
        }

        @Bean
        Settings settings() {
            return new DefaultSettings();
        }

        @Bean
        ConfiguredSecretKeyring secretKeyring() {
            SecretKeyringProperties properties = new SecretKeyringProperties();
            properties.setActiveKeyId("test");
            properties.setKeys(Map.of("test", Base64.getEncoder().encodeToString(new byte[32])));
            return new ConfiguredSecretKeyring(properties);
        }

        @Bean
        InMemoryFileStorage fileStorage() {
            return new InMemoryFileStorage();
        }

        @Bean
        DistributedLock distributedLock() {
            return new DistributedLock() {
                @Override
                public <T> T execute(
                        LockKey key,
                        Duration waitTime,
                        Supplier<T> task) {
                    return task.get();
                }
            };
        }
    }

    /** 始终返回设置定义默认值，避免文件状态测试依赖 `sys_setting` 表。 */
    private static final class DefaultSettings implements Settings {

        @Override
        public <T> @NotNull T get(@NotNull SystemSetting<T> setting) {
            return setting.defaultValue();
        }

        @Override
        public <T> @NotNull SettingValue<T> inspect(@NotNull SystemSetting<T> setting) {
            return defaultValue(setting, SettingOwner.system());
        }

        @Override
        public @NotNull SettingValues get(@NotNull Collection<? extends SystemSetting<?>> settings) {
            List<SettingValue<?>> values = new ArrayList<>();
            for (SystemSetting<?> setting : settings) {
                values.add(defaultValue(setting, SettingOwner.system()));
            }
            return new SettingValues(values);
        }

        @Override
        public <T> @NotNull T get(@NotNull LogicSetting<T> setting, @NotNull SettingOwner owner) {
            return setting.defaultValue();
        }

        @Override
        public <T> @NotNull SettingValue<T> inspect(
                @NotNull LogicSetting<T> setting,
                @NotNull SettingOwner owner) {
            return defaultValue(setting, owner);
        }

        @Override
        public <T> @NotNull SettingValue<T> replace(
                @NotNull SystemSetting<T> setting,
                @NotNull T value) {
            throw new UnsupportedOperationException("文件状态测试不修改 Settings");
        }

        @Override
        public <T> @NotNull SettingValue<T> replace(
                @NotNull LogicSetting<T> setting,
                @NotNull SettingOwner owner,
                @NotNull T value) {
            throw new UnsupportedOperationException("文件状态测试不修改 Settings");
        }

        @Override
        public @NotNull SettingValue<?> reset(@NotNull SystemSetting<?> setting) {
            throw new UnsupportedOperationException("文件状态测试不修改 Settings");
        }

        @Override
        public @NotNull SettingValue<?> reset(
                @NotNull LogicSetting<?> setting,
                @NotNull SettingOwner owner) {
            throw new UnsupportedOperationException("文件状态测试不修改 Settings");
        }

        @Override
        public void erase(@NotNull LogicSetting<?> setting, @NotNull SettingOwner owner) {
            throw new UnsupportedOperationException("文件状态测试不修改 Settings");
        }

        /** 把设置定义转换为使用 Java 默认值的快照项。 */
        private static <T> SettingValue<T> defaultValue(Setting<T> setting, SettingOwner owner) {
            return new SettingValue<>(setting, owner, setting.defaultValue(), false, false, new Version(0));
        }
    }

    /** 保存测试上传对象，完整执行 `FileStorage` 的复制、打开和删除行为。 */
    static final class InMemoryFileStorage implements FileStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

        private volatile StorageObject preparedObject;

        private final AtomicInteger preparedUploadCount = new AtomicInteger();

        private volatile long failingDeleteFileId;

        private volatile boolean inspectionFails;

        private volatile boolean writeFailsWithIOException;

        private volatile boolean writeFailsWithBackendError;

        @Override
        public @NotNull String backendId() {
            return "test";
        }

        @Override
        public @NotNull String defaultContainer() {
            return "files";
        }

        @Override
        public void checkAvailable() {
        }

        @Override
        public @NotNull StorageUpload prepareUpload(
                long fileId,
                @NotNull StorageObject object,
                @NotNull String contentType,
                long sizeBytes,
                @NotNull Instant expiresAt) {
            preparedObject = object;
            preparedUploadCount.incrementAndGet();
            return new StorageUpload(
                    URI.create("https://upload.example.test/" + fileId),
                    Map.of("Content-Type", contentType));
        }

        @Override
        public @NotNull Optional<StorageObjectMetadata> inspect(@NotNull StorageObject object) {
            if (inspectionFails) {
                throw new IllegalStateException("测试存储不可用");
            }
            return Optional.ofNullable(objects.get(key(object)))
                    .map(stored -> new StorageObjectMetadata(stored.contentType(), stored.content().length));
        }

        @Override
        public void promote(@NotNull StorageObject source, @NotNull StorageObject target) {
            StoredObject stored = objects.remove(key(source));
            if (stored != null) {
                objects.put(key(target), stored);
            } else {
                loadObject(target);
            }
        }

        @Override
        public @NotNull URI prepareAccess(
                long fileId,
                @NotNull StorageObject object,
                @NotNull Instant expiresAt) {
            loadObject(object);
            return URI.create("https://access.example.test/" + fileId);
        }

        @Override
        public void write(
                @NotNull StorageObject object,
                @NotNull String contentType,
                long sizeBytes,
                @NotNull InputStream content) throws IOException {
            if (writeFailsWithIOException) {
                throw new IOException("测试内容流读取失败");
            }
            if (writeFailsWithBackendError) {
                throw new IllegalStateException("测试存储写入失败");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            content.transferTo(output);
            if (output.size() != sizeBytes)
                throw new IllegalArgumentException("内容大小不匹配");
            objects.put(key(object), new StoredObject(contentType, output.toByteArray()));
        }

        @Override
        public @NotNull InputStream open(@NotNull StorageObject object) {
            return new ByteArrayInputStream(loadObject(object).content());
        }

        @Override
        public void delete(@NotNull StorageObject object) {
            if (failingDeleteFileId > 0 && object.objectKey().contains("/" + failingDeleteFileId)) {
                throw new IllegalStateException("测试存储删除失败");
            }
            objects.remove(key(object));
        }

        boolean contains(String container, String objectKey) {
            return objects.containsKey(container + "/" + objectKey);
        }

        void failInspection() {
            inspectionFails = true;
        }

        void failWritesWithIOException() {
            writeFailsWithIOException = true;
            writeFailsWithBackendError = false;
        }

        void failWritesWithBackendError() {
            writeFailsWithIOException = false;
            writeFailsWithBackendError = true;
        }

        int preparedUploadCount() {
            return preparedUploadCount.get();
        }

        StorageObject preparedObject() {
            return Objects.requireNonNull(preparedObject);
        }

        void reset() {
            objects.clear();
            preparedObject = null;
            preparedUploadCount.set(0);
            failingDeleteFileId = 0L;
            inspectionFails = false;
            writeFailsWithIOException = false;
            writeFailsWithBackendError = false;
        }

        private StoredObject loadObject(StorageObject object) {
            StoredObject stored = objects.get(key(object));
            if (stored == null)
                throw new IllegalStateException("对象不存在");
            return stored;
        }

        private static String key(StorageObject object) {
            return object.container() + "/" + object.objectKey();
        }
    }

    /** 保存内存对象的媒体类型和内容副本。 */
    @SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
    private static final class StoredObject {
        private final String contentType;

        private final byte[] content;

        private StoredObject(String contentType, byte[] content) {
            this.contentType = contentType;
            this.content = content.clone();
        }

        String contentType() {
            return contentType;
        }

        byte[] content() {
            return content.clone();
        }
    }
}
