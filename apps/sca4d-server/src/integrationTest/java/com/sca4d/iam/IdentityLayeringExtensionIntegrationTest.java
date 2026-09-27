package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.SessionIdentityLoader;
import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.authentication.principalsession.PrincipalLoader;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.spring.identity.PrincipalSelection;
import com.sca4d.iam.acl.AclEntry;
import com.sca4d.iam.acl.AclPermissionPolicy;
import com.sca4d.iam.acl.AclRepository;
import com.sca4d.iam.acl.AclService;
import com.sca4d.iam.identity.AccountIdentifierNormalizer;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.rbac.PermissionSelections;
import com.sca4d.iam.rbac.PermissionSelector;
import com.sca4d.iam.rbac.PrincipalRoles;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacErrorCode;
import com.sca4d.iam.rbac.RbacRepository;
import com.sca4d.iam.rbac.RbacScope;
import com.sca4d.iam.rbac.RbacService;
import com.sca4d.iam.rbac.RbacSessionInvalidation;
import com.sca4d.iam.rbac.Role;
import com.sca4d.iam.rbac.RoleStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Testcontainers
@Sql(statements = "TRUNCATE TABLE auth_password_secret, auth_password_credential, platform_principal_role, iam_account_identifier, iam_account_principal, iam_principal, iam_account, iam_subject CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SpringBootTest(classes = IdentityLayeringExtensionIntegrationTest.TestApplication.class, properties = {
        "sca4dj.iam.bootstrap.enabled=false",
        "sca4dj.auth.password.bootstrap-enabled=false",
        "spring.autoconfigure.exclude=com.sca4d.messaging.config.MessagingAutoConfiguration,com.sca4d.web.realtime.config.WebRealtimeAutoConfiguration,com.sca4d.notification.config.NotificationAutoConfiguration,com.sca4d.notification.email.config.EmailNotificationAutoConfiguration,com.sca4d.auth.email.config.EmailCodeAutoConfiguration,com.sca4d.iot.config.IotAutoConfiguration,com.sca4d.iot.config.IotWorkerAutoConfiguration,com.sca4d.iot.emqx.config.EmqxAutoConfiguration,com.sca4d.iot.config.TimescaleIotAutoConfiguration"
})
@SuppressWarnings({
        "DuplicatedCode",
        "SqlResolve",
        "SqlNoDataSourceInspection",
        "SpringJavaInjectionPointsAutowiringInspection"
}) // Testcontainers、Flyway 和 SpringBootTest 在测试启动时提供表结构及测试 Bean；重复配置用于隔离测试上下文。
class IdentityLayeringExtensionIntegrationTest {

    private static final AccountIdentifierType PHONE = new AccountIdentifierType("PHONE");

    private static final RealmKey RIDER_REALM = new RealmKey("rider");

    private static final DomainScope DELIVERY = new DomainScope("DELIVERY");

    private static final RbacDomain DELIVERY_RBAC = new RbacDomain("DELIVERY");

    private static final RbacScope DELIVERY_ROLES = RbacScope.global(DELIVERY_RBAC);

    private static final TreeCode DELIVERY_ORDER = TreeCode.ofSlash("DELIVERY/ORDER");

