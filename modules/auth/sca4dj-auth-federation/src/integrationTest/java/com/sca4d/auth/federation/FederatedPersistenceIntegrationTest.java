package com.sca4d.auth.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sca4d.auth.federation.identity.FederatedIdentityRepository;
import com.sca4d.auth.federation.identity.mapper.FederatedIdentityMapper;
import com.sca4d.auth.federation.provider.DefaultFederatedProviderRepository;
import com.sca4d.auth.federation.provider.FederatedProviderRepository;
import com.sca4d.auth.federation.provider.mapper.ProviderMapper;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.LongValueTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

/** 验证联邦身份资料更新、账号清理及协议配置替换的持久化不变量。 */
@Testcontainers
class FederatedPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static FederatedIdentityRepository identities;

    private static FederatedProviderRepository providers;

    private static JdbcTemplate jdbc;

    /** 使用真实 Mapper、事务和影响行数检查。 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @MapperScan(basePackageClasses = {FederatedIdentityMapper.class, ProviderMapper.class})
    static class PersistenceConfiguration {
    }

    /** 执行正式 schema 并装配测试持久化上下文。 */
    @BeforeAll
    static void initialize() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/auth-federation.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.getTypeHandlerRegistry()
                .register(
                        Version.class,
                        JdbcType.BIGINT,
                        new LongValueTypeHandler<>(Version::new, Version::value));
        factory.setConfiguration(configuration);
        factory.setPlugins(new AffectedRowsInterceptor());
        factory.setMapperLocations(
                new ClassPathResource("mapper/auth/federation/FederatedIdentityMapper.xml"),
                new ClassPathResource("mapper/auth/federation/ProviderMapper.xml"));
        SqlSessionFactory sessions = factory.getObject();
        context = new AnnotationConfigApplicationContext();
        context.register(PersistenceConfiguration.class);
        context.registerBean(SqlSessionFactory.class, () -> sessions);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(AffectedRowsAspect.class);
        context.registerBean(FederatedIdentityRepository.class);
        context.registerBean(
                FederatedProviderRepository.class,
                () -> new DefaultFederatedProviderRepository(
                        context.getBean(ProviderMapper.class),
                        new Sca4djMapper(),
                        new FederatedSecretCryptography(mock(SecretKeyring.class)),
                        VersionCondition::optimistic));
        context.refresh();
        identities = context.getBean(FederatedIdentityRepository.class);
        providers = context.getBean(FederatedProviderRepository.class);
    }

    /** 释放测试上下文。 */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    /** 每例重建两个不同 Provider，避免账号绑定的唯一性互相干扰。 */
    @BeforeEach
    void reset() {
        jdbc.execute(
                "TRUNCATE auth_federated_identity, auth_federated_provider_configuration, auth_federated_provider RESTART IDENTITY");
        jdbc.update(
                "INSERT INTO auth_federated_provider (id, code, protocol, i18n) VALUES (1, 'first', 'OIDC', '{}'::jsonb), (2, 'second', 'OIDC', '{}'::jsonb)");
    }

    @Test
    @DisplayName("刷新资料明确清空字段且不递增绑定版本，解绑后拒绝旧资料覆盖")
    void profileRefreshPreservesBindingEvidence() {
        FederatedProvider provider = providers.findByCode("first").orElseThrow();
        FederatedIdentity identity = identities.insert(
                provider,
                101,
                new FederatedProfile(
                        "https://issuer.example",
                        "subject",
                        "名称",
                        "user@example.test",
                        true,
                        "https://avatar.example"));
        FederatedProfile cleared = new FederatedProfile("https://issuer.example", "subject", null, null, false, null);
        identities.updateProfile(identity, cleared);
        FederatedIdentity saved = identities.find(provider, cleared);
        assertThat(saved).isNotNull();
        assertThat(saved.version()).isEqualTo(identity.version());
        assertThat(saved.profile().email()).isNull();
        assertThat(saved.profile().displayName()).isNull();
        assertThat(saved.profile().avatarUrl()).isNull();
        assertThat(saved.profile().emailVerified()).isFalse();
        identities.verifyCurrentBinding(identity.id(), identity.accountId(), identity.version());
        identities.delete(identity.id(), identity.accountId(), identity.version());
        assertThatThrownBy(() -> identities.updateProfile(identity, cleared))
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    @DisplayName("账号清理删除多个 Provider 的全部绑定且保持幂等")
    void erasesEveryBindingOfOnlyTheRequestedAccount() {
        FederatedProfile profile = new FederatedProfile(null, "subject", null, null, false, null);
        identities.insert(providers.findByCode("first").orElseThrow(), 101, profile);
        identities.insert(providers.findByCode("second").orElseThrow(), 101, profile);
        identities.insert(
                providers.findByCode("first").orElseThrow(),
                102,
                new FederatedProfile(null, "other", null, null, false, null));
        identities.erase(101);
        identities.erase(101);
        assertThat(identities.findByAccount(101)).isEmpty();
        assertThat(identities.findByAccount(102)).hasSize(1);
    }

    @Test
    @DisplayName("配置插入失败时 Provider 版本和原协议配置一并恢复")
    void configurationReplacementIsAtomic() {
        FederatedProvider saved = providers
                .saveConfiguration("first", configuration("https://issuer.example/authorize"));
        assertThatThrownBy(() -> providers.saveConfiguration("first", configuration("x".repeat(2049))))
                .isInstanceOf(RuntimeException.class);
        FederatedProvider after = providers.findByCode("first").orElseThrow();
        assertThat(after.version()).isEqualTo(saved.version());
        assertThat(providers.configuration(after).authorizationEndpoint())
                .isEqualTo("https://issuer.example/authorize");
        providers.replace("first", false, 0, FederatedUnlinkedIdentityAction.REJECT, false, Map.of(), null);
        providers.changeStatus("first", FederatedProviderStatus.ACTIVE);
        assertThat(providers.findLoginProviders()).isEmpty();
        assertThat(providers.findByCode("first").orElseThrow().status()).isEqualTo(FederatedProviderStatus.ACTIVE);
        assertThat(providers.findByCode("first").orElseThrow().iconReference()).isNull();
    }

    /** 创建无需 Client Secret 的协议配置，只改变授权端点。 */
    private static FederatedProviderConfiguration configuration(String authorizationEndpoint) {
        return new FederatedProviderConfiguration(
                FederatedProviderProtocol.OIDC,
                "https://issuer.example",
                "client",
                null,
                authorizationEndpoint,
                "https://issuer.example/token",
                null,
                null,
                FederatedClientAuthentication.ONE,
                List.of("openid"),
                Map.of(),
                Map.of());
    }
}
