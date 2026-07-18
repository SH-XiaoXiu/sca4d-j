package com.sca4d.identity.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.ClusterIdGenerator;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.mapper.ExternalIdentityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

/** 在真实 PostgreSQL 17 中验证 ExternalIdentity 条件写入、查询映射和唯一约束转换。 */
@EnabledIfEnvironmentVariable(named = "SCA4DJ_POSTGRES_INTEGRATION", matches = "true")
@SpringBootTest(classes = ExternalIdentityRepositoryIntegrationTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ExternalIdentityRepositoryIntegrationTest {

    @Autowired
    private ExternalIdentityRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdGenerator ids;

    private long providerId;
    private long otherProviderId;
    private long firstSubjectId;
    private long secondSubjectId;
    private long firstAccountId;
    private long secondAccountId;
    private long tenantAccountId;

    @BeforeEach
    void insertActiveGlobalProviderAndAccounts() {
        providerId = ids.nextId();
        otherProviderId = ids.nextId();
        firstSubjectId = ids.nextId();
        secondSubjectId = ids.nextId();
        firstAccountId = ids.nextId();
        secondAccountId = ids.nextId();
        tenantAccountId = ids.nextId();

        jdbc.update(
                "INSERT INTO iam_subject (id, display_name, status) VALUES (?, ?, 'ACTIVE'), (?, ?, 'ACTIVE')",
                firstSubjectId,
                "External Identity One",
                secondSubjectId,
                "External Identity Two");
        jdbc.update(
                "INSERT INTO iam_account (id, subject_id, scope_type, scope_id, status) "
                        + "VALUES (?, ?, 'GLOBAL', NULL, 'ACTIVE'), (?, ?, 'GLOBAL', NULL, 'ACTIVE'), "
                        + "(?, ?, 'TENANT', ?, 'ACTIVE')",
                firstAccountId,
                firstSubjectId,
                secondAccountId,
                secondSubjectId,
                tenantAccountId,
                firstSubjectId,
                ids.nextId());
        jdbc.update(
                "INSERT INTO iam_identity_provider "
                        + "(id, scope_type, scope_id, code, name, protocol, registration_policy, status) "
                        + "VALUES (?, 'GLOBAL', NULL, ?, 'Integration OIDC', 'OIDC', 'JIT', 'ACTIVE'), "
                        + "(?, 'GLOBAL', NULL, ?, 'Integration OAuth2', 'OAUTH2', 'JIT', 'DISABLED')",
                providerId,
                "integration-" + providerId,
                otherProviderId,
                "integration-" + otherProviderId);
    }

    @AfterEach
    void deleteProviderAndAccounts() {
        jdbc.update(
                "DELETE FROM iam_external_identity WHERE provider_id IN (?, ?)",
                providerId,
                otherProviderId);
        jdbc.update(
                "DELETE FROM iam_identity_provider WHERE id IN (?, ?)",
                providerId,
                otherProviderId);
        jdbc.update(
                "DELETE FROM iam_account WHERE id IN (?, ?, ?)",
                firstAccountId,
                secondAccountId,
                tenantAccountId);
        jdbc.update(
                "DELETE FROM iam_subject WHERE id IN (?, ?)",
                firstSubjectId,
                secondSubjectId);
    }

    @Test
    void shouldCreateAndRefreshAuthenticationProfileWithoutChangingBindingVersion() {
        ExternalIdentityLink created = repository.create(
                providerId,
                firstAccountId,
                new ExternalIdentityProfile(
                        "external-1", "Alice", "alice@example.com", true, "https://example.com/alice.png"));
        OffsetDateTime firstAuthentication = created.getIdentity().getLastAuthenticatedAt();

        ExternalIdentityLink updated = repository.recordAuthentication(
                created.getIdentity().getIdentityId(),
                providerId,
                created.getIdentity().getVersion(),
                new ExternalIdentityProfile(
                        "external-1", "Alice Updated", null, false, null));

        assertThat(updated.getIdentity().getVersion()).isZero();
        assertThat(updated.getIdentity().getProfile().getDisplayName()).isEqualTo("Alice Updated");
        assertThat(updated.getIdentity().getProfile().getEmail()).isNull();
        assertThat(updated.getIdentity().getLastAuthenticatedAt()).isAfterOrEqualTo(firstAuthentication);
        assertThat(repository.findLink(providerId, "external-1")
                .getIdentity()
                .getIdentityId())
                .isEqualTo(created.getIdentity().getIdentityId());
    }

    @Test
    void shouldRejectAccountOutsideProviderScope() {
        assertThatThrownBy(() -> repository.create(
                providerId,
                tenantAccountId,
                new ExternalIdentityProfile("external-1", null, null, false, null)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE));
    }

    @Test
    void shouldTranslateProviderSubjectUniqueness() {
        repository.create(
                providerId,
                firstAccountId,
                new ExternalIdentityProfile("external-1", null, null, false, null));

        assertThatThrownBy(() -> repository.create(
                providerId,
                secondAccountId,
                new ExternalIdentityProfile("external-1", null, null, false, null)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED));
    }

    @Test
    void shouldTranslateProviderAccountUniqueness() {
        repository.create(
                providerId,
                firstAccountId,
                new ExternalIdentityProfile("external-1", null, null, false, null));

        assertThatThrownBy(() -> repository.create(
                providerId,
                firstAccountId,
                new ExternalIdentityProfile("external-2", null, null, false, null)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_ACCOUNT_ALREADY_LINKED));
    }

    @Test
    void shouldOnlyCountOtherIdentitiesWhoseProviderIsActive() {
        ExternalIdentityLink current = repository.create(
                providerId,
                firstAccountId,
                new ExternalIdentityProfile("external-1", null, null, false, null));
        jdbc.update(
                "UPDATE iam_identity_provider SET status = 'ACTIVE' WHERE id = ?",
                otherProviderId);
        repository.create(
                otherProviderId,
                firstAccountId,
                new ExternalIdentityProfile("external-2", null, null, false, null));
        jdbc.update(
                "UPDATE iam_identity_provider SET status = 'DISABLED' WHERE id = ?",
                otherProviderId);

        assertThat(repository.hasOtherActiveLoginIdentity(
                firstAccountId, current.getIdentity().getIdentityId()))
                .isFalse();

        jdbc.update(
                "UPDATE iam_identity_provider SET status = 'ACTIVE' WHERE id = ?",
                otherProviderId);
        assertThat(repository.hasOtherActiveLoginIdentity(
                firstAccountId, current.getIdentity().getIdentityId()))
                .isTrue();
    }

    /** 仅装配 ExternalIdentity 持久化测试需要的 DataSource、MyBatis 和 Repository。 */
    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            MybatisFlexAutoConfiguration.class
    })
    @MapperScan(basePackageClasses = ExternalIdentityMapper.class)
    @Import(ExternalIdentityRepository.class)
    static class TestApplication {

        /** @return 集成测试专用的集群 ID 生成器 */
        @Bean
        IdGenerator idGenerator() {
            return new ClusterIdGenerator(31, 31);
        }
    }
}
