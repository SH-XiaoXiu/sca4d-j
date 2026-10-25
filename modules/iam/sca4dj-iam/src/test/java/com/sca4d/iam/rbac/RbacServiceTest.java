package com.sca4d.iam.rbac;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.base.rbac.PlatformRbacRepository;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

class RbacServiceTest {

    private static final long DEFAULT_ID = 11L;

    private static final long PRINCIPAL_ID = 20L;

    private static final long ROOT_ID = 10L;

    private static final long SECOND_PRINCIPAL_ID = 21L;

    private static final RbacDomain PLATFORM_DOMAIN = PlatformRbacRepository.PLATFORM_DOMAIN;

    private static final RbacScope PLATFORM = PlatformRbacRepository.PLATFORM;

    private static final RbacScope PROJECT_A = RbacScope.of(PLATFORM_DOMAIN, 101L);

    private static final RbacScope PROJECT_B = RbacScope.of(PLATFORM_DOMAIN, 102L);

    private RbacRepository repository;

    private RbacSessionInvalidation invalidation;

    private RbacPermissionCache permissionCache;

    private RbacService rbac;

    @BeforeEach
    void prepareDomain() {
        repository = mock(RbacRepository.class);
        invalidation = mock(RbacSessionInvalidation.class);
        permissionCache = mock(RbacPermissionCache.class);
        when(repository.domain()).thenReturn(PLATFORM_DOMAIN);
        when(invalidation.domain()).thenReturn(PLATFORM_DOMAIN);
        when(permissionCache.find(any())).thenAnswer(invocation -> {
            Map<RbacScope, ? extends Collection<Long>> requested = invocation.getArgument(0);
            LinkedHashMap<RbacScope, Map<Long, Long>> revisions = new LinkedHashMap<>();
            requested.forEach((scope, principalIds) -> {
                LinkedHashMap<Long, Long> scopeRevisions = new LinkedHashMap<>();
                principalIds.forEach(principalId -> scopeRevisions.put(principalId, 0L));
                revisions.put(scope, scopeRevisions);
            });
            return new RbacPermissionCacheLookup(Map.of(), revisions);
        });
        RbacOperationModule operationModule = new RbacOperationModule() {
            @Override
            public OperationNode.@NotNull Module operationTree() {
                return OperationNode.module(
                        TreeCode.ofSegment("TEST"),
                        "operation.test",
                        100,
                        OperationNode.resource(
                                TreeCode.ofSegment("ITEMS"),
                                "operation.test.items",
                                100,
                                OperationNode.action(
                                        Action.READ.code(),
                                        "operation.test.items.read",
                                        100),
                                OperationNode.action(
                                        Action.MANAGE.code(),
                                        "operation.test.items.manage",
                                        200)));
            }

            @Override
            public @NotNull Set<@NotNull RbacDomain> domains() {
                return Set.of(PLATFORM_DOMAIN);
            }
        };
        OperationCatalog operationCatalog = new OperationCatalog(List.of(operationModule));
        rbac = new RbacService(
                new RbacRepositoryRegistry(List.of(repository)),
                new RbacSessionInvalidationRegistry(List.of(invalidation)),
                new PermissionSelectionRegistry(operationCatalog, List.of(operationModule)),
                permissionCache,
                () -> Id.of(30L));
    }

    @Test
    @DisplayName("Routes Scope To Its Domain Repository")
    void routesScopeToItsDomainRepository() {
        Role role = role(31L, "AUDITOR", false);
        when(repository.findRoles(PLATFORM)).thenReturn(List.of(role));

        assertThat(rbac.findRoles(RbacScope.global(new RbacDomain("platform"))))
                .containsExactly(role);
    }

    @Test
    @DisplayName("Rejects Internal Selector From Role Management API")
    void rejectsInternalSelectorFromRoleManagementApi() {
        assertBusinessFailure(
                () -> rbac.createRole(PLATFORM, "ADMIN", List.of(TreeCode.ofSegment("*"))),
                RbacErrorCode.OPERATION_INVALID);

        verify(repository, never())
                .createRole(any(), any(), anyString(), any(), anyBoolean(), anyCollection());
    }