    private static final TreeCode STORE_REPORT = TreeCode.ofSlash("DELIVERY/STORE_REPORT");

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
        /**
         * 为不执行认证步骤的 IAM 集成测试声明具体 App 必须提供的固定认证策略。
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
        AccountIdentifierNormalizer phoneNormalizer() {
            return new PhoneNormalizer();
        }

        @Bean
        PrincipalCodec<Rider> riderPrincipalCodec() {
            return new PrincipalIdCodec<>("delivery.rider", Rider.class, Rider::new);
        }

        @Bean
        PrincipalCodec<Customer> customerPrincipalCodec() {
            return new PrincipalIdCodec<>("delivery.customer", Customer.class, Customer::new);
        }

        @Bean
        SessionIdentityLoader deliverySessionIdentityLoader(IdentityService identities) {
            return new DeliverySessionIdentityLoader(identities);
        }

        @Bean
        PrincipalLoader deliveryPrincipalLoader(IdentityService identities) {
            return new DeliveryPrincipalLoader(identities);
        }

        @Bean
        RbacRepository deliveryRbacRepository() {
            return new InMemoryDeliveryRbacRepository();
        }

        @Bean
        PermissionSelections deliveryPermissionSelections(OperationCatalog operations) {
            return new PermissionSelections(DELIVERY_RBAC, operations);
        }

        @Bean
        RbacSessionInvalidation deliveryRbacSessionInvalidation() {
            return new NoOpDeliverySessionInvalidation();
        }

        @Bean
        AclRepository deliveryAclRepository() {
            return new InMemoryDeliveryAclRepository();
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
    private IdentityService identities;

    @Autowired
    private SessionIdentityLoader deliverySessionIdentityLoader;

    @Autowired
    private PrincipalLoader deliveryPrincipalLoader;

    @Autowired
    private RbacService rbac;

    @Autowired
    private AclService acl;

    @Autowired
    private AclPermissionPolicy aclPolicy;

    @Autowired
    private OperationCatalog operations;

    @Test
    @DisplayName("Extends Identity Login Principal Selection Rbac Acl And Abac Without Iam Changes")
    void extendsCompleteIdentityAndPermissionChain() {
        Subject subject = identities.createSubject(SubjectType.HUMAN);
        Account account = identities.createAccount(subject.subjectId(), RIDER_REALM);
        assertThat(identities.addIdentifier(account.accountId(), PHONE, "138 0013-8000").accountId())
                .isEqualTo(account.accountId());
        Principal rider = new Rider(identities.createPrincipal(Rider.class));
        Principal customer = new Customer(identities.createPrincipal(Customer.class));
        identities.link(account.accountId(), rider.principalId());
        identities.link(account.accountId(), customer.principalId());

        Long resolved = identities.resolveAccount(RIDER_REALM, PHONE, "13800138000").orElseThrow();
        assertThat(resolved).isEqualTo(account.accountId());
        Session session = login(resolved);
        assertThat(session.principals()).anyMatch(Rider.class::isInstance);
        assertThat(session.principals()).anyMatch(Customer.class::isInstance);

        Principal selected = new DeliveryPrincipalSelection().select(
                session,
                session.subject(),
                session.account(),
                Map.of("principalId", rider.principalId()));
        assertThat(selected).isInstanceOf(Rider.class);

        TreeCode operationCode = operations.actions().getFirst().operation().code();
        Role role = rbac.createRole(DELIVERY_ROLES, "DISPATCHER", List.of(operationCode)).role();
        rbac.bindRole(DELIVERY_ROLES, rider.principalId(), role.code(), false);
        assertThat(rbac.findGrantedOperations(DELIVERY_ROLES, List.of(rider.principalId())).get(rider.principalId()))
                .extracting(Operation::code)
                .containsExactly(operationCode);

        Call call = new Call("extension-call", NOW);
        call.authenticate(session);
        call.selectPrincipal(selected);

        acl.grant(DELIVERY, rider.principalId(), DELIVERY_ORDER, "order-42", Action.READ);
        Permission orderRead = new Permission(new SimpleResource(DELIVERY_ORDER, "order-42"), Action.READ);
        Permission ownStoreRead = new Permission(new SimpleResource(STORE_REPORT, "store-7"), Action.READ);
        Permission otherStoreRead = new Permission(new SimpleResource(STORE_REPORT, "store-8"), Action.READ);
        PermissionAssessment permissionAssessment = new PermissionAssessment(
                List.of(
                        aclPolicy,
                        new StoreAttributePolicy(Map.of(rider.principalId(), "store-7"))));

        assertThat(permissionAssessment.assess(call, orderRead).isAllowed()).isTrue();
        assertThat(permissionAssessment.assess(call, ownStoreRead).isAllowed()).isTrue();
        assertThat(permissionAssessment.assess(call, otherStoreRead).isAllowed()).isFalse();
    }

    private Session login(Long accountId) {
        Account account = identities.activeAccountById(accountId);
        SessionIdentity identity = deliverySessionIdentityLoader.load(account);
        List<? extends Principal> principals = deliveryPrincipalLoader.load(identity);
        LinkedHashMap<Long, List<Resource>> resources = new LinkedHashMap<>();
        principals.forEach(principal -> resources.put(principal.principalId(), List.of()));
        return new Session(
                new SessionId("extension-session"),
                identity.subject(),
                identity.account(),
                principals,
                List.of(),
                List.of(
                        new Authentication(
                                accountId,
                                TreeCode.ofSlash("AUTHENTICATION/PHONE_TEST"),
                                Set.of(AuthenticationFactorKind.POSSESSION),
                                NOW)),
                resources,
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class Rider implements Principal {
        private final long id;

        private Rider(long id) {
            this.id = id;
        }

        @Override
        public long principalId() {
            return id;
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class Customer implements Principal {
        private final long id;

        private Customer(long id) {
            this.id = id;
        }

        @Override
        public long principalId() {
            return id;
        }
    }

    private static final class PhoneNormalizer implements AccountIdentifierNormalizer {
        @Override
        public @NonNull AccountIdentifierType type() {
            return PHONE;
        }

        @Override
        public @NonNull String normalize(@NonNull String rawValue) {
            String normalized = Objects.requireNonNull(rawValue, "手机号不能为空").replaceAll("[^0-9]", "");
            if (normalized.length() != 11) {
                throw new IllegalArgumentException("手机号必须包含 11 位数字");
            }
            return normalized;
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class PrincipalIdCodec<P extends Principal> implements PrincipalCodec<P> {
        private final String type;

        private final Class<P> valueType;

        private final Function<Long, P> factory;

        private PrincipalIdCodec(String type, Class<P> valueType, Function<Long, P> factory) {
            this.type = type;
            this.valueType = valueType;
            this.factory = factory;
        }

        @Override
        public @NotNull TreeCode type() {
            return TreeCode.of(type);
        }

        @Override
        public Class<P> valueType() {
            return valueType;
        }

        @Override
        public byte[] encode(P value) {
            return ByteBuffer.allocate(Long.BYTES).putLong(value.principalId()).array();
        }

        @Override
        public P decode(byte[] payload) {
            if (payload.length != Long.BYTES) {
                throw new IllegalArgumentException("Principal payload 必须为 8 字节");
            }
            return factory.apply(ByteBuffer.wrap(payload).getLong());
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class DeliverySessionIdentityLoader implements SessionIdentityLoader {
        private final IdentityService identities;

        private DeliverySessionIdentityLoader(IdentityService identities) {
            this.identities = identities;
        }

        @Override
        public boolean supports(@NotNull Account account) {
            return RIDER_REALM.equals(account.realmKey());
        }

        @Override
        public @NotNull SessionIdentity load(@NotNull Account account) {
            Subject subject = identities.activeSubjectById(account.subjectId());
            Account current = identities.activeAccountById(account.accountId());
            return new SessionIdentity(subject, current);
        }
    }

    @SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通测试类型。
    private static final class DeliveryPrincipalLoader implements PrincipalLoader {
        private final IdentityService identities;

        private DeliveryPrincipalLoader(IdentityService identities) {
            this.identities = identities;
        }

        @Override
        public boolean supports(@NotNull SessionIdentity identity) {
            return RIDER_REALM.equals(identity.account().realmKey());
        }

        @Override
        public @NotNull List<? extends Principal> load(@NotNull SessionIdentity identity) {
            List<Principal> principals = new ArrayList<>();
            identities.findActiveLinkedPrincipalIds(identity.account().accountId(), Rider.class)
                    .stream()
                    .map(Rider::new)
                    .forEach(principals::add);
            identities.findActiveLinkedPrincipalIds(identity.account().accountId(), Customer.class)
                    .stream()
                    .map(Customer::new)
                    .forEach(principals::add);
            return List.copyOf(principals);
        }
    }

    private static final class DeliveryPrincipalSelection
            implements
                PrincipalSelection<Map<String, @Nullable Object>> {
        @Override
        public @NotNull Class<Map<String, @Nullable Object>> parameterType() {
            return PrincipalSelection.mapParameterType();
        }

        @Override
        public @Nullable Principal select(
                @Nullable Session session,
                @Nullable Subject subject,
                @Nullable Account account,
                @NotNull Map<String, @Nullable Object> parameters) {
            if (session == null || account == null || !RIDER_REALM.equals(account.realmKey())) {
                return null;
            }
            long selectedId = ((Number) Objects.requireNonNull(parameters.get("principalId"))).longValue();
            return session.principals()
                    .stream()
                    .filter(principal -> principal.principalId() == selectedId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("选择的 Principal 不在当前 Session 中"));
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class SimpleResource implements Resource {
        private final TreeCode type;

        private final String id;

        private SimpleResource(TreeCode type, String id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public @NotNull TreeCode type() {
            return type;
        }

        @Override
        public @NotNull String id() {
            return id;
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class StoreAttributePolicy implements PermissionPolicy {
        private final Map<Long, String> stores;

        private StoreAttributePolicy(Map<Long, String> stores) {
            this.stores = Map.copyOf(stores);
        }

        @Override
        public boolean supports(Resource resource) {
            return STORE_REPORT.equals(resource.type());
        }

        @Override
        public PermissionDecision decide(Call call, Permission permission) {
            Long principalId = call.principal().orElseThrow().principalId();
            return permission.resource().id().equals(stores.get(principalId))
                    ? PermissionDecision.allow()
                    : PermissionDecision.deny("只能读取所属门店报表");
        }
    }

    private static void assertDeliveryScope(RbacScope scope) {
        if (!DELIVERY_ROLES.equals(Objects.requireNonNull(scope))) {
            throw new IllegalArgumentException("测试 Repository 只接受 Delivery 全局 RBAC 范围");
        }
    }

    private static final class NoOpDeliverySessionInvalidation implements RbacSessionInvalidation {
        @Override
        public @NotNull RbacDomain domain() {
            return DELIVERY_RBAC;
        }

        @Override
        public void invalidate(
                @NotNull RbacScope scope,
                @NotNull Collection<@NotNull Long> principalIds) {
            assertDeliveryScope(scope);
            for (Long principalId : Objects.requireNonNull(principalIds)) {
                Objects.requireNonNull(principalId);
            }
        }
    }

    private static final class InMemoryDeliveryAclRepository implements AclRepository {
        private final Set<AclEntry> entries = new LinkedHashSet<>();

        @Override
        @CheckReturnValue
        public @NotNull DomainScope scope() {
            return DELIVERY;
        }

        @Override
        @CheckReturnValue
        public @NotNull @Unmodifiable Set<@NotNull TreeCode> resourceTypes() {
            return Set.of(DELIVERY_ORDER);
        }

        @Override
        public void grant(
                @NotNull Long principalId,
                @NotNull TreeCode resourceType,
                @NotNull String resourceId,
                @NotNull Action action) {
            if (!allows(principalId, resourceType, resourceId, action)) {
                entries.add(new AclEntry(principalId, resourceType, resourceId, action));
            }
        }

        @Override
        public void revoke(
                @NotNull Long principalId,
                @NotNull TreeCode resourceType,
                @NotNull String resourceId,
                @NotNull Action action) {
            entries.removeIf(entry -> matches(entry, principalId, resourceType, resourceId, action));
        }

        @Override
        @CheckReturnValue
        public boolean allows(
                @NotNull Long principalId,
                @NotNull TreeCode resourceType,
                @NotNull String resourceId,
                @NotNull Action action) {
            return entries.stream().anyMatch(entry -> matches(entry, principalId, resourceType, resourceId, action));
        }

        @Override
        @CheckReturnValue
        public @NotNull @Unmodifiable List<@NotNull String> findAllowedResourceIds(
                @NotNull Long principalId,
                @NotNull TreeCode resourceType,
                @NotNull Action action) {
            return entries.stream()
                    .filter(
                            entry -> entry.principalId().equals(principalId)
                                    && entry.resourceType().equals(resourceType)
                                    && entry.action().equals(action))
                    .map(AclEntry::resourceId)
                    .sorted()
                    .toList();
        }

        @Override
        @CheckReturnValue
        public @NotNull @Unmodifiable List<@NotNull AclEntry> findEntries(
                @NotNull Long principalId,
                @NotNull TreeCode resourceType) {
            return entries.stream()
                    .filter(
                            entry -> entry.principalId().equals(principalId)
                                    && entry.resourceType().equals(resourceType))
                    .toList();
        }

        @Override
        public void revokeByPrincipal(@NotNull Long principalId) {
            entries.removeIf(entry -> entry.principalId().equals(principalId));
        }

        @Override
        public void revokeByResource(@NotNull TreeCode resourceType, @NotNull String resourceId) {
            entries.removeIf(
                    entry -> entry.resourceType().equals(resourceType)
                            && entry.resourceId().equals(resourceId));
        }

        private static boolean matches(
                AclEntry entry,
                Long principalId,
                TreeCode resourceType,
                String resourceId,
                Action action) {
            return entry.principalId().equals(principalId)
                    && entry.resourceType().equals(resourceType)
                    && entry.resourceId().equals(resourceId)
                    && entry.action().equals(action);
        }
    }

    private static final class InMemoryDeliveryRbacRepository implements RbacRepository {
        private final Map<Long, Role> roles = new LinkedHashMap<>();

        private final Map<Long, List<PermissionSelector>> permissions = new LinkedHashMap<>();

        private final Map<Long, LinkedHashMap<Long, Boolean>> assignments = new LinkedHashMap<>();

        private final Map<Long, Version> assignmentVersions = new LinkedHashMap<>();

        @Override
        public @NotNull RbacDomain domain() {
            return DELIVERY_RBAC;
        }

        @Override
        public @NotNull List<Role> findRoles(@NotNull RbacScope scope) {
            assertDeliveryScope(scope);
            return roles.values().stream().sorted(Comparator.comparing(Role::code)).toList();
        }

        @Override
        public @NotNull Optional<Role> findRole(@NotNull RbacScope scope, @NotNull Long roleId) {
            assertDeliveryScope(scope);
            return Optional.ofNullable(roles.get(roleId));
        }

        @Override
        public @NotNull Optional<Role> findRole(@NotNull RbacScope scope, @NotNull String code) {
            assertDeliveryScope(scope);
            return roles.values().stream().filter(role -> role.code().equals(code)).findFirst();
        }

        @Override
        public @NotNull List<Role> findRoles(
                @NotNull RbacScope scope,
                @NotNull Collection<@NotNull Long> roleIds) {
            assertDeliveryScope(scope);
            return roleIds.stream()
                    .map(roles::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Role::code))
                    .toList();
        }

        @Override
        public @NotNull Role createRole(
                @NotNull RbacScope scope,
                @NotNull Long roleId,
                @NotNull String code,
                @NotNull RoleStatus status,
                boolean builtIn,
                @NotNull Collection<@NotNull PermissionSelector> permissionSelectors) {
            assertDeliveryScope(scope);
            Instant now = NOW;
            Role role = new Role(roleId, scope, code, status, builtIn, now, now, new Version(0));
            roles.put(roleId, role);
            permissions.put(roleId, List.copyOf(permissionSelectors));
            return role;
        }

        @Override
        public void bindRole(
                @NotNull RbacScope scope,
                @NotNull Long principalId,
                @NotNull Long roleId,
                boolean protectedRelation) {
            assertDeliveryScope(scope);
            assignments.computeIfAbsent(principalId, ignored -> new LinkedHashMap<>())
                    .merge(roleId, protectedRelation, Boolean::logicalOr);
            assignmentVersions.compute(
                    principalId,
                    (ignored, current) -> current == null ? new Version(1) : current.next());
        }

        @Override
        public @NotNull Role updateRoleStatus(
                @NotNull RbacScope scope,
                @NotNull Long roleId,
                @NotNull RoleStatus status) {
            assertDeliveryScope(scope);
            Role current = loadRole(roleId);
            Role updated = new Role(
                    current.roleId(),
                    current.scope(),
                    current.code(),
                    status,
                    current.builtIn(),
                    current.createdAt(),
                    NOW,
                    current.version().next());
            roles.put(roleId, updated);
            return updated;
        }

        @Override
        public void deleteRole(@NotNull RbacScope scope, @NotNull Long roleId) {
            assertDeliveryScope(scope);
            loadRole(roleId);
            if (assignments.values().stream().anyMatch(values -> values.containsKey(roleId))) {
                throw new IllegalStateException("仍分配给 Principal 的测试角色不能删除");
            }
            roles.remove(roleId);
            permissions.remove(roleId);
        }

        @Override
        public @NotNull List<PermissionSelector> findPermissionSelectors(
                @NotNull RbacScope scope,
                @NotNull Long roleId) {
            assertDeliveryScope(scope);
            return List.copyOf(permissions.getOrDefault(roleId, List.of()));
        }

        @Override
        public @NotNull Role replacePermissionSelectors(
                @NotNull RbacScope scope,
                @NotNull Long roleId,
                @NotNull Collection<@NotNull PermissionSelector> permissionSelectors) {
            assertDeliveryScope(scope);
            Role current = loadRole(roleId);
            Role updated = updateRoleStatus(scope, roleId, current.status());
            permissions.put(roleId, List.copyOf(permissionSelectors));
            return updated;
        }

        @Override
        public @NotNull Map<Long, PrincipalRoles> findPrincipalRoles(
                @NotNull RbacScope scope,
                @NotNull Collection<@NotNull Long> principalIds) {
            assertDeliveryScope(scope);
            LinkedHashMap<Long, PrincipalRoles> result = new LinkedHashMap<>();
            principalIds.forEach(principalId -> result.put(principalId, principalRoles(scope, principalId)));
            return Map.copyOf(result);
        }

        private @NotNull PrincipalRoles principalRoles(
                @NotNull RbacScope scope,
                @NotNull Long principalId) {
            LinkedHashMap<Long, Boolean> links = assignments.getOrDefault(principalId, new LinkedHashMap<>());
            List<Role> assignedRoles = findRoles(scope, links.keySet());
            Set<Long> protectedIds = links.entrySet()
                    .stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableSet());
            return new PrincipalRoles(
                    scope,
                    principalId,
                    assignedRoles,
                    protectedIds,
                    assignmentVersions.getOrDefault(principalId, new Version(0)));
        }

        @Override
        public @NotNull PrincipalRoles replacePrincipalRoles(
                @NotNull RbacScope scope,
                @NotNull Long principalId,
                @NotNull Collection<@NotNull Long> roleIds) {
            assertDeliveryScope(scope);
            LinkedHashMap<Long, Boolean> links = assignments.computeIfAbsent(
                    principalId,
                    ignored -> new LinkedHashMap<>());
            links.entrySet().removeIf(entry -> !entry.getValue());
            roleIds.forEach(roleId -> links.putIfAbsent(roleId, false));
            assignmentVersions.compute(
                    principalId,
                    (ignored, current) -> current == null ? new Version(1) : current.next());
            return principalRoles(scope, principalId);
        }

        @Override
        public @NotNull List<Long> changeRoleAssignments(
                @NotNull RbacScope scope,
                @NotNull Long roleId,
                @NotNull Collection<@NotNull Long> addPrincipalIds,
                @NotNull Collection<@NotNull Long> removePrincipalIds) {
            assertDeliveryScope(scope);
            loadRole(roleId);
            LinkedHashSet<Long> changed = new LinkedHashSet<>();
            for (Long principalId : addPrincipalIds) {
                LinkedHashMap<Long, Boolean> links = assignments.computeIfAbsent(
                        principalId,
                        ignored -> new LinkedHashMap<>());
                if (links.putIfAbsent(roleId, false) == null) {
                    changed.add(principalId);
                }
            }
            for (Long principalId : removePrincipalIds) {
                LinkedHashMap<Long, Boolean> links = assignments.get(principalId);
                if (links == null || !links.containsKey(roleId)) {
                    continue;
                }
                if (Boolean.TRUE.equals(links.get(roleId))) {
                    throw new BusinessException(RbacErrorCode.BUILT_IN_ROLE_PROTECTED);
                }
                links.remove(roleId);
                changed.add(principalId);
            }
            changed.forEach(
                    principalId -> assignmentVersions.compute(
                            principalId,
                            (ignored, current) -> current == null ? new Version(1) : current.next()));
            return changed.stream().sorted().toList();
        }

        @Override
        public @NotNull List<Long> findRolePrincipalIds(
                @NotNull RbacScope scope,
                @NotNull Long roleId) {
            assertDeliveryScope(scope);
            return assignments.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().containsKey(roleId))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }

        @Override
        public @NotNull Map<RbacScope, Map<Long, List<PermissionSelector>>> findActivePermissionSelectors(
                @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
            LinkedHashMap<RbacScope, Map<Long, List<PermissionSelector>>> result = new LinkedHashMap<>();
            for (Map.Entry<RbacScope, ? extends Collection<Long>> scopeEntry : principalIdsByScope.entrySet()) {
                RbacScope scope = scopeEntry.getKey();
                assertDeliveryScope(scope);
                LinkedHashMap<Long, List<PermissionSelector>> selectorsByPrincipal = new LinkedHashMap<>();
                for (Long principalId : scopeEntry.getValue()) {
                    LinkedHashSet<PermissionSelector> selectors = new LinkedHashSet<>();
                    for (Role role : principalRoles(scope, principalId).roles()) {
                        if (role.status() == RoleStatus.ACTIVE) {
                            selectors.addAll(permissions.getOrDefault(role.roleId(), List.of()));
                        }
                    }
                    selectorsByPrincipal.put(principalId, List.copyOf(selectors));
                }
                result.put(scope, Map.copyOf(selectorsByPrincipal));
            }
            return Map.copyOf(result);
        }

        private Role loadRole(Long roleId) {
            return Optional.ofNullable(roles.get(roleId)).orElseThrow();
        }
    }
}
