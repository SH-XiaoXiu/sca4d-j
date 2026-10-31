package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionPreconditionFailedException;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.RequestVersionConditions;
import com.sca4d.iam.base.PlatformAccountEntry;
import com.sca4d.iam.base.PlatformAccountQuery;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.base.PlatformPrincipalLoader;
import com.sca4d.iam.base.PlatformSessionIdentityLoader;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.base.rbac.PlatformRoleDetails;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.rbac.PrincipalRoles;
import com.sca4d.iam.rbac.RbacErrorCode;
import com.sca4d.iam.rbac.Role;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Testcontainers
@Sql(statements = "TRUNCATE TABLE auth_password_secret, auth_password_credential, platform_acl_entry, platform_principal_role, platform_role_permission, platform_role, iam_account_identifier, iam_account_principal, iam_principal, iam_account, iam_subject CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SpringBootTest(classes = PlatformIdentityIntegrationTest.TestApplication.class, properties = {
        "sca4dj.iam.bootstrap.enabled=false",
        "sca4dj.auth.password.bootstrap.enabled=false",
        "spring.autoconfigure.exclude=com.sca4d.messaging.config.MessagingAutoConfiguration,com.sca4d.web.realtime.config.WebRealtimeAutoConfiguration,com.sca4d.notification.config.NotificationAutoConfiguration,com.sca4d.notification.email.config.EmailNotificationAutoConfiguration,com.sca4d.auth.email.config.EmailCodeAutoConfiguration,com.sca4d.iot.config.IotAutoConfiguration,com.sca4d.iot.config.IotWorkerAutoConfiguration,com.sca4d.iot.emqx.config.EmqxAutoConfiguration,com.sca4d.iot.config.TimescaleIotAutoConfiguration"
})
@SuppressWarnings({
        "DuplicatedCode",
        "SqlNoDataSourceInspection",
        "SqlResolve",
        "SpringJavaInjectionPointsAutowiringInspection"
}) // Testcontainers、Flyway 和 SpringBootTest 在测试启动时提供表结构及测试 Bean；重复配置用于隔离测试上下文。
class PlatformIdentityIntegrationTest {

    /**
     * 只加载已安装模块的自动配置，不扫描与身份测试无关的 Controller。
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
        /**
         * 为不执行认证流程的 IAM 集成测试声明具体 App 必须提供的固定认证策略。
         */
        @Bean
        AuthenticationPolicy testAuthenticationPolicy() {
            return new AuthenticationPolicy() {
                @Override
                public @NotNull AuthenticationRequirement requirementFor(
                        @NotNull Account account,
                        @NotNull AuthenticationActionCode actionCode) {
                    return new AuthenticationRequirement(1, null);
                }

                @Override
                public @NotNull Set<TreeCode> allowedMethods(
                        @NotNull Account account,
                        @NotNull AuthenticationActionCode actionCode,
                        @NotNull List<Authentication> completedAuthentications) {
                    return Set.of(TreeCode.ofSlash("AUTHENTICATION/PASSWORD"));
                }
            };
        }