    @Test
    @DisplayName("Expands And Caches Stored All Selector Without Checking Role Code")
    void expandsAndCachesStoredAllSelectorWithoutCheckingRoleCode() {
        Role customCode = role(ROOT_ID, "FULL_ACCESS", true);
        Map<RbacScope, Collection<Long>> request = Map.of(PLATFORM, List.of(PRINCIPAL_ID));
        Map<RbacScope, Map<Long, List<PermissionSelector>>> loaded = Map.of(
                PLATFORM,
                Map.of(PRINCIPAL_ID, List.of(PermissionSelections.ALL)));
        when(repository.findActivePermissionSelectors(request)).thenReturn(loaded);

        assertThat(rbac.findGrantedOperations(PLATFORM, List.of(PRINCIPAL_ID)).get(PRINCIPAL_ID))
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("TEST:ITEMS:MANAGE", "TEST:ITEMS:READ");
        verify(permissionCache).put(any(RbacPermissionCacheLookup.class), ArgumentMatchers.eq(loaded));
        assertThat(customCode.code()).isNotEqualTo(PlatformRbacService.ROOT);
    }

    @Test
    @DisplayName("Loads All Missing Scopes In One Domain Batch And Caches Empty Grants")
    void loadsAllMissingScopesInOneDomainBatchAndCachesEmptyGrants() {
        Map<RbacScope, Collection<Long>> request = Map.of(
                PROJECT_A,
                List.of(PRINCIPAL_ID),
                PROJECT_B,
                List.of(SECOND_PRINCIPAL_ID));
        Map<RbacScope, Map<Long, List<PermissionSelector>>> loaded = Map.of(
                PROJECT_A,
                Map.of(PRINCIPAL_ID, List.of(new PermissionSelector("TEST:ITEMS:READ"))),
                PROJECT_B,
                Map.of(SECOND_PRINCIPAL_ID, List.of()));
        when(repository.findActivePermissionSelectors(request)).thenReturn(loaded);

        Map<RbacScope, Map<Long, List<Operation>>> result = rbac.findGrantedOperations(request);

        assertThat(result.get(PROJECT_A).get(PRINCIPAL_ID))
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("TEST:ITEMS:READ");
        assertThat(result.get(PROJECT_B).get(SECOND_PRINCIPAL_ID)).isEmpty();
        verify(repository).findActivePermissionSelectors(request);
        verify(permissionCache).put(any(RbacPermissionCacheLookup.class), ArgumentMatchers.eq(loaded));
    }

    @Test
    @DisplayName("Reads Only Cache Misses From Repository")
    void readsOnlyCacheMissesFromRepository() {
        doReturn(
                new RbacPermissionCacheLookup(
                        Map.of(
                                PROJECT_A,
                                Map.of(
                                        PRINCIPAL_ID,
                                        List.of(new PermissionSelector("TEST:ITEMS:READ")))),
                        Map.of(
                                PROJECT_A,
                                Map.of(PRINCIPAL_ID, 4L, SECOND_PRINCIPAL_ID, 7L))))
                .when(permissionCache)
                .find(any());
        Map<RbacScope, Collection<Long>> missing = Map.of(PROJECT_A, List.of(SECOND_PRINCIPAL_ID));
        Map<RbacScope, Map<Long, List<PermissionSelector>>> loaded = Map.of(
                PROJECT_A,
                Map.of(SECOND_PRINCIPAL_ID, List.of()));
        when(repository.findActivePermissionSelectors(missing)).thenReturn(loaded);

        Map<Long, List<Operation>> result = rbac.findGrantedOperations(
                PROJECT_A,
                List.of(PRINCIPAL_ID, SECOND_PRINCIPAL_ID));

        assertThat(result.get(PRINCIPAL_ID))
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("TEST:ITEMS:READ");
        assertThat(result.get(SECOND_PRINCIPAL_ID)).isEmpty();
        verify(repository).findActivePermissionSelectors(missing);
        verify(permissionCache).put(any(RbacPermissionCacheLookup.class), ArgumentMatchers.eq(loaded));
    }

    @Test
    @DisplayName("Filters Principals By All Or Any Required Operations")
    void filtersPrincipalsByRequiredOperations() {
        Map<RbacScope, Collection<Long>> request = Map.of(
                PLATFORM,
                List.of(PRINCIPAL_ID, SECOND_PRINCIPAL_ID));
        Map<RbacScope, Map<Long, List<PermissionSelector>>> loaded = Map.of(
                PLATFORM,
                Map.of(
                        PRINCIPAL_ID,
                        List.of(
                                new PermissionSelector("TEST:ITEMS:READ"),
                                new PermissionSelector("TEST:ITEMS:MANAGE")),
                        SECOND_PRINCIPAL_ID,
                        List.of(new PermissionSelector("TEST:ITEMS:READ"))));
        when(repository.findActivePermissionSelectors(request)).thenReturn(loaded);

        assertThat(
                rbac.findPrincipalIds(
                        PLATFORM,
                        PrincipalGrantQuery.forIds(List.of(PRINCIPAL_ID, SECOND_PRINCIPAL_ID))
                                .allOf(TreeCode.ofColon("TEST:ITEMS:READ"), TreeCode.ofColon("TEST:ITEMS:MANAGE"))
                                .build()))
                .containsExactly(PRINCIPAL_ID);
        assertThat(
                rbac.findPrincipalIds(
                        PLATFORM,
                        PrincipalGrantQuery.forIds(List.of(PRINCIPAL_ID, SECOND_PRINCIPAL_ID))
                                .anyOf(TreeCode.ofColon("TEST:ITEMS:MANAGE"))
                                .build()))
                .containsExactly(PRINCIPAL_ID);
    }

