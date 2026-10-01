package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.*;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.spring.security.ConfiguredSecretKeyring;
import com.sca4d.core.spring.security.SecretKeyringProperties;
import com.sca4d.core.spring.setting.SettingSecretCodecCatalog;
import db.migration.V13__normalize_setting_codes;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** 使用 Flyway 的真实事务验证设置编码归一及密文用途迁移。 */
@Testcontainers
class SettingCodesFlywayIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private DriverManagerDataSource source;

    private JdbcTemplate jdbc;

    private String schema;

    private SecretKeyring keys;

    /** 每例创建独立 schema，旧结构由正式资源建立。 */
    @BeforeEach
    void prepare() {
        source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        schema = "setting_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(source).execute("CREATE SCHEMA " + schema);
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setConnectionProperties(properties);
        var populator = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"));
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.execute(source);
        jdbc = new JdbcTemplate(source);
        var keyProperties = new SecretKeyringProperties();
        keyProperties.setActiveKeyId("test");
        keyProperties.setKeys(Map.of("test", Base64.getEncoder().encodeToString(new byte[32])));
        keys = new ConfiguredSecretKeyring(keyProperties);
    }

    @Test
    void migratesPlainSecretAndResetRowsAndRecordsFlywayVersion() {
        plain("product.display-name", "product");
        jdbc.update(
                "INSERT INTO sys_setting(owner_type,owner_id,setting_key,schema_version,has_value,version) VALUES ('SYSTEM','1','product.old-value',1,FALSE,4)");
        var encrypted = new SettingSecretCodecCatalog(keys.activeCipher(), SettingOwner.system(), "auth.secret-key")
                .select(2)
                .encode("private-value".getBytes(StandardCharsets.UTF_8));
        jdbc.update(
                "INSERT INTO sys_setting(owner_type,owner_id,setting_key,schema_version,has_value,value_key_id,value_ciphertext,encryption_context_version,synchronized_from_environment,version) VALUES ('SYSTEM','1','auth.secret-key',1,TRUE,?,?,2,TRUE,7)",
                encrypted.keyId(),
                encrypted.ciphertext());
        Flyway flyway = flyway(true);
        flyway.migrate();
        assertThat(
                jdbc.queryForObject(
                        "SELECT value_text FROM sys_setting WHERE setting_key='PRODUCT:DISPLAY_NAME'",
                        String.class))
                .isEqualTo("product");
        assertThat(
                jdbc.queryForObject(
                        "SELECT has_value FROM sys_setting WHERE setting_key='PRODUCT:OLD_VALUE'",
                        Boolean.class))
                .isFalse();
        byte[] cipher = jdbc.queryForObject(
                "SELECT value_ciphertext FROM sys_setting WHERE setting_key='AUTH:SECRET_KEY'",
                byte[].class);
        byte[] plain = new SettingSecretCodecCatalog(keys.activeCipher(), SettingOwner.system(), "AUTH:SECRET_KEY")
                .select(2)
                .decode(new EncryptedSecret("test", cipher));
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo("private-value");
        assertThat(
                jdbc.queryForObject(
                        "SELECT synchronized_from_environment FROM sys_setting WHERE setting_key='AUTH:SECRET_KEY'",
                        Boolean.class))
                .isTrue();
        assertThat(
                jdbc.queryForObject("SELECT version FROM sys_setting WHERE setting_key='AUTH:SECRET_KEY'", Long.class))
                .isEqualTo(8);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void normalizationCollisionLeavesBothOldValues() {
        plain("product.a-b", "one");
        plain("product.a_b", "two");
        assertThatThrownBy(() -> flyway(true).migrate()).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForList("SELECT setting_key FROM sys_setting", String.class))
                .containsExactlyInAnyOrder("product.a-b", "product.a_b");
    }

    @Test
    void missingKeyringRollsBackAlreadyUpdatedPlainRow() {
        plain("a.plain", "before");
        var encrypted = new SettingSecretCodecCatalog(keys.activeCipher(), SettingOwner.system(), "z.secret").select(2)
                .encode(new byte[] {1});
        jdbc.update(
                "INSERT INTO sys_setting(owner_type,owner_id,setting_key,schema_version,has_value,value_key_id,value_ciphertext,encryption_context_version,version) VALUES ('SYSTEM','1','z.secret',1,TRUE,?,?,2,1)",
                encrypted.keyId(),
                encrypted.ciphertext());
        assertThatThrownBy(() -> flyway(false).migrate()).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT value_text FROM sys_setting WHERE setting_key='a.plain'", String.class))
                .isEqualTo("before");
    }

    @Test
    void readsKeyringFromFlywayPlaceholders() {
        var encrypted = new SettingSecretCodecCatalog(keys.activeCipher(), SettingOwner.system(), "z.secret")
                .select(2)
                .encode(new byte[] {1});
        jdbc.update(
                "INSERT INTO sys_setting(owner_type,owner_id,setting_key,schema_version,has_value,value_key_id,value_ciphertext,encryption_context_version,version) VALUES ('SYSTEM','1','z.secret',1,TRUE,?,?,2,1)",
                encrypted.keyId(),
                encrypted.ciphertext());

        flywayWithPlaceholders().migrate();

        byte[] cipher = jdbc.queryForObject(
                "SELECT value_ciphertext FROM sys_setting WHERE setting_key='Z:SECRET'",
                byte[].class);
        byte[] plain = new SettingSecretCodecCatalog(keys.activeCipher(), SettingOwner.system(), "Z:SECRET")
                .select(2)
                .decode(new EncryptedSecret("test", cipher));
        assertThat(plain).containsExactly(1);
    }

    /** 在 V12 基线上只运行新的 Java 迁移，验证迁移本身而不启动业务模块。 */
    private Flyway flyway(boolean withKeys) {
        return Flyway.configure()
                .dataSource(source)
                .schemas(schema)
                .defaultSchema(schema)
                .baselineOnMigrate(true)
                .baselineVersion("12")
                .locations("classpath:setting-migration-test-empty")
                .javaMigrations(new V13__normalize_setting_codes(withKeys ? keys : null))
                .load();
    }

    /** 验证 Java migration 使用 Spring/Flyway 配置边界传入的密钥材料。 */
    private Flyway flywayWithPlaceholders() {
        return Flyway.configure()
                .dataSource(source)
                .schemas(schema)
                .defaultSchema(schema)
                .baselineOnMigrate(true)
                .baselineVersion("12")
                .locations("classpath:setting-migration-test-empty")
                .placeholders(
                        Map.of(
                                "sca4dj_keyring_active_key_id",
                                "test",
                                "sca4dj_keyring_test_key",
                                Base64.getEncoder().encodeToString(new byte[32])))
                .javaMigrations(new V13__normalize_setting_codes())
                .load();
    }

    /** 保存合法旧编码的普通值。 */
    private void plain(String key, String value) {
        jdbc.update(
                "INSERT INTO sys_setting(owner_type,owner_id,setting_key,schema_version,has_value,value_text,version) VALUES ('SYSTEM','1',?,1,TRUE,?,1)",
                key,
                value);
    }
}
