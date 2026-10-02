package com.sca4d.app.server.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.mapper.PlatformUserProfileMapper;
import com.sca4d.app.server.repository.PlatformUserProfileRepository;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.ResourceReference;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
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
import com.sca4d.core.spring.security.ConfiguredSecretKeyring;
import com.sca4d.core.spring.security.SecretKeyringProperties;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.storage.file.config.FileAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 使用 PostgreSQL 验证示例 App 头像替换的事务回滚和同一资料并发串行结果。 */
@Testcontainers
@SuppressWarnings({"DuplicatedCode", "SqlDialectInspection"})
@Sql(statements = "TRUNCATE TABLE platform_user_profile, storage_file CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SpringBootTest(classes = PlatformUserProfilePersistenceIntegrationTest.TestApplication.class, properties = {
        "spring.flyway.locations=classpath:db/migration",
        "mybatis.mapper-locations=classpath:/mapper/app/PlatformUserProfileMapper.xml,"
                + "classpath:/mapper/storage/FileMapper.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true",
        "mybatis.configuration.arg-name-based-constructor-auto-mapping=true",
        "sca4dj.storage.backend.default=test",
        "sca4dj.storage.transfer-base-url=https://files.example.test"
})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PlatformUserProfilePersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    private static final byte[] FIRST_AVATAR = "first-avatar-content".getBytes(UTF_8);

    private static final byte[] SECOND_AVATAR = "second-avatar-content"
            .getBytes(UTF_8);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final FileService files;

    private final PlatformUserProfileService profiles;

    private final PlatformUserProfileRepository repository;

    private final TestFileStorage storage;

    PlatformUserProfilePersistenceIntegrationTest(
            PlatformUserProfileService profiles,
            PlatformUserProfileRepository repository,
            FileService files,
            TestFileStorage storage) {
        this.profiles = profiles;
        this.repository = repository;
        this.files = files;
        this.storage = storage;
    }

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    @DisplayName("Rolls Back Profile And New File Binding When Previous Avatar Release Fails")
    void rollsBackProfileAndNewFileBindingWhenPreviousAvatarReleaseFails() throws Exception {
        long targetPrincipalId = 101L;
        long unrelatedPrincipalId = 102L;
        PlatformPrincipal uploader = new PlatformPrincipal(201L);
        assertThat(repository.insert(targetPrincipalId, "Target", null, null).principalId())
                .isEqualTo(targetPrincipalId);

        long unrelatedFileId = readyFile(
                uploader,
                unrelatedPrincipalId,
                FIRST_AVATAR,
                "image/png");
        long newFileId = readyFile(
                uploader,
                targetPrincipalId,
                SECOND_AVATAR,
                "image/webp");
        assertThat(repository.updateAvatar(targetPrincipalId, unrelatedFileId).avatarFileId())
                .isEqualTo(unrelatedFileId);

        assertThatThrownBy(
                () -> profiles.replaceAvatar(
                        targetPrincipalId,
                        uploader,
                        newFileId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(FileErrorCode.STATE_CONFLICT));

        assertThat(repository.loadByPrincipalId(targetPrincipalId).avatarFileId()).isEqualTo(unrelatedFileId);
        assertThat(files.loadById(newFileId).status()).isEqualTo(FileStatus.READY);
        assertThat(files.loadById(unrelatedFileId).status()).isEqualTo(FileStatus.READY);
    }

    @Test
    @DisplayName("Serializes Concurrent Avatar Replacements For The Same Profile")
    void serializesConcurrentAvatarReplacementsForTheSameProfile() throws Exception {
        long targetPrincipalId = 301L;
        PlatformPrincipal uploader = new PlatformPrincipal(401L);
        assertThat(repository.insert(targetPrincipalId, "Concurrent", null, null).principalId())
                .isEqualTo(targetPrincipalId);
        long firstFileId = readyFile(
                uploader,
                targetPrincipalId,
                FIRST_AVATAR,
                "image/png");
        long secondFileId = readyFile(
                uploader,
                targetPrincipalId,
                SECOND_AVATAR,
                "image/webp");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待并发头像替换开始超时");
                }
                return profiles.replaceAvatar(
                        targetPrincipalId,
                        uploader,
                        firstFileId);
            });
            var second = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待并发头像替换开始超时");
                }
                return profiles.replaceAvatar(
                        targetPrincipalId,
                        uploader,
                        secondFileId);
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        PlatformUserProfile current = repository.loadByPrincipalId(targetPrincipalId);
        assertThat(current.avatarFileId()).isIn(firstFileId, secondFileId);
        long currentFileId = Objects.requireNonNull(current.avatarFileId());
        assertThat(files.loadById(currentFileId).status()).isEqualTo(FileStatus.IN_USE);
        long replacedFileId = currentFileId == firstFileId ? secondFileId : firstFileId;
        assertThat(files.loadById(replacedFileId).status()).isEqualTo(FileStatus.DELETE_PENDING);
    }

    /** 创建并完成一份绑定到指定用户资料、仍处于 READY 的头像文件。 */
    private long readyFile(
            @NotNull PlatformPrincipal uploader,
            long principalId,
            byte[] content,
            String contentType) throws IOException {
        FileUpload upload = files.createUpload(
                uploader,
                new FileUploadIntent(
                        avatarUse(principalId),
                        "avatar.bin",
                        contentType,
                        content.length));
        storage.writeUpload(upload.fileId(), contentType, content);
        files.completeUpload(upload.fileId(), uploader);
        return upload.fileId();
    }

    /** 返回指定 Platform 用户资料的头像用途。 */
    private static FileUse avatarUse(long principalId) {
        return new FileUse(
                PlatformUserProfileService.PROFILE_AVATAR,
                new ResourceReference(
                        PlatformUserProfile.RESOURCE_TYPE,
                        Long.toString(principalId)));
    }

    /** 只装配头像持久化测试需要的数据库、MyBatis、文件服务和资料服务。 */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            MybatisAutoConfiguration.class,
            FileAutoConfiguration.class
    })
    @MapperScan(basePackageClasses = PlatformUserProfileMapper.class)
    @Import({PlatformUserProfileRepository.class, PlatformUserProfileService.class})
    static class TestApplication {

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
        ConfiguredSecretKeyring secretKeyring() {
            SecretKeyringProperties properties = new SecretKeyringProperties();
            properties.setActiveKeyId("test");
            properties.setKeys(
                    Map.of(
                            "test",
                            Base64.getEncoder().encodeToString(new byte[32])));
            return new ConfiguredSecretKeyring(properties);
        }

        @Bean
        DefaultSource settings() {
            return new DefaultSource();
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
        TestFileStorage fileStorage() {
            return new TestFileStorage();
        }

        @Bean
        DistributedLock distributedLock() {
            return new LocalDistributedLock();
        }
    }

    /** 始终返回设置定义默认值，避免头像持久化测试依赖 Settings 表内容。 */
    private static final class DefaultSource implements SettingReader<String>, SettingWriter<String> {
        @Override
        public @NotNull TreeCode code() {
            return TreeCode.of("SETTING:TEST");
        }

        @Override
        public @NotNull SettingValue<String> read(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
            return SettingValue.read(code(), setting, owner, null, 0, new Version(0), false);
        }

        @Override
        public void replace(
                @NotNull TreeCode setting,
                @NotNull SettingOwner owner,
                @NotNull String value,
                int codecVersion,
                boolean secret,
                @NotNull Version expected) {
            throw new UnsupportedOperationException("持久化测试不修改设置");
        }

        @Override
        public void reset(
                @NotNull TreeCode setting,
                @NotNull SettingOwner owner,
                int codecVersion,
                @NotNull Version expected) {
            throw new UnsupportedOperationException("持久化测试不修改设置");
        }

        @Override
        public void erase(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
            throw new UnsupportedOperationException("持久化测试不修改设置");
        }
    }

    /** 使用 JVM 锁实现测试进程内相同 LockKey 的限时互斥。 */
    private static class LocalDistributedLock implements DistributedLock {
        private final Map<LockKey, ReentrantLock> locks = new ConcurrentHashMap<>();

        @Override
        public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
            ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
            boolean acquired;
            try {
                acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(CoreErrorCode.UNAVAILABLE, exception);
            }
            if (!acquired) {
                throw new BusinessException(CoreErrorCode.CONFLICT);
            }
            try {
                return task.get();
            } finally {
                lock.unlock();
            }
        }
    }

    /** 在内存中保存文件内容，同时让文件状态继续使用 PostgreSQL Repository。 */
    static class TestFileStorage implements FileStorage {
        private final Map<Long, StorageObject> preparedUploads = new ConcurrentHashMap<>();

        private final Map<String, byte[]> contents = new ConcurrentHashMap<>();

        private final Map<String, String> contentTypes = new ConcurrentHashMap<>();

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
            preparedUploads.put(fileId, object);
            return new StorageUpload(
                    URI.create("https://files.example.test/uploads/" + fileId),
                    Map.of("Content-Type", contentType));
        }

        @Override
        public @NotNull Optional<StorageObjectMetadata> inspect(
                @NotNull StorageObject object) {
            byte[] content = contents.get(key(object));
            if (content == null) {
                return Optional.empty();
            }
            return Optional.of(
                    new StorageObjectMetadata(
                            contentTypes.get(key(object)),
                            content.length));
        }

        @Override
        public void promote(
                @NotNull StorageObject source,
                @NotNull StorageObject target) {
            byte[] content = contents.remove(key(source));
            String contentType = contentTypes.remove(key(source));
            if (content == null || contentType == null) {
                if (!contents.containsKey(key(target))) {
                    throw new IllegalStateException("测试上传对象不存在");
                }
                return;
            }
            contents.put(key(target), content);
            contentTypes.put(key(target), contentType);
        }

        @Override
        public @NotNull URI prepareAccess(
                long fileId,
                @NotNull StorageObject object,
                @NotNull Instant expiresAt) {
            return URI.create("https://files.example.test/objects/" + fileId);
        }

        @Override
        public void write(
                @NotNull StorageObject object,
                @NotNull String contentType,
                long sizeBytes,
                @NotNull InputStream content) throws IOException {
            byte[] bytes = content.readAllBytes();
            if (bytes.length != sizeBytes) {
                throw new IllegalArgumentException("上传内容字节数与申请不一致");
            }
            contents.put(key(object), bytes);
            contentTypes.put(key(object), contentType);
        }

        @Override
        public @NotNull InputStream open(@NotNull StorageObject object) {
            byte[] content = contents.get(key(object));
            if (content == null) {
                throw new IllegalStateException("测试文件对象不存在");
            }
            return new ByteArrayInputStream(content);
        }

        @Override
        public void delete(@NotNull StorageObject object) {
            contents.remove(key(object));
            contentTypes.remove(key(object));
        }

        /** 把客户端已经上传的内容写入对应临时对象。 */
        void writeUpload(long fileId, String contentType, byte[] content) throws IOException {
            StorageObject object = preparedUploads.get(fileId);
            if (object == null) {
                throw new IllegalStateException("测试文件缺少上传申请: " + fileId);
            }
            write(object, contentType, content.length, new ByteArrayInputStream(content));
        }

        /** 清除测试方法之间不属于数据库事务的对象内容。 */
        void reset() {
            preparedUploads.clear();
            contents.clear();
            contentTypes.clear();
        }

        /** 返回内存对象使用的稳定容器与对象键。 */
        private static String key(StorageObject object) {
            return object.container() + "/" + object.objectKey();
        }
    }
}