        @Bean
        @Primary
        SessionControl testSessionControl() {
            return Mockito.mock(SessionControl.class);
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private PlatformIdentityService identities;

    @Autowired
    private IdentityService identityFacts;

    @Autowired
    private PlatformSessionIdentityLoader sessionIdentities;

    @Autowired
    private PlatformPrincipalLoader sessionPrincipals;

    @Autowired
    private PlatformRbacService rbac;

    @Autowired
    private RequestVersionConditions versionConditions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("Creates And Resolves Complete Platform Identity")
    void createsAndResolvesCompletePlatformIdentity() {
        Account account = provisionAccount("  Admin  ");
        Subject subject = identities.activeSubjectById(account.subjectId());
        List<PlatformPrincipal> principals = identities.activePrincipals(account.accountId());
        PlatformPrincipal principal = principals.getFirst();

        assertThat(account.accountId()).isGreaterThan(1);
        assertThat(subject.type()).isEqualTo(SubjectType.HUMAN);
        assertThat(principals).singleElement().extracting(PlatformPrincipal::principalId).isNotNull();
        assertThat(identities.findActiveAccounts(principal.principalId()))
                .singleElement()
                .extracting(Account::accountId)
                .isEqualTo(account.accountId());
        assertThat(identities.resolvePlatformAccount(AccountIdentifierType.USERNAME, "ＡＤＭＩＮ"))
                .get()
                .extracting(Account::accountId)
                .isEqualTo(account.accountId());
        assertThat(
                jdbc.sql("SELECT COUNT(*) FROM iam_account_principal")
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        assertThat(
                jdbc.sql("SELECT principal_type FROM iam_principal")
                        .query(String.class)
                        .single())
                .isEqualTo("iam.platform-principal");
    }

    @Test
    @DisplayName("Rejects Duplicate Normalized Username And Rolls Back Identity Chain")
    void rejectsDuplicateNormalizedUsernameAndRollsBackIdentityChain() {
        assertThat(identities.provision("same-user")).isNotNull();

        assertThatThrownBy(() -> {
            PlatformPrincipal unexpected = identities.provision("ＳＡＭＥ－ＵＳＥＲ");
            throw new AssertionError("重复规范化用户名未被拒绝: " + unexpected.principalId());
        })
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(IamErrorCode.IDENTIFIER_EXISTS));
        assertThat(
                jdbc.sql("SELECT COUNT(*) FROM iam_subject")
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Excludes Disabled Identity Facts")
    void excludesDisabledIdentityFacts() {
        Account account = provisionAccount("disabled-user");
        PlatformPrincipal principal = identities.activePrincipals(account.accountId()).getFirst();
        jdbc.sql("UPDATE iam_principal SET status = 'DISABLED' WHERE id = ?")
                .param(principal.principalId())
                .update();

        assertThatThrownBy(() -> {
            List<PlatformPrincipal> unexpected = identities.activePrincipals(account.accountId());
            throw new AssertionError("停用 Principal 仍被返回: " + unexpected);
        })
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Changes Identity Status Without Exposing Persistence Version")
    void changesIdentityStatusWithoutExposingPersistenceVersion() {
        Account account = provisionAccount("status-user");
        PlatformPrincipal principal = identities.activePrincipals(account.accountId()).getFirst();

        assertThat(identityFacts.findSubjectStatus(account.subjectId())).contains(IdentityStatus.ACTIVE);
        assertThat(identityFacts.findAccountStatus(account.accountId())).contains(IdentityStatus.ACTIVE);
        assertThat(identityFacts.findPrincipalStatus(principal.principalId(), PlatformPrincipal.class))
                .contains(IdentityStatus.ACTIVE);

        identityFacts.changeSubjectStatus(account.subjectId(), IdentityStatus.DISABLED);
        identityFacts.changeAccountStatus(account.accountId(), IdentityStatus.DISABLED);
        identityFacts.changePrincipalStatus(principal.principalId(), IdentityStatus.DISABLED);

        assertThat(identityFacts.findSubjectStatus(account.subjectId())).contains(IdentityStatus.DISABLED);
        assertThat(identityFacts.findAccountStatus(account.accountId())).contains(IdentityStatus.DISABLED);
        assertThat(identityFacts.findPrincipalStatus(principal.principalId(), PlatformPrincipal.class))
                .contains(IdentityStatus.DISABLED);
        assertThat(
                jdbc.sql("SELECT principal_type FROM iam_principal WHERE id = ?")
                        .param(principal.principalId())
                        .query(String.class)
                        .single())
                .isEqualTo("iam.platform-principal");

        assertThatThrownBy(() -> {
            Account unexpected = identities.activeAccountById(account.accountId());
            throw new AssertionError("停用 Account 仍被返回: " + unexpected.accountId());
        })
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> {
            var unexpected = identityFacts.activePrincipalById(
                    principal.principalId(),
                    PlatformPrincipal.class);
            throw new AssertionError("停用 Principal 仍被返回: " + unexpected);
        })
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Logically Deletes And Restores The Same Principal Without Removing Account Links")
    void deletesAndRestoresPrincipalWithoutRemovingAccountLinks() {
        Account account = provisionAccount("restored-principal-user");
        PlatformPrincipal principal = identities.activePrincipals(account.accountId()).getFirst();
        List<Long> principalIds = List.of(principal.principalId());

        identityFacts.deletePrincipals(principalIds, PlatformPrincipal.class);

        assertThat(identityFacts.findPrincipal(principal.principalId(), PlatformPrincipal.class)).isEmpty();
        assertThat(identityFacts.findLinkedAccountIds(principalIds).get(principal.principalId()))
                .containsExactly(account.accountId());
        assertThat(
                jdbc.sql("SELECT status || ':' || deleted FROM iam_principal WHERE id = ?")
                        .param(principal.principalId())
                        .query(String.class)
                        .single())
                .isEqualTo("DISABLED:1");
        assertThat(
                jdbc.sql("SELECT COUNT(*) FROM iam_account_principal WHERE principal_id = ?")
                        .param(principal.principalId())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);

        identityFacts.restorePrincipals(principalIds, PlatformPrincipal.class);

        assertThat(identityFacts.activePrincipalById(principal.principalId(), PlatformPrincipal.class))
                .isEqualTo(principal.principalId());
        assertThat(identities.activePrincipals(account.accountId()))
                .extracting(PlatformPrincipal::principalId)
                .containsExactly(principal.principalId());
    }

    @Test
    @DisplayName("Loads All Principals From Account Relations Without Subject Ownership Inference")
    void loadsAllPrincipalsFromAccountRelationsWithoutSubjectOwnershipInference() {
        Account firstAccount = provisionAccount("first-user");
        Account secondAccount = provisionAccount("second-user");
        PlatformPrincipal firstPrincipal = identities.activePrincipals(firstAccount.accountId()).getFirst();
        PlatformPrincipal secondPrincipal = identities.activePrincipals(secondAccount.accountId()).getFirst();
        jdbc.sql("INSERT INTO iam_account_principal (account_id, principal_id) VALUES (?, ?)")
                .param(firstAccount.accountId())
                .param(secondPrincipal.principalId())
                .update();
        jdbc.sql("INSERT INTO iam_account_principal (account_id, principal_id) VALUES (?, ?)")
                .param(secondAccount.accountId())
                .param(firstPrincipal.principalId())
                .update();

        SessionIdentity firstIdentity = sessionIdentities.load(firstAccount);
        SessionIdentity secondIdentity = sessionIdentities.load(secondAccount);
        List<? extends Principal> firstPrincipals = sessionPrincipals.load(firstIdentity);
        List<? extends Principal> secondPrincipals = sessionPrincipals.load(secondIdentity);

        assertThat(firstIdentity.subject().subjectId()).isNotEqualTo(secondIdentity.subject().subjectId());
        assertThat(firstPrincipals)
                .extracting(Principal::principalId)
                .containsExactlyInAnyOrder(firstPrincipal.principalId(), secondPrincipal.principalId());
        assertThat(secondPrincipals)
                .extracting(Principal::principalId)
                .containsExactlyInAnyOrder(firstPrincipal.principalId(), secondPrincipal.principalId());
        assertThat(firstPrincipals)
                .extracting(Principal::principalId)
                .contains(firstPrincipal.principalId());
        assertThat(secondPrincipals)
                .extracting(Principal::principalId)
                .contains(firstPrincipal.principalId());
    }

    @Test
    @DisplayName("Rejects Principal Role Replacement With A Stale Version")
    void rejectsPrincipalRoleReplacementWithStaleVersion() {
        Account account = provisionAccount("concurrent-role-user");
        PlatformPrincipal principal = identities.activePrincipals(account.accountId()).getFirst();
        Role firstRole = rbac.createRole("FIRST", roleI18n("First"), List.of()).role();
        Role secondRole = rbac.createRole("SECOND", roleI18n("Second"), List.of()).role();
        PrincipalRoles initial = rbac.findPrincipalRoles(principal.principalId());
        assertThat(initial.roles()).isEmpty();
        Version initialVersion = initial.version();

        versionConditions.bind(initialVersion);
        try {
            assertThat(rbac.replacePrincipalRoles(principal.principalId(), List.of(firstRole.roleId())).roles())
                    .extracting(Role::roleId)
                    .containsExactly(firstRole.roleId());
        } finally {
            versionConditions.clear();
        }

        versionConditions.bind(initialVersion);
        try {
            assertThatThrownBy(
                    () -> rbac.replacePrincipalRoles(
                            principal.principalId(),
                            List.of(secondRole.roleId())))
                    .isInstanceOf(VersionPreconditionFailedException.class);
        } finally {
            versionConditions.clear();
        }
        assertThat(rbac.findPrincipalRoles(principal.principalId()).roles())
                .extracting(Role::roleId)
                .containsExactly(firstRole.roleId());
    }

    @Test
    @DisplayName("Batch Role Account Changes Preserve Other Roles And Skip No Op Versions")
    void changesRoleAccountsInBatchesWithoutReplacingOtherRoles() {
        Account addedAccount = provisionAccount("batch-added-user");
        Account removedAccount = provisionAccount("batch-removed-user");
        PlatformPrincipal addedPrincipal = identities.activePrincipals(addedAccount.accountId()).getFirst();
        PlatformPrincipal removedPrincipal = identities.activePrincipals(removedAccount.accountId())
                .getFirst();
        Role target = rbac.createRole("BATCH_TARGET", roleI18n("Batch target"), List.of()).role();
        Role retained = rbac.createRole("BATCH_RETAINED", roleI18n("Batch retained"), List.of()).role();
        PrincipalRoles addedBefore = rbac.replacePrincipalRoles(
                addedPrincipal.principalId(),
                List.of(retained.roleId()));
        PrincipalRoles removedBefore = rbac.replacePrincipalRoles(
                removedPrincipal.principalId(),
                List.of(target.roleId(), retained.roleId()));
        assertThat(addedBefore.roles()).extracting(Role::roleId).containsExactly(retained.roleId());
        assertThat(removedBefore.roles())
                .extracting(Role::roleId)
                .containsExactly(retained.roleId(), target.roleId());

        Map<Long, PrincipalRoles> changed = rbac.changeRoleAccounts(
                target.roleId(),
                List.of(addedAccount.accountId()),
                List.of(removedAccount.accountId()));

        assertThat(changed.get(addedPrincipal.principalId()).roles())
                .extracting(Role::roleId)
                .containsExactly(retained.roleId(), target.roleId());
        assertThat(changed.get(removedPrincipal.principalId()).roles())
                .extracting(Role::roleId)
                .containsExactly(retained.roleId());
        assertThat(
                rbac.findRoleAccounts(
                        target.roleId(),
                        true,
                        new PlatformAccountQuery(null, null, new PageRequest(1, 20)))
                        .getItems())
                .extracting(PlatformAccountEntry::accountId)
                .containsExactly(addedAccount.accountId());

        Map<Long, Version> versions = Map.of(
                addedPrincipal.principalId(),
                changed.get(addedPrincipal.principalId()).version(),
                removedPrincipal.principalId(),
                changed.get(removedPrincipal.principalId()).version());
        Map<Long, PrincipalRoles> unchanged = rbac.changeRoleAccounts(
                target.roleId(),
                List.of(addedAccount.accountId()),
                List.of(removedAccount.accountId()));

        assertThat(unchanged.get(addedPrincipal.principalId()).version())
                .isEqualTo(versions.get(addedPrincipal.principalId()));
        assertThat(unchanged.get(removedPrincipal.principalId()).version())
                .isEqualTo(versions.get(removedPrincipal.principalId()));
    }

    @Test
    @DisplayName("Protected Role Account Removal Rolls Back The Whole Batch")
    void rejectsProtectedRoleAccountRemovalBeforeBatchWrites() {
        Account protectedAccount = provisionAccount("protected-role-user");
        Account addedAccount = provisionAccount("protected-batch-added-user");
        PlatformPrincipal protectedPrincipal = identities
                .activePrincipals(protectedAccount.accountId())
                .getFirst();
        PlatformPrincipal addedPrincipal = identities.activePrincipals(addedAccount.accountId()).getFirst();
        rbac.initializeBuiltInRoles();
        Role root = rbac.findRoles()
                .stream()
                .map(PlatformRoleDetails::role)
                .filter(role -> PlatformRbacService.ROOT.equals(role.code()))
                .findFirst()
                .orElseThrow();
        rbac.bindBootstrapRoot(protectedPrincipal.principalId());

        assertThatThrownBy(
                () -> rbac.changeRoleAccounts(
                        root.roleId(),
                        List.of(addedAccount.accountId()),
                        List.of(protectedAccount.accountId())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(RbacErrorCode.BUILT_IN_ROLE_PROTECTED));

        assertThat(rbac.findPrincipalRoles(addedPrincipal.principalId()).roles()).isEmpty();
        assertThat(rbac.findPrincipalRoles(protectedPrincipal.principalId()).roles())
                .extracting(Role::roleId)
                .containsExactly(root.roleId());
    }

    private Account provisionAccount(String username) {
        PlatformPrincipal principal = identities.provision(username);
        return identities.findActiveAccounts(principal.principalId()).getFirst();
    }

    /**
     * 创建集成测试角色使用的默认语言名称。
     */
    private static LocalizedFields roleI18n(String name) {
        return LocalizedFields.of(
                Map.of(
                        Locale.forLanguageTag("zh-CN"),
                        Map.of("name", name)));
    }

}
