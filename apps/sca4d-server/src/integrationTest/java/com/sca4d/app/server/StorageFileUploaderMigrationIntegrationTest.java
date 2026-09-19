package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/** 验证已有文件只能从上传 Account 严格迁移到唯一 Platform Principal。 */
@Testcontainers
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
class StorageFileUploaderMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc = jdbc();
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS job CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS quartz CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    @Test
    @DisplayName("唯一 Platform Principal 回填为文件创建者")
    void migratesUniquePlatformPrincipal() {
        migrateToV4();
        insertAccount(101L, 11L);
        insertPrincipal(201L, "iam.platform-principal");
        insertPrincipal(202L, "test:other-principal");
        link(101L, 201L);
        link(101L, 202L);
        insertFile(301L, 101L);

        migrateLatest();

        assertThat(jdbc.queryForMap("SELECT * FROM storage_file WHERE id = 301"))
                .containsEntry("uploader_principal_id", 201L);
        assertThat(storageFileColumns())
                .contains("uploader_principal_id")
                .doesNotContain("uploader_account_id");
    }

    @Test
    @DisplayName("没有或存在多个 Platform Principal 时拒绝迁移")
    void rejectsMissingAndAmbiguousPlatformPrincipals() {
        migrateToV4();
        insertAccount(101L, 11L);
        insertAccount(102L, 12L);
        insertPrincipal(201L, "test:other-principal");
        insertPrincipal(202L, "iam.platform-principal");
        insertPrincipal(203L, "iam.platform-principal");
        link(101L, 201L);
        link(102L, 202L);
        link(102L, 203L);
        insertFile(301L, 101L);
        insertFile(302L, 102L);

        assertThatThrownBy(this::migrateLatest)
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("2 条记录无法从 uploader_account_id 唯一确定 iam.platform-principal");

        assertThat(storageFileColumns())
                .contains("uploader_account_id")
                .doesNotContain("uploader_principal_id");
    }

    /** 创建 V4 结构中的 Subject 和 Account。 */
    private void insertAccount(long accountId, long subjectId) {
        jdbc.update(
                "INSERT INTO iam_subject (id, subject_type, status) VALUES (?, 'HUMAN', 'ACTIVE')",
                subjectId);
        jdbc.update(
                "INSERT INTO iam_account (id, subject_id, realm_key, status) VALUES (?, ?, 'platform', 'ACTIVE')",
                accountId,
                subjectId);
    }

    /** 创建指定稳定类型的 Principal。 */
    private void insertPrincipal(long principalId, String principalType) {
        jdbc.update(
                "INSERT INTO iam_principal (id, principal_type, status) VALUES (?, ?, 'ACTIVE')",
                principalId,
                principalType);
    }

    /** 建立 Account 可以选择的 Principal 关系。 */
    private void link(long accountId, long principalId) {
        jdbc.update(
                "INSERT INTO iam_account_principal (account_id, principal_id) VALUES (?, ?)",
                accountId,
                principalId);
    }

    /** 创建一条使用旧 uploader_account_id 的待上传文件。 */
    private void insertFile(long fileId, long uploaderAccountId) {
        jdbc.update(
                "INSERT INTO storage_file ("
                        + "id, uploader_account_id, scene, target_resource_type, target_resource_id, "
                        + "backend_id, container_name, upload_object_key, object_key, filename, "
                        + "declared_content_type, declared_size_bytes, status, upload_expires_at) "
                        + "VALUES (?, ?, 'test', 'test:target', ?, 'test', 'files', ?, ?, "
                        + "'test.bin', 'application/octet-stream', 1, 'PENDING', CURRENT_TIMESTAMP + INTERVAL '1 hour')",
                fileId,
                uploaderAccountId,
                Long.toString(fileId),
                "uploads/" + fileId,
                "objects/" + fileId);
    }

    /** 返回 storage_file 当前列名。 */
    private List<String> storageFileColumns() {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'storage_file'",
                String.class);
    }

    /** 把测试数据库迁移到包含旧文件上传者字段的 V4。 */
    private void migrateToV4() {
        flyway(MigrationVersion.fromVersion("4")).migrate();
    }

    /** 把测试数据库从 V4 迁移到当前最新版本。 */
    private void migrateLatest() {
        flyway(null).migrate();
    }

    /** 创建使用 App 主库迁移位置和可选目标版本的 Flyway。 */
    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    /** 创建连接测试 PostgreSQL 的 JdbcTemplate。 */
    private JdbcTemplate jdbc() {
        return new JdbcTemplate(
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()));
    }
}