    @Test
    @DisplayName("Rejects Operations Outside The Scope Domain")
    void rejectsGrantQueryOperationsOutsideTheScopeDomain() {
        assertBusinessFailure(
                () -> rbac.findPrincipalIds(
                        PLATFORM,
                        PrincipalGrantQuery.forIds(List.of(PRINCIPAL_ID))
                                .allOf(TreeCode.ofColon("OTHER:ITEMS:READ"))
                                .build()),
                RbacErrorCode.OPERATION_INVALID);

        verify(repository, never()).findActivePermissionSelectors(any());
    }

    @Test
    @DisplayName("Initializes Root With All Selector And Default Without Operations")
    void initializesRootWithAllSelectorAndDefaultWithoutOperations() {
        Role root = role(ROOT_ID, PlatformRbacService.ROOT, true);
        Role defaultRole = role(DEFAULT_ID, PlatformRbacService.DEFAULT, true);
        when(repository.findRole(PLATFORM, PlatformRbacService.ROOT)).thenReturn(Optional.empty());
        when(repository.findRole(PLATFORM, PlatformRbacService.DEFAULT)).thenReturn(Optional.empty());
        when(repository.createRole(any(), any(), anyString(), any(), anyBoolean(), anyCollection()))
                .thenAnswer(
                        invocation -> invocation.getArgument(2, String.class).equals(PlatformRbacService.ROOT)
                                ? root
                                : defaultRole);
        when(repository.findPermissionSelectors(PLATFORM, ROOT_ID))
                .thenReturn(List.of(PermissionSelections.ALL));

        rbac.initializeBuiltInRole(
                PLATFORM,
                PlatformRbacService.ROOT,
                List.of(PermissionSelections.ALL),
                true);
        rbac.initializeBuiltInRole(
                PLATFORM,
                PlatformRbacService.DEFAULT,
                List.of(),
                false);

        verify(repository).createRole(
                ArgumentMatchers.eq(PLATFORM),
                any(),
                ArgumentMatchers.eq(PlatformRbacService.ROOT),
                ArgumentMatchers.eq(RoleStatus.ACTIVE),
                ArgumentMatchers.eq(true),
                ArgumentMatchers.eq(List.of(PermissionSelections.ALL)));
        verify(repository).createRole(
                ArgumentMatchers.eq(PLATFORM),
                any(),
                ArgumentMatchers.eq(PlatformRbacService.DEFAULT),
                ArgumentMatchers.eq(RoleStatus.ACTIVE),
                ArgumentMatchers.eq(true),
                ArgumentMatchers.eq(List.of()));
    }

