package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.iot.message.BuiltInDevicePolicies;
import com.sca4d.iot.message.DevicePolicyCatalog;
import com.sca4d.iot.model.ThingModelJsonCodec;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/** 验证主库和 TimescaleDB 能够把 V1 数据迁移到当前结构。 */
@Testcontainers
@SuppressWarnings("SqlDialectInspection")
class IotI18nMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final PostgreSQLContainer<?> TIMESCALE = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.29.0-pg17-oss")
                    .asCompatibleSubstituteFor("postgres"));

    private static final String LEGACY_MODEL = """
            {
              "formatVersion": 1,
              "productCode": "migration-climate",
              "version": 1,
              "displayName": "迁移温控器",
              "properties": [
                {
                  "code": "power",
                  "displayName": "电源",
                  "valueType": {"kind": "BOOLEAN"},
                  "access": "READ_WRITE",
                  "telemetry": false,
                  "acknowledgementPolicy": "reported-equals-desired",
                  "retryPolicy": "state-sync-default"
                },
                {
                  "code": "mode",
                  "displayName": "运行模式",
                  "valueType": {
                    "kind": "ENUM",
                    "enumValues": [
                      {"code": "cool", "displayName": "制冷"},
                      {"code": "fan", "displayName": "送风"}
                    ]
                  },
                  "access": "READ_ONLY",
                  "telemetry": false
                }
              ],
              "events": [
                {
                  "code": "fault",
                  "delivery": "RELIABLE",
                  "outputs": [
                    {"code": "code", "valueType": {"kind": "STRING", "maximumLength": 32}}
                  ]
                }
              ],
              "commands": [
                {
                  "code": "set-mode",
                  "inputs": [
                    {
                      "code": "mode",
                      "valueType": {
                        "kind": "ENUM",
                        "enumValues": [
                          {"code": "cool", "displayName": "制冷"},
                          {"code": "fan", "displayName": "送风"}
                        ]
                      }
                    }
                  ],
                  "acknowledgementPolicy": "device-result",
                  "retryPolicy": "command-default"
                }
              ]
            }
            """;

    @Test
    @DisplayName("主库把 V1 IoT、系统设置和账号认证数据迁移到当前结构")
    void migratesMainDatabaseIotDataAndVersionColumns() throws Exception {
        migrateToV1(POSTGRES, "classpath:db/migration");
        JdbcTemplate jdbc = jdbc(POSTGRES);
        jdbc.update(
                "INSERT INTO sys_setting (setting_key, value_text, synchronized_from_environment, version) "
                        + "VALUES ('product.display-name', 'Legacy Product', FALSE, 7)");
        jdbc.update(
                "INSERT INTO sys_setting (setting_key, value_key_id, value_ciphertext, version) "
                        + "VALUES ('migration.secret', 'legacy-key', decode('01020304', 'hex'), 2)");
        jdbc.update(
                "INSERT INTO app_account_authentication_settings ("
                        + "account_id, login_factor_count, sensitive_action_factor_count, "
                        + "reusable_authentication_maximum_age_seconds) VALUES (91002, 2, 1, 600)");
        jdbc.update(
                "INSERT INTO app_account_authentication_method ("
                        + "account_id, method_code, login_start_enabled, login_additional_enabled, "
                        + "sensitive_action_enabled) VALUES "
                        + "(91002, 'totp', FALSE, TRUE, TRUE), "
                        + "(91002, 'password', TRUE, TRUE, TRUE)");
        jdbc.update(
                "INSERT INTO iot_product (id, code, display_name) VALUES (91001, 'migration-climate', '迁移温控器')");
        jdbc.update(
                "INSERT INTO iot_thing_model (product_id, version, model_json, content_hash, published_at) "
                        + "VALUES (91001, 1, CAST(? AS JSONB), ?, CURRENT_TIMESTAMP)",
                LEGACY_MODEL,
                "0".repeat(64));
        jdbc.update(
                "INSERT INTO iot_downlink_message ("
                        + "message_id, device_id, binding_revision, kind, target_code, data_json, "
                        + "acknowledgement_policy, acknowledgement_timeout_millis, retry_policy, status, last_error) "
                        + "VALUES (92001, 93001, 1, 'COMMAND', 'set-mode', '{}'::JSONB, "
                        + "'device-result', 1000, 'command-default', 'FAILED', '命令重试次数已耗尽')");
        jdbc.update(
                "INSERT INTO iot_device_schedule ("
                        + "schedule_id, device_id, command_code, command_inputs_json, trigger_type, "
                        + "execute_at, valid_for_millis, status, last_failure) "
                        + "VALUES (94001, 93001, 'set-mode', '{}'::JSONB, 'ONCE', CURRENT_TIMESTAMP, "
                        + "60000, 'PAUSED', '2908')");

        migrateLatest(POSTGRES, "classpath:db/migration");

        String productI18n = jdbc.queryForObject(
                "SELECT i18n::TEXT FROM iot_product WHERE id = 91001",
                String.class);
        assertThat(new ObjectMapper().readTree(productI18n).path("zh-CN").path("displayName").textValue())
                .isEqualTo("迁移温控器");

        Map<String, Object> modelRow = jdbc.queryForMap(
                "SELECT model_json::TEXT AS model_json, i18n::TEXT AS i18n, content_hash "
                        + "FROM iot_thing_model WHERE product_id = 91001 AND version = 1");
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode migratedModel = objectMapper.readTree((String) modelRow.get("model_json"));
        JsonNode migratedI18n = objectMapper.readTree((String) modelRow.get("i18n")).path("zh-CN");
        assertThat(migratedModel.path("formatVersion").intValue()).isEqualTo(1);
        assertThat(migratedModel.findValues("displayName")).isEmpty();
        assertThat(migratedI18n.path("/displayName").textValue()).isEqualTo("迁移温控器");
        assertThat(migratedI18n.path("/properties/power/displayName").textValue()).isEqualTo("电源");
        assertThat(migratedI18n.path("/properties/mode/valueType/enumValues/cool/displayName").textValue())
                .isEqualTo("制冷");
        assertThat(migratedI18n.path("/events/fault/displayName").textValue()).isEqualTo("fault");
        assertThat(migratedI18n.path("/events/fault/outputs/code/displayName").textValue()).isEqualTo("code");
        assertThat(migratedI18n.path("/commands/set-mode/displayName").textValue()).isEqualTo("set-mode");
        assertThat(migratedI18n.path("/commands/set-mode/inputs/mode/displayName").textValue())
                .isEqualTo("mode");

        ThingModelJsonCodec codec = new ThingModelJsonCodec(
                objectMapper,
                new DevicePolicyCatalog(
                        BuiltInDevicePolicies.acknowledgements(),
                        BuiltInDevicePolicies.retries()));
        String canonical = codec.encode(codec.decode((String) modelRow.get("model_json")));
        String expectedHash = DigestUtil.sha256Hex(canonical);
        assertThat(modelRow.get("content_hash")).isEqualTo(expectedHash);

        assertThat(
                jdbc.queryForObject(
                        "SELECT last_failure_code FROM iot_downlink_message WHERE message_id = 92001",
                        String.class))
                .isEqualTo("COMMAND_RETRY_EXHAUSTED");
        assertThat(
                jdbc.queryForObject(
                        "SELECT last_failure_code FROM iot_device_schedule WHERE schedule_id = 94001",
                        String.class))
                .isEqualTo("2908");
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_name IN ('iot_product', 'iot_downlink_message', 'iot_device_schedule') "
                                + "AND column_name IN ('display_name', 'last_error', 'last_failure', "
                                + "'binding_revision', 'desired_revision')",
                        String.class))
                .isEmpty();
        assertThat(
                jdbc.queryForObject(
                        "SELECT binding_generation FROM iot_downlink_message WHERE message_id = 92001",
                        Long.class))
                .isEqualTo(1L);
        assertThat(
                jdbc.queryForList(
                        "SELECT table_schema || '.' || table_name || '.' || column_name "
                                + "FROM information_schema.columns "
                                + "WHERE (table_schema, table_name, column_name) IN ("
                                + "('public', 'iam_principal', 'roles_version'), "
                                + "('job', 'job_run', 'job_definition_version'), "
                                + "('public', 'iot_device', 'binding_generation'), "
                                + "('public', 'iot_device', 'desired_sequence'), "
                                + "('public', 'iot_reported_group', 'binding_generation'), "
                                + "('public', 'iot_reported_group', 'version'), "
                                + "('public', 'iot_desired_property', 'binding_generation'), "
                                + "('public', 'iot_desired_property', 'desired_sequence'), "
                                + "('public', 'iot_downlink_message', 'binding_generation'), "
                                + "('public', 'iot_downlink_message', 'desired_sequence')) "
                                + "ORDER BY table_schema, table_name, column_name",
                        String.class))
                .containsExactly(
                        "job.job_run.job_definition_version",
                        "public.iam_principal.roles_version",
                        "public.iot_desired_property.binding_generation",
                        "public.iot_desired_property.desired_sequence",
                        "public.iot_device.binding_generation",
                        "public.iot_device.desired_sequence",
                        "public.iot_downlink_message.binding_generation",
                        "public.iot_downlink_message.desired_sequence",
                        "public.iot_reported_group.binding_generation",
                        "public.iot_reported_group.version");

        assertThat(
                jdbc.queryForMap(
                        "SELECT owner_type, owner_id, schema_version, has_value, value_text, "
                                + "synchronized_from_environment, version "
                                + "FROM sys_setting WHERE setting_key = 'product.display-name'"))
                .containsEntry("owner_type", "SYSTEM")
                .containsEntry("owner_id", "1")
                .containsEntry("schema_version", 1)
                .containsEntry("has_value", true)
                .containsEntry("value_text", "Legacy Product")
                .containsEntry("synchronized_from_environment", false)
                .containsEntry("version", 8L);
        assertThat(
                jdbc.queryForMap(
                        "SELECT owner_type, owner_id, encryption_context_version, version "
                                + "FROM sys_setting WHERE setting_key = 'migration.secret'"))
                .containsEntry("owner_type", "SYSTEM")
                .containsEntry("owner_id", "1")
                .containsEntry("encryption_context_version", 1)
                .containsEntry("version", 3L);

        Map<String, Object> authenticationSetting = jdbc.queryForMap(
                "SELECT owner_type, owner_id, schema_version, has_value, value_text, version "
                        + "FROM sys_setting WHERE setting_key = 'authentication.account-policy'");
        assertThat(authenticationSetting)
                .containsEntry("owner_type", "account")
                .containsEntry("owner_id", "91002")
                .containsEntry("schema_version", 1)
                .containsEntry("has_value", true)
                .containsEntry("version", 1L);
        JsonNode authenticationValue = new ObjectMapper().readTree((String) authenticationSetting.get("value_text"));
        assertThat(authenticationValue.path("loginFactorCount").intValue()).isEqualTo(2);
        assertThat(authenticationValue.path("sensitiveActionFactorCount").intValue()).isOne();
        assertThat(authenticationValue.path("reusableAuthenticationMaximumAgeSeconds").longValue()).isEqualTo(600L);
        assertThat(authenticationValue.path("methods").findValuesAsText("methodCode"))
                .containsExactly("password", "totp");
        assertThat(
                jdbc.queryForList(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name IN ("
                                + "'app_account_authentication_settings', 'app_account_authentication_method')",
                        String.class))
                .isEmpty();
    }

    @Test
    @DisplayName("Timescale V2 和 V3 迁移 V1 事件与绑定代数列")
    void migratesTimescaleScheduleFailureEventsAndBindingGeneration() throws Exception {
        String location = "classpath:META-INF/sca4dj/timescale/migration";
        migrateToV1(TIMESCALE, location);
        JdbcTemplate jdbc = jdbc(TIMESCALE);
        jdbc.update(
                "INSERT INTO iot_event ("
                        + "occurred_at, message_id, device_id, binding_revision, event_code, source_event_id, "
                        + "data_json, device_occurred_at, received_at) VALUES ("
                        + "CURRENT_TIMESTAMP, 95001, 93001, 1, '$schedule.execution', '94001:one', "
                        + "CAST(? AS JSONB), FALSE, CURRENT_TIMESTAMP)",
                "{\"scheduleId\":94001,\"plannedAt\":\"2026-08-15T00:00:00Z\","
                        + "\"triggeredAt\":\"2026-08-15T00:00:01Z\",\"status\":\"FAILED\","
                        + "\"failure\":\"设备报告执行失败\"}");

        migrateLatest(TIMESCALE, location);

        String data = jdbc.queryForObject(
                "SELECT data_json::TEXT FROM iot_event WHERE message_id = 95001",
                String.class);
        JsonNode migrated = new ObjectMapper().readTree(data);
        assertThat(migrated.has("failure")).isFalse();
        assertThat(migrated.path("failureCode").textValue()).isEqualTo("DEVICE_REPORTED_FAILURE");
        assertThat(
                jdbc.queryForObject(
                        "SELECT binding_generation FROM iot_event WHERE message_id = 95001",
                        Long.class))
                .isEqualTo(1L);
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_name IN ('iot_telemetry_sample', 'iot_event') "
                                + "AND column_name = 'binding_revision'",
                        String.class))
                .isEmpty();
    }

    /** 把指定数据库的 Flyway 迁移到 V1，供测试写入旧结构数据。 */
    private static void migrateToV1(PostgreSQLContainer<?> database, String location) {
        flyway(database, location, MigrationVersion.fromVersion("1")).migrate();
    }

    /** 把指定数据库从 V1 迁移到当前最新版本。 */
    private static void migrateLatest(PostgreSQLContainer<?> database, String location) {
        flyway(database, location, null).migrate();
    }

    /** 创建使用指定迁移位置和可选目标版本的 Flyway。 */
    private static Flyway flyway(
            PostgreSQLContainer<?> database,
            String location,
            MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations(location);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    /** 创建连接到测试容器的 JdbcTemplate。 */
    private static JdbcTemplate jdbc(PostgreSQLContainer<?> database) {
        return new JdbcTemplate(
                new DriverManagerDataSource(
                        database.getJdbcUrl(),
                        database.getUsername(),
                        database.getPassword()));
    }
}
