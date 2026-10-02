package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证 App 在空数据库上仅通过 V1 建立现行结构，重复启动不再执行迁移。 */
@Testcontainers
class AppInitialMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withCreateContainerCmdModifier(
                    command -> command.getHostConfig()
                            .withMemory(256L * 1024 * 1024)
                            .withNanoCPUs(500_000_000L));

    @Test
    @DisplayName("空数据库仅执行 V1 并建立设置、字典、身份和 App 现行结构")
    void createsCurrentSchemaWithOneMigrationAndDoesNotRepeatIt() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();

        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()));
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'sys_setting'",
                        String.class))
                .contains("owner_type", "owner_id", "schema_version", "has_value", "encryption_context_version")
                .doesNotContain("scope", "account_id");
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'sys_dictionary_option'",
                        String.class))
                .contains("i18n");
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'iot_device'",
                        String.class))
                .contains("binding_generation")
                .doesNotContain("binding_revision");
        assertThat(
                jdbc.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                        String.class))
                .contains(
                        "iam_system_identity",
                        "platform_user_profile",
                        "app_iot_product_profile",
                        "app_iot_device_profile",
                        "app_iot_device_creation_request",
                        "notification_email_sender",
                        "notification_email_template",
                        "storage_file");
        assertThat(
                jdbc.update(
                        "INSERT INTO iam_system_identity (code, principal_id, subject_id) VALUES ('MAIN', 100, 42)"))
                .isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                        "SELECT subject_id FROM iam_system_identity WHERE code = 'MAIN'",
                        Long.class))
                .isEqualTo(42L);
    }
}
