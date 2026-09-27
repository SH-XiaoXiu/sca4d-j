package com.sca4d.app.server.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.name.code.TreeCode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证旧 Account 设置和审计记录升级后可由当前 TreeCode 类型读取。 */
@Testcontainers
class TreeCodeDataMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migratesAccountSettingsAndAuditHistory() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("7").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                """
                        INSERT INTO audit_record (id, request_id, subject_id, principal_id, operation, target_type, target_id, result, occurred_at)
                        VALUES (701, 'old-audit', 101, 102, 'platform/account/delete', 'account', '103', 'SUCCESS', CURRENT_TIMESTAMP),
                               (702, 'old-email-audit', 101, 102, 'system/email/template/replace', 'NOTIFICATION/EMAIL/TEMPLATE', '104', 'SUCCESS', CURRENT_TIMESTAMP)
                        """);
        jdbc.update(
                """
                        INSERT INTO sys_setting (owner_type, owner_id, setting_key, schema_version, has_value, value_text, version)
                        VALUES ('account', '103', 'authentication.account-policy', 1, TRUE, ?, 7)
                        """,
                """
                        {"loginFactorCount":1,"sensitiveActionFactorCount":1,
                         "reusableAuthenticationMaximumAgeSeconds":300,
                         "methods":[
                           {"methodCode":"password","loginStartEnabled":true,"loginAdditionalEnabled":true,"sensitiveActionEnabled":true},
                           {"methodCode":"totp","loginStartEnabled":false,"loginAdditionalEnabled":true,"sensitiveActionEnabled":true}]}
                        """);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(
                new AuditOperation(
                        jdbc.queryForObject(
                                "SELECT operation FROM audit_record WHERE id = 701",
                                String.class))
                        .id())
                .isEqualTo("PLATFORM/ACCOUNT/DELETE");
        assertThat(
                TreeCode.ofSlash(
                        jdbc.queryForObject(
                                "SELECT target_type FROM audit_record WHERE id = 701",
                                String.class)))
                .isEqualTo(TreeCode.ofSlash("IAM/ACCOUNT"));
        assertThat(jdbc.queryForObject("SELECT target_type FROM audit_record WHERE id = 702", String.class))
                .isEqualTo("NOTIFICATION/EMAIL/TEMPLATES");
        String policy = jdbc.queryForObject(
                "SELECT value_text FROM sys_setting WHERE owner_type = 'IAM/ACCOUNT' AND owner_id = '103'",
                String.class);
        assertThat(policy).contains("AUTHENTICATION/PASSWORD", "AUTHENTICATION/TOTP");
        assertThat(new AccountAuthenticationSettingsCodec().decode(policy).methods())
                .containsKeys(PasswordAuthenticationMethod.CODE, TreeCode.ofSlash("AUTHENTICATION/TOTP"));
        assertThat(
                jdbc.queryForObject(
                        "SELECT version FROM sys_setting WHERE owner_type = 'IAM/ACCOUNT' AND owner_id = '103'",
                        Long.class))
                .isEqualTo(8L);
    }
}