    @Test
    @DisplayName("Protects Built In Status And Stored All Selector")
    void protectsBuiltInStatusAndStoredAllSelector() {
        Role root = role(ROOT_ID, PlatformRbacService.ROOT, true);
        when(repository.findRole(PLATFORM, ROOT_ID)).thenReturn(Optional.of(root));
        when(repository.findPermissionSelectors(PLATFORM, ROOT_ID))
                .thenReturn(List.of(PermissionSelections.ALL));

        assertBusinessFailure(
                () -> rbac.updateRoleStatus(PLATFORM, ROOT_ID, RoleStatus.DISABLED),
                RbacErrorCode.BUILT_IN_ROLE_PROTECTED);
        assertThatThrownBy(() -> rbac.deleteRole(PLATFORM, ROOT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(RbacErrorCode.BUILT_IN_ROLE_PROTECTED));
        assertBusinessFailure(
                () -> rbac.replaceRolePermissions(
                        PLATFORM,
                        ROOT_ID,
                        List.of(TreeCode.ofColon("TEST:ITEMS:READ"))),
                RbacErrorCode.BUILT_IN_ROLE_PROTECTED);
    }

    @Test
    @DisplayName("Invalidates Assigned Principals After Replacing Role Operations")
    void invalidatesAssignedPrincipalsAfterReplacingRoleOperations() {
        Role defaultRole = role(DEFAULT_ID, PlatformRbacService.DEFAULT, true);
        Role updated = role(DEFAULT_ID, PlatformRbacService.DEFAULT, true);
        when(repository.findRole(PLATFORM, DEFAULT_ID)).thenReturn(Optional.of(defaultRole));
        when(repository.findPermissionSelectors(PLATFORM, DEFAULT_ID)).thenReturn(List.of());
        when(
                repository.replacePermissionSelectors(
                        PLATFORM,
                        DEFAULT_ID,
                        List.of(new PermissionSelector("TEST:ITEMS:READ"))))
                .thenReturn(updated);
        when(repository.findRolePrincipalIds(PLATFORM, DEFAULT_ID)).thenReturn(List.of(PRINCIPAL_ID));

        assertThat(
                rbac.replaceRolePermissions(
                        PLATFORM,
                        DEFAULT_ID,
                        List.of(TreeCode.ofColon("TEST:ITEMS:READ")))
                        .operations())
                .extracting(operation -> operation.code().format(COLON))
                .containsExactly("TEST:ITEMS:READ");
        verify(permissionCache).evict(Map.of(PLATFORM, List.of(PRINCIPAL_ID)));
        verify(invalidation).invalidate(PLATFORM, List.of(PRINCIPAL_ID));
    }

    @Test
    @DisplayName("Changes One Role For Multiple Principals In One Repository Batch")
    void changesRoleAssignmentsInOneBatch() {
        Role role = role(DEFAULT_ID, PlatformRbacService.DEFAULT, true);
        PrincipalRoles first = new PrincipalRoles(
                PLATFORM,
                PRINCIPAL_ID,
                List.of(role),
                Set.of(),
                new Version(1));
        PrincipalRoles second = new PrincipalRoles(
                PLATFORM,
                SECOND_PRINCIPAL_ID,
                List.of(),
                Set.of(),
                new Version(2));
        when(repository.findRole(PLATFORM, DEFAULT_ID)).thenReturn(Optional.of(role));
        when(
                repository.changeRoleAssignments(
                        PLATFORM,
                        DEFAULT_ID,
                        Set.of(PRINCIPAL_ID),
                        Set.of(SECOND_PRINCIPAL_ID)))
                .thenReturn(List.of(PRINCIPAL_ID));
        when(
                repository.findPrincipalRoles(
                        PLATFORM,
                        Set.of(PRINCIPAL_ID, SECOND_PRINCIPAL_ID)))
                .thenReturn(Map.of(PRINCIPAL_ID, first, SECOND_PRINCIPAL_ID, second));

        Map<Long, PrincipalRoles> result = rbac.changeRoleAssignments(
                PLATFORM,
                DEFAULT_ID,
                Set.of(PRINCIPAL_ID),
                Set.of(SECOND_PRINCIPAL_ID));

        assertThat(result).containsEntry(PRINCIPAL_ID, first).containsEntry(SECOND_PRINCIPAL_ID, second);
        verify(repository).changeRoleAssignments(
                PLATFORM,
                DEFAULT_ID,
                Set.of(PRINCIPAL_ID),
                Set.of(SECOND_PRINCIPAL_ID));
        verify(permissionCache).evict(Map.of(PLATFORM, List.of(PRINCIPAL_ID)));
        verify(invalidation).invalidate(PLATFORM, List.of(PRINCIPAL_ID));
    }

    @Test
    @DisplayName("Rejects A Principal Present In Both Role Assignment Collections")
    void rejectsOverlappingRoleAssignments() {
        assertBusinessFailure(
                () -> rbac.changeRoleAssignments(
                        PLATFORM,
                        DEFAULT_ID,
                        List.of(PRINCIPAL_ID),
                        List.of(PRINCIPAL_ID)),
                RbacErrorCode.PRINCIPAL_ROLE_INVALID);

        verify(repository, never()).changeRoleAssignments(any(), any(), anyCollection(), anyCollection());
    }

    private Role role(Long id, String code, boolean builtIn) {
        return new Role(
                id,
                PLATFORM,
                code,
                RoleStatus.ACTIVE,
                builtIn,
                Instant.parse("2026-07-23T00:00:00Z"),
                Instant.parse("2026-07-23T00:00:00Z"),
                new Version(0));
    }

    /**
     * 验证有返回值的 RBAC 调用在产生结果前按指定业务错误失败。
     */
    private void assertBusinessFailure(Supplier<?> invocation, RbacErrorCode errorCode) {
        assertThatThrownBy(() -> {
            Object unexpectedResult = invocation.get();
            throw new AssertionError("调用本应失败，实际返回: " + unexpectedResult);
        })
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(errorCode));
    }
}
