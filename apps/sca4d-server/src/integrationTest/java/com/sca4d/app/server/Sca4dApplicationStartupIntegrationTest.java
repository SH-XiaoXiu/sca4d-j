package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.sca4d.app.server.authentication.AccountAuthenticationSettingConfiguration;
import com.sca4d.app.server.authentication.AccountAuthenticationSettings;
import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.app.server.iot.DeviceCreationRequestRecord;
import com.sca4d.app.server.iot.IotProductProfile;
import com.sca4d.app.server.iot.IotProfileStatus;
import com.sca4d.app.server.mapper.IotApplicationMapper;
import com.sca4d.auth.passkey.credential.PasskeyCredential;
import com.sca4d.auth.passkey.credential.mapper.PasskeyAccountState;
import com.sca4d.auth.passkey.credential.mapper.PasskeyCredentialMapper;
import com.sca4d.core.api.enums.BaseEnum;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import com.sca4d.core.spring.dictionary.DatabaseDictionaryOption;
import com.sca4d.core.spring.dictionary.DictionaryMapper;
import com.sca4d.core.spring.setting.SettingMapper;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.config.IotTimescale;
import com.sca4d.iot.persistence.EncodedDownlinkMessage;
import com.sca4d.iot.persistence.EncodedThingModel;
import com.sca4d.iot.persistence.mapper.DeviceDownlinkMapper;
import com.sca4d.iot.persistence.mapper.ThingModelMapper;
import com.sca4d.iot.timescale.persistence.EncodedTelemetrySample;
import com.sca4d.iot.timescale.persistence.TimescaleMapper;
import com.sca4d.iot.timescale.persistence.TimescaleTelemetrySample;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 验证完整 Sca4d Server 能同时装配默认数据库、IoT 历史数据库和启用 AOP 的 EMQX 回调。 */
@SpringBootTest(classes = Sca4dApplication.class)
@ActiveProfiles("integration")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SuppressWarnings({
        "DuplicatedCode",
        "SqlDialectInspection",
        "SpringJavaInjectionPointsAutowiringInspection"
})
class Sca4dApplicationStartupIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final PostgreSQLContainer<?> TIMESCALE = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.29.0-pg17-oss")
                    .asCompatibleSubstituteFor("postgres"));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-alpine")
            .withAdminUser("sca4dj")
            .withAdminPassword("sca4dj");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> EMQX = new GenericContainer<>(DockerImageName.parse("emqx/emqx:5.8.9"))
            .withExposedPorts(1883)
            .waitingFor(Wait.forListeningPort());

    /** 把完整应用的外部依赖绑定到本测试拥有的隔离容器。 */
    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.dynamic.datasource.main.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.main.username", POSTGRES::getUsername);
        registry.add("spring.datasource.dynamic.datasource.main.password", POSTGRES::getPassword);

        registry.add("sca4dj.iot.timescale.enabled", () -> true);
        registry.add("spring.datasource.dynamic.datasource.iot-history.url", TIMESCALE::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.iot-history.username", TIMESCALE::getUsername);
        registry.add("spring.datasource.dynamic.datasource.iot-history.password", TIMESCALE::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "sca4dj");
        registry.add("spring.rabbitmq.password", () -> "sca4dj");

        registry.add("sca4dj.iot.emqx.enabled", () -> true);
        registry.add("sca4dj.iot.emqx.host", EMQX::getHost);
        registry.add("sca4dj.iot.emqx.port", () -> EMQX.getMappedPort(1883));
        registry.add("sca4dj.iot.emqx.management-base-url", () -> "http://localhost:18083");
        registry.add("sca4dj.iot.emqx.management-api-key", () -> "integration-key");
        registry.add("sca4dj.iot.emqx.management-secret", () -> "integration-secret");
        registry.add("app.iot.emqx.webhook-secret", () -> "integration-webhook-secret");
        registry.add("sca4dj.iam.bootstrap.enabled", () -> false);
        registry.add("sca4dj.auth.password.bootstrap.enabled", () -> false);
        registry.add("sca4dj.bootstrap.enabled", () -> false);
    }

    @Autowired
    private DynamicRoutingDataSource dataSource;

    @Autowired
    private Flyway defaultFlyway;

    @Autowired
    @IotTimescale
    private Flyway historyFlyway;

    @Autowired
    private SqlSessionFactory defaultSqlSessionFactory;

    @Autowired
    @IotTimescale
    private SqlSessionFactory historySqlSessionFactory;

    @Autowired
    private SqlSessionTemplate defaultSqlSessionTemplate;

    @Autowired
    @IotTimescale
    private SqlSessionTemplate historySqlSessionTemplate;

    @Autowired
    private SettingMapper settingMapper;

    @Autowired
    private DictionaryMapper dictionaryMapper;

    @Autowired
    private AccountAuthenticationSettingConfiguration settings;

    @Autowired
    private PasskeyCredentialMapper passkeyCredentialMapper;

    @Autowired
    private IotApplicationMapper iotApplicationMapper;

    @Autowired
    private ThingModelMapper thingModelMapper;

    @Autowired
    private DeviceDownlinkMapper deviceDownlinkMapper;

    @Autowired
    @IotTimescale
    private TimescaleMapper timescaleMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("默认数据库与 IoT 历史数据库分别完成装配和迁移")
    void startsWithIsolatedDatabaseInfrastructure() {
        assertThat(dataSource.getDataSource("main"))
                .isNotSameAs(dataSource.getDataSource(IotTimescale.DATASOURCE));
        assertThat(defaultFlyway).isNotSameAs(historyFlyway);
        assertThat(defaultSqlSessionFactory).isNotSameAs(historySqlSessionFactory);
        assertThat(defaultSqlSessionTemplate).isNotSameAs(historySqlSessionTemplate);

        assertThat(defaultFlyway.info().applied()).hasSize(2);
        assertThat(defaultFlyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(historyFlyway.info().applied()).hasSize(1);
        assertThat(historyFlyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(settingMapper.select(TreeCode.of("SYSTEM"), "1", List.of("startup.integration.missing"))).isEmpty();
        // 直接调用 Mapper 时显式选择历史库，生产代码由 @DS 在服务边界选择。
        DynamicDataSourceContextHolder.push(IotTimescale.DATASOURCE);
        try {
            assertThat(
                    timescaleMapper.selectTelemetry(
                            1L,
                            "temperature",
                            Instant.parse("2026-08-08T00:00:00Z"),
                            Instant.parse("2026-08-08T00:01:00Z"),
                            1))
                    .isEmpty();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("两套会话工厂不交叉注册 Mapper")
    void keepsMapperRegistriesIsolated() {
        assertThat(defaultSqlSessionFactory.getConfiguration().hasMapper(SettingMapper.class)).isTrue();
        assertThat(defaultSqlSessionFactory.getConfiguration().hasMapper(TimescaleMapper.class)).isFalse();
        assertThat(historySqlSessionFactory.getConfiguration().hasMapper(TimescaleMapper.class)).isTrue();
        assertThat(historySqlSessionFactory.getConfiguration().hasMapper(SettingMapper.class)).isFalse();
    }

    @Test
    @DisplayName("两套会话工厂使用相同的直接映射和 Mapper 类型约束")
    void usesSharedMyBatisConventions() {
        assertMyBatisConventions(defaultSqlSessionFactory);
        assertMyBatisConventions(historySqlSessionFactory);
    }

    @Test
    @DisplayName("主数据库直接映射认证设置、Passkey 二进制值和应用 IoT 状态")
    void mapsDefaultDatabaseValues() {
        long accountId = 9_100_001L;
        TreeCode methodCode = TreeCode.ofSlash("AUTHENTICATION/PASSKEY");
        byte[] userHandle = new byte[32];
        for (int index = 0; index < userHandle.length; index++) {
            userHandle[index] = (byte) index;
        }
        byte[] credentialId = new byte[] {10, 20, 30, 40};
        byte[] publicKeyCose = new byte[] {50, 60, 70, 80};

        assertThat(passkeyCredentialMapper.selectAccount(accountId, null)).isNull();
        assertThat(passkeyCredentialMapper.insertAccountIfAbsent(accountId, userHandle)).isOne();
        PasskeyAccountState account = passkeyCredentialMapper.selectAccount(accountId, null);
        assertThat(account).isNotNull();
        assertThat(account.userHandle()).containsExactly(userHandle);
        assertThat(account.version().value()).isZero();

        Long credentialRecordId = passkeyCredentialMapper.insertCredentialIfAbsent(
                accountId,
                credentialId,
                publicKeyCose,
                7,
                Set.of("internal", "usb"),
                true,
                false,
                "Integration key");
        assertThat(credentialRecordId).isNotNull().isPositive();
        PasskeyCredential credential = passkeyCredentialMapper.selectCredentials(null, credentialId, null)
                .stream()
                .findFirst()
                .orElse(null);
        assertThat(credential).isNotNull();
        assertThat(credential.userHandle()).containsExactly(userHandle);
        assertThat(credential.credentialId()).containsExactly(credentialId);
        assertThat(credential.publicKeyCose()).containsExactly(publicKeyCose);
        assertThat(credential.signatureCount()).isEqualTo(7);

        SettingOwner accountOwner = new SettingOwner(Account.RESOURCE_TYPE, Long.toString(accountId));
        SettingValue<AccountAuthenticationSettings> savedAuthentication = settings.ACCOUNT_POLICY.replace(
                accountOwner,
                new AccountAuthenticationSettings(
                        2,
                        1,
                        Duration.ofSeconds(300),
                        List.of(new AuthenticationMethodSettings(methodCode, true, false, true))));
        assertThat(savedAuthentication.value().loginFactorCount()).isEqualTo(2);
        assertThat(savedAuthentication.value().reusableAuthenticationMaximumAge().toSeconds()).isEqualTo(300);
        assertThat(savedAuthentication.value().methods().values()).singleElement().satisfies(method -> {
            assertThat(method.methodCode()).isEqualTo(methodCode);
            assertThat(method.loginStartEnabled()).isTrue();
            assertThat(method.loginAdditionalEnabled()).isFalse();
            assertThat(method.sensitiveActionEnabled()).isTrue();
        });

        String dictionaryKey = "startup.integration.dictionary";
        LocalizedFields dictionaryI18n = LocalizedFields.of(
                Map.of(Locale.SIMPLIFIED_CHINESE, Map.of("label", "就绪")));
        assertThat(dictionaryMapper.insertDictionary(dictionaryKey, "Startup dictionary", true, 10)).isOne();
        assertThat(
                dictionaryMapper.insertOption(
                        dictionaryKey,
                        "ready",
                        "Ready",
                        dictionaryI18n,
                        true,
                        20))
                .isOne();
        List<DatabaseDictionaryOption> dictionaryOptions = dictionaryMapper.selectOptions(dictionaryKey);
        assertThat(dictionaryOptions).singleElement().satisfies(option -> {
            assertThat(option.value()).isEqualTo("ready");
            assertThat(option.label()).isEqualTo("Ready");
            assertThat(option.i18n().find(Locale.SIMPLIFIED_CHINESE, "label")).contains("就绪");
            assertThat(option.enabled()).isTrue();
            assertThat(option.sortOrder()).isEqualTo(20);
        });

        long productId = 9_200_001L;
        String productCode = "startup-integration-product";
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        jdbc.update(
                "INSERT INTO iot_product (id, code, i18n, created_at, updated_at) "
                        + "VALUES (?, ?, CAST(? AS JSONB), ?, ?)",
                productId,
                productCode,
                "{\"zh-CN\":{\"displayName\":\"Startup integration product\"}}",
                Timestamp.from(now),
                Timestamp.from(now));
        assertThat(iotApplicationMapper.insertProductProfile(productId, IotProfileStatus.DISABLED)).isOne();
        IotProductProfile product = iotApplicationMapper.selectProductProfile(productId);
        assertThat(product).isNotNull();
        assertThat(product.status()).isEqualTo(IotProfileStatus.DISABLED);

        String thingModelHash = "1".repeat(64);
        assertThat(
                thingModelMapper.insertThingModel(
                        productId,
                        1,
                        "{}",
                        thingModelHash,
                        LocalizedFields.fromJson(
                                Map.of(
                                        "zh-CN",
                                        Map.of("/displayName", "Startup integration product"))),
                        now))
                .isOne();
        EncodedThingModel thingModel = thingModelMapper.selectThingModels(productCode, 1)
                .stream()
                .findFirst()
                .orElse(null);
        assertThat(thingModel).isNotNull();
        assertThat(thingModel.version()).isOne();
        assertThat(thingModel.modelJson()).isEqualTo("{}");
        assertThat(thingModel.contentHash()).isEqualTo(thingModelHash);

        String idempotencyKey = "startup-integration-device-creation";
        String requestHash = "0".repeat(64);
        byte[] responseCiphertext = new byte[] {0, 1, -1, 127};
        Instant expiresAt = now.plusSeconds(600);
        assertThat(
                iotApplicationMapper.insertPendingDeviceCreation(
                        idempotencyKey,
                        requestHash,
                        expiresAt))
                .isOne();
        assertThat(
                iotApplicationMapper.completeDeviceCreation(
                        idempotencyKey,
                        requestHash,
                        "integration-key",
                        responseCiphertext))
                .isOne();
        DeviceCreationRequestRecord creation = iotApplicationMapper.selectCompletedDeviceCreation(
                idempotencyKey,
                now.plusSeconds(1));
        assertThat(creation).isNotNull();
        assertThat(creation.responseCiphertext()).containsExactly(responseCiphertext);
    }

    @Test
    @DisplayName("TimescaleDB 直接映射 Telemetry 样本")
    void mapsTimescaleValues() {
        long deviceId = 9_300_001L;
        long messageId = 9_300_002L;
        Instant occurredAt = Instant.parse("2026-08-08T00:00:30Z");

        DynamicDataSourceContextHolder.push(IotTimescale.DATASOURCE);
        try {
            assertThat(
                    timescaleMapper.insertTelemetrySamples(
                            List.of(
                                    new TimescaleTelemetrySample(
                                            messageId,
                                            0,
                                            deviceId,
                                            1,
                                            "temperature",
                                            "23.5",
                                            occurredAt,
                                            occurredAt))))
                    .isOne();
            List<EncodedTelemetrySample> samples = timescaleMapper.selectTelemetry(
                    deviceId,
                    "temperature",
                    occurredAt.minusSeconds(1),
                    occurredAt.plusSeconds(1),
                    10);
            assertThat(samples).singleElement().satisfies(sample -> {
                assertThat(sample.propertyCode()).isEqualTo("temperature");
                assertThat(sample.valueJson()).isEqualTo("23.5");
            });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("默认数据库批量映射指定标识的下行消息")
    void mapsDownlinksByMessageIds() {
        long firstMessageId = 9_400_001L;
        long secondMessageId = 9_400_002L;
        long deviceId = 9_400_003L;
        assertThat(
                deviceDownlinkMapper.insertDownlinkMessage(
                        firstMessageId,
                        deviceId,
                        1L,
                        DeviceDownlinkKind.COMMAND,
                        "first-command",
                        null,
                        null,
                        null,
                        "{}",
                        TreeCode.of("NONE"),
                        30_000L,
                        TreeCode.of("NONE"),
                        null))
                .isOne();
        assertThat(
                deviceDownlinkMapper.insertDownlinkMessage(
                        secondMessageId,
                        deviceId,
                        1L,
                        DeviceDownlinkKind.COMMAND,
                        "second-command",
                        null,
                        null,
                        null,
                        "{}",
                        TreeCode.of("NONE"),
                        30_000L,
                        TreeCode.of("NONE"),
                        null))
                .isOne();

        assertThat(
                deviceDownlinkMapper.selectDownlinkMessages(
                        List.of(secondMessageId, firstMessageId, 9_400_004L)))
                .extracting(EncodedDownlinkMessage::messageId)
                .containsExactly(firstMessageId, secondMessageId);
    }

    /** 检查一个会话工厂的公共映射配置和已经注册的 Mapper 类型。 */
    private static void assertMyBatisConventions(SqlSessionFactory factory) {
        Configuration configuration = factory.getConfiguration();
        assertThat(configuration.isMapUnderscoreToCamelCase()).isTrue();
        assertThat(configuration.isArgNameBasedConstructorAutoMapping()).isTrue();

        for (Class<?> mapper : configuration.getMapperRegistry().getMappers()) {
            for (java.lang.reflect.Method method : mapper.getMethods()) {
                Class<?> returnType = method.getReturnType();
                assertThat(returnType.isArray() && returnType != byte[].class)
                        .as("Mapper 只能用 List 返回集合: %s#%s", mapper.getName(), method.getName())
                        .isFalse();
                if (Collection.class.isAssignableFrom(returnType)) {
                    assertThat(returnType)
                            .as("Mapper 集合返回值必须是 List: %s#%s", mapper.getName(), method.getName())
                            .isEqualTo(List.class);
                }
                assertMappedType(method.getGenericReturnType(), mapper, method.getName());
                for (Type parameterType : method.getGenericParameterTypes()) {
                    assertMappedType(parameterType, mapper, method.getName());
                }
            }
        }

        for (ResultMap resultMap : configuration.getResultMaps()) {
            Class<?> resultType = resultMap.getType();
            assertThat(resultType.getSimpleName())
                    .as("MyBatis 查询结果类型不能使用 Row 后缀: %s", resultMap.getId())
                    .doesNotEndWith("Row");
            assertMappedClass(resultType, resultType, resultMap.getId());
            if (resultType.getName().startsWith("com.sca4d.")) {
                for (Constructor<?> constructor : resultType.getDeclaredConstructors()) {
                    for (Class<?> parameterType : constructor.getParameterTypes()) {
                        assertMappedClass(parameterType, resultType, resultMap.getId());
                    }
                }
            }
        }
    }

    /** 递归检查 Mapper 方法签名中的数组和枚举类型。 */
    private static void assertMappedType(Type type, Class<?> owner, String member) {
        if (type instanceof Class<?> mappedClass) {
            assertMappedClass(mappedClass, owner, member);
            return;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            assertMappedType(parameterizedType.getRawType(), owner, member);
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                assertMappedType(argument, owner, member);
            }
            return;
        }
        if (type instanceof GenericArrayType arrayType) {
            assertMappedType(arrayType.getGenericComponentType(), owner, member);
            return;
        }
        if (type instanceof WildcardType wildcardType) {
            for (Type bound : wildcardType.getUpperBounds()) {
                assertMappedType(bound, owner, member);
            }
        }
    }

    /** 检查 MyBatis 边界中的数组只表达二进制，并要求枚举实现 `BaseEnum`。 */
    private static void assertMappedClass(Class<?> type, Class<?> owner, String member) {
        if (type.isArray()) {
            assertThat(type)
                    .as("MyBatis 数组只能表达二进制: %s#%s", owner.getName(), member)
                    .isEqualTo(byte[].class);
        }
        if (type.isEnum()) {
            assertThat(BaseEnum.class.isAssignableFrom(type))
                    .as("MyBatis 枚举必须实现 BaseEnum: %s#%s -> %s", owner.getName(), member, type.getName())
                    .isTrue();
        }
    }
}
