package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.IamRoleEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.entity.IamTenantEntity;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.ManagedAccount;
import com.sca4d.iam.client.IamClientStateTargets;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.principal.PlatformPrincipal;
import com.sca4d.iam.principal.PlatformRole;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.principal.RoleStatus;
import com.sca4d.iam.repository.CredentialRow;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.resource.AccountReference;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import com.sca4d.mapper.IamAccountMapper;
import com.sca4d.mapper.IamMapper;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.Tenant;
import com.sca4d.tenant.TenantErrorCode;
import com.sca4d.tenant.TenantRole;
import com.sca4d.tenant.policy.TenantAccessCriteria;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证 Subject、Credential、Principal、角色、权限和租户关系的数据库读写。 */
class IamRepositoryTest {

    @Test
    void shouldBuildAccountPaginationWithProfileUsernameAndSearchProjection() {
        IamAccountMapper accountMapper = mock(IamAccountMapper.class);
        Page<ManagedAccount> mapperPage = new Page<>(2, 15);
        when(accountMapper.paginateAs(eq(2L), eq(15L), any(QueryWrapper.class), eq(ManagedAccount.class)))
                .thenReturn(mapperPage);
        IamRepository repository = new IamRepository(
                mock(IamMapper.class), accountMapper, mock(IdGenerator.class), mock(ResourceAccess.class));

        repository.pageAccounts(AccountScopeType.GLOBAL, null, " Admin ", AccountStatus.ACTIVE, 2, 15);

        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(accountMapper).paginateAs(
                eq(2L), eq(15L), wrapperCaptor.capture(), eq(ManagedAccount.class));
    }

    @Test
    void shouldIncludeExactAccountIdWhenSearchIsNumeric() {
        IamAccountMapper accountMapper = mock(IamAccountMapper.class);
        when(accountMapper.paginateAs(eq(1L), eq(20L), any(QueryWrapper.class), eq(ManagedAccount.class)))
                .thenReturn(new Page<>(1, 20));
        IamRepository repository = new IamRepository(
                mock(IamMapper.class), accountMapper, mock(IdGenerator.class), mock(ResourceAccess.class));

        repository.pageAccounts(AccountScopeType.GLOBAL, null, "10001", null, 1, 20);

        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(accountMapper).paginateAs(
                eq(1L), eq(20L), wrapperCaptor.capture(), eq(ManagedAccount.class));
    }

    @Test
    void shouldReadAccountReferencesInOneSafeProjectionQuery() {
        IamAccountMapper accountMapper = mock(IamAccountMapper.class);
        when(accountMapper.selectListByQueryAs(any(QueryWrapper.class), eq(AccountReference.class)))
                .thenReturn(List.of(new AccountReference(
                        80L, "平台账号", AccountScopeType.GLOBAL, null, false)));
        IamRepository repository = new IamRepository(
                mock(IamMapper.class), accountMapper, mock(IdGenerator.class), mock(ResourceAccess.class));

        assertThat(repository.findAccountReferences(Set.of(80L)))
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.getAccountId()).isEqualTo(80L);
                    assertThat(reference.getDisplayName()).isEqualTo("平台账号");
                });

        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(accountMapper).selectListByQueryAs(wrapperCaptor.capture(), eq(AccountReference.class));
    }

    @Test
    void shouldDelegateReachabilityAndIsolateWithAccountVersion() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.hasUnreachableActivePrincipal(70L, 80L)).thenReturn(true);
        when(mapper.isolateAccount(80L, 4L)).thenReturn(1);
        IamRepository repository = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class));

        assertThat(repository.hasUnreachableActivePrincipal(70L, 80L)).isTrue();
        repository.isolateAccount(80L, 4L);

        verify(mapper).hasUnreachableActivePrincipal(70L, 80L);
        verify(mapper).isolateAccount(80L, 4L);
    }

    @Test
    void shouldBatchPlatformPrincipalsAndRolesWithTwoQueries() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.selectListByQueryAs(any(QueryWrapper.class), eq(IamPrincipalEntity.class)))
                .thenReturn(List.of(
                        new IamPrincipalEntity()
                                .setId(30L)
                                .setSubjectId(10L)
                                .setPrincipalType(PrincipalType.PLATFORM)
                                .setStatus(PrincipalStatus.ACTIVE)
                                .setVersion(2L),
                        new IamPrincipalEntity()
                                .setId(31L)
                                .setSubjectId(11L)
                                .setPrincipalType(PrincipalType.PLATFORM)
                                .setStatus(PrincipalStatus.DISABLED)
                                .setVersion(4L)));
        when(mapper.selectPlatformPrincipalRoles(List.of(30L, 31L)))
                .thenReturn(List.of(new PlatformRole()
                        .setPrincipalId(30L)
                        .setRoleId(40L)
                        .setRoleCode("PLATFORM_ADMIN")
                        .setRoleName("平台管理员")));

        Map<Long, PlatformPrincipal> principals = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .findPlatformPrincipalsBySubjectIds(List.of(10L, 11L, 10L));

        assertThat(principals).containsOnlyKeys(10L, 11L);
        assertThat(principals.get(10L).getRoles())
                .extracting(PlatformRole::getRoleId)
                .containsExactly(40L);
        assertThat(principals.get(11L).getRoles()).isEmpty();
        verify(mapper, times(1)).selectListByQueryAs(any(QueryWrapper.class), eq(IamPrincipalEntity.class));
        verify(mapper, times(1)).selectPlatformPrincipalRoles(List.of(30L, 31L));
    }

    @Test
    void shouldBuildActivePlatformRoleOptionsQuery() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.selectListByQueryAs(any(QueryWrapper.class), eq(IamRoleEntity.class)))
                .thenReturn(List.of(new IamRoleEntity()
                        .setId(40L)
                        .setCode("PLATFORM_ADMIN")
                        .setName("平台管理员")));

        List<PlatformRole> roles = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .findAssignablePlatformRoles();

        assertThat(roles).extracting(PlatformRole::getRoleId).containsExactly(40L);
        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectListByQueryAs(wrapperCaptor.capture(), eq(IamRoleEntity.class));
    }

    @Test
    void shouldBuildActiveTenantRoleOptionsQuery() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.selectListByQueryAs(any(QueryWrapper.class), eq(IamRoleEntity.class)))
                .thenReturn(List.of(new IamRoleEntity()
                        .setId(40L)
                        .setCode("TENANT_ADMIN")
                        .setName("租户管理员")));

        List<TenantRole> roles = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .findAssignableTenantRoles(20L);

        assertThat(roles).extracting(TenantRole::getRoleId).containsExactly(40L);
        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectListByQueryAs(wrapperCaptor.capture(), eq(IamRoleEntity.class));
    }

    @Test
    void shouldReadSubjectThroughTypedQueryWithoutBaseMapperPrimaryKeyMetadata() {
        IamMapper mapper = mock(IamMapper.class);
        IamSubjectEntity subject = new IamSubjectEntity()
                .setId(10L)
                .setStatus(SubjectStatus.ACTIVE)
                .setVersion(3L);
        when(mapper.selectOneByQueryAs(any(QueryWrapper.class), eq(IamSubjectEntity.class)))
                .thenReturn(subject);

        IamSubjectEntity loaded = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .findSubject(10L);

        assertThat(loaded.getId()).isEqualTo(10L);
        assertThat(loaded.getStatus()).isEqualTo(SubjectStatus.ACTIVE);
        assertThat(loaded.getVersion()).isEqualTo(3L);
        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectOneByQueryAs(wrapperCaptor.capture(), eq(IamSubjectEntity.class));
    }

    @Test
    void shouldPersistCompleteAccountInsertFields() {
        IamMapper mapper = mock(IamMapper.class);
        IamAccountMapper accountMapper = mock(IamAccountMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(20L);
        IamRepository repository = new IamRepository(
                mapper,
                accountMapper,
                idGenerator,
                mock(ResourceAccess.class));

        IamAccountEntity account = repository.createAccount(10L, AccountScopeType.GLOBAL, null);

        ArgumentCaptor<IamAccountEntity> inserted = ArgumentCaptor.forClass(IamAccountEntity.class);
        verify(accountMapper).insert(inserted.capture());
        IamAccountEntity stored = inserted.getValue();
        assertThat(account.getId()).isEqualTo(20L);
        assertThat(account.getSubjectId()).isEqualTo(10L);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(Boolean.TRUE.equals(account.getSecondFactorRequired())).isFalse();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
        assertThat(account.getVersion()).isZero();
        assertThat(stored.getDeleted()).isZero();
    }

    @Test
    void shouldAllocateIndependentPrincipalIdsInsideRepository() {
        IamMapper mapper = mock(IamMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(30L, 31L);
        IamRepository repository = new IamRepository(
                mapper, mock(IamAccountMapper.class), idGenerator, mock(ResourceAccess.class));

        IamPrincipalEntity platform = repository.createPrincipal(11L, PrincipalType.PLATFORM, null, "管理员");
        IamPrincipalEntity defaultPrincipal = repository.createDefaultPrincipal(11L, "默认主体");

        assertThat(platform.getId()).isEqualTo(30L);
        assertThat(defaultPrincipal.getId()).isEqualTo(31L);
        ArgumentCaptor<IamPrincipalEntity> inserted = ArgumentCaptor.forClass(IamPrincipalEntity.class);
        verify(mapper, times(2)).insertPrincipal(inserted.capture());
        assertThat(inserted.getAllValues())
                .extracting(IamPrincipalEntity::getId)
                .containsExactly(30L, 31L);
    }

    @Test
    void shouldOnlyTranslatePrincipalScopeUniqueConstraint() {
        IamMapper mapper = mock(IamMapper.class);
        doThrow(new DataIntegrityViolationException(
                "duplicate principal scope",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key\0nuk_iam_principal_subject_type_tenant\0\0"))))
                .when(mapper)
                .insertPrincipal(any());

        assertThatThrownBy(() -> new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .createPrincipal(11L, PrincipalType.PLATFORM, null, "管理员"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(IamErrorCode.PRINCIPAL_EXISTS));

        DataIntegrityViolationException primaryKeyFailure = new DataIntegrityViolationException(
                "duplicate principal id",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key\0niam_principal_pkey\0\0")));
        IamMapper primaryKeyMapper = mock(IamMapper.class);
        doThrow(primaryKeyFailure).when(primaryKeyMapper).insertPrincipal(any());

        assertThatThrownBy(() -> new IamRepository(
                primaryKeyMapper,
                mock(IamAccountMapper.class),
                mock(IdGenerator.class),
                mock(ResourceAccess.class))
                .createPrincipal(11L, PrincipalType.PLATFORM, null, "管理员"))
                .isSameAs(primaryKeyFailure);
    }

    @Test
    void shouldOnlyTranslateTenantCodeUniqueConstraint() {
        IamMapper mapper = mock(IamMapper.class);
        doThrow(new DataIntegrityViolationException(
                "duplicate tenant code",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key\0nuk_iam_tenant_code\0\0"))))
                .when(mapper)
                .insertTenant(any(IamTenantEntity.class));

        assertThatThrownBy(() -> new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .createTenant("acme", "Acme", TenantType.DEFAULT))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TenantErrorCode.TENANT_CODE_EXISTS));

        DataIntegrityViolationException primaryKeyFailure = new DataIntegrityViolationException(
                "duplicate tenant id",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key\0niam_tenant_pkey\0\0")));
        IamMapper primaryKeyMapper = mock(IamMapper.class);
        doThrow(primaryKeyFailure).when(primaryKeyMapper).insertTenant(any(IamTenantEntity.class));

        assertThatThrownBy(() -> new IamRepository(
                primaryKeyMapper,
                mock(IamAccountMapper.class),
                mock(IdGenerator.class),
                mock(ResourceAccess.class))
                .createTenant("acme", "Acme", TenantType.DEFAULT))
                .isSameAs(primaryKeyFailure);
    }

    @Test
    void shouldPersistTenantAndInitialCodeAsOneAggregate() {
        IamMapper mapper = mock(IamMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(20L);

        Long tenantId = new IamRepository(
                mapper, mock(IamAccountMapper.class), idGenerator, mock(ResourceAccess.class))
                .createTenant("acme", "Acme", TenantType.DEFAULT);

        ArgumentCaptor<IamTenantEntity> inserted = ArgumentCaptor.forClass(IamTenantEntity.class);
        verify(mapper).insertTenant(inserted.capture());
        assertThat(tenantId).isEqualTo(20L);
        assertThat(inserted.getValue().getId()).isEqualTo(20L);
        verify(mapper).insertTenantCode(20L, "acme");
    }

    @Test
    void shouldResolveAuthenticationTenantByCurrentCodeWithoutAccessCriteria() {
        IamMapper mapper = mock(IamMapper.class);
        Tenant tenant = new Tenant();
        tenant.setTenantId(20L);
        tenant.setCode("acme");
        when(mapper.selectOneByQueryAs(any(QueryWrapper.class), eq(Tenant.class)))
                .thenReturn(tenant);
        IamRepository repository = new IamRepository(
                mapper,
                mock(IamAccountMapper.class),
                mock(IdGenerator.class),
                mock(ResourceAccess.class));

        assertThat(repository.findTenantByCode("acme")).isSameAs(tenant);

        ArgumentCaptor<QueryWrapper> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectOneByQueryAs(query.capture(), eq(Tenant.class));
    }

    @Test
    void shouldAppendChangedTenantCodeAfterVersionedUpdate() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.updateTenant(20L, "acme-new", "Acme New", 3L)).thenReturn("acme");
        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));

        new IamRepository(mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), resourceAccess)
                .updateTenant(20L, "acme-new", "Acme New", 3L);

        verify(mapper).insertTenantCode(20L, "acme-new");
    }

    @Test
    void shouldNotAppendTenantCodeWhenCodeDidNotChange() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.updateTenant(20L, "acme", "Acme New", 3L)).thenReturn("acme");
        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));

        new IamRepository(mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), resourceAccess)
                .updateTenant(20L, "acme", "Acme New", 3L);

        verify(mapper, never()).insertTenantCode(any(), any());
    }

    @Test
    void shouldRejectPreviouslyUsedTenantCodeAndPreserveOtherIntegrityFailures() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.updateTenant(20L, "legacy", "Acme", 3L)).thenReturn("acme");
        doThrow(new DataIntegrityViolationException(
                "historical tenant code",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key\0npk_iam_tenant_code\0\0"))))
                .when(mapper)
                .insertTenantCode(20L, "legacy");
        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));

        assertThatThrownBy(() -> new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), resourceAccess)
                .updateTenant(20L, "legacy", "Acme", 3L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TenantErrorCode.TENANT_CODE_EXISTS));

        IamMapper foreignKeyMapper = mock(IamMapper.class);
        when(foreignKeyMapper.updateTenant(20L, "acme-new", "Acme", 3L)).thenReturn("acme");
        DataIntegrityViolationException foreignKeyFailure = new DataIntegrityViolationException(
                "missing tenant", new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23503\0Mforeign key\0nfk_iam_tenant_code_tenant\0\0")));
        doThrow(foreignKeyFailure).when(foreignKeyMapper).insertTenantCode(20L, "acme-new");

        assertThatThrownBy(() -> new IamRepository(
                foreignKeyMapper,
                mock(IamAccountMapper.class),
                mock(IdGenerator.class),
                resourceAccess)
                .updateTenant(20L, "acme-new", "Acme", 3L))
                .isSameAs(foreignKeyFailure);
    }

    @Test
    void shouldRejectStaleTenantUpdateBeforeAppendingCode() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.updateTenant(20L, "acme-new", "Acme", 3L)).thenReturn(null);
        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));

        assertThatThrownBy(() -> new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), resourceAccess)
                .updateTenant(20L, "acme-new", "Acme", 3L))
                .isInstanceOf(ConcurrencyConflictException.class);

        verify(mapper, never()).insertTenantCode(any(), any());
    }

    @Test
    void shouldBuildTypedTenantPagination() {
        IamMapper mapper = mock(IamMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        Page<Tenant> mapperPage = new Page<>(2, 15);
        when(mapper.paginateAs(eq(2L), eq(15L), any(QueryWrapper.class), eq(Tenant.class)))
                .thenReturn(mapperPage);

        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));
        new IamRepository(mapper, mock(IamAccountMapper.class), idGenerator, resourceAccess)
                .pageTenants("华东", TenantStatus.ACTIVE, 2, 15);

        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).paginateAs(eq(2L), eq(15L), wrapperCaptor.capture(), eq(Tenant.class));
    }

    @Test
    void shouldReturnDisabledPlatformAdministratorRoleMaintainedBySeed() {
        IamMapper mapper = mock(IamMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        IamRoleEntity disabledRole = new IamRoleEntity()
                .setId(10L)
                .setCode("PLATFORM_ADMIN")
                .setName("平台管理员")
                .setStatus(RoleStatus.DISABLED);
        when(mapper.selectOneByQueryAs(any(QueryWrapper.class), eq(IamRoleEntity.class)))
                .thenReturn(disabledRole);
        Long synchronizedRoleId = new IamRepository(
                mapper, mock(IamAccountMapper.class), idGenerator, mock(ResourceAccess.class))
                .requirePlatformAdministratorRoleId();

        assertThat(synchronizedRoleId).isEqualTo(10L);
    }

    @Test
    void shouldRejectMissingPlatformAdministratorAfterSeed() {
        IamMapper mapper = mock(IamMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(mapper.selectOneByQueryAs(any(QueryWrapper.class), eq(IamRoleEntity.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> new IamRepository(
                mapper, mock(IamAccountMapper.class), idGenerator, mock(ResourceAccess.class))
                .requirePlatformAdministratorRoleId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_ADMIN");
    }

    @Test
    void shouldCreateTenantAdministratorAsBuiltInRole() {
        IamMapper mapper = mock(IamMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(10L);
        when(mapper.selectObjectListByQueryAs(any(QueryWrapper.class), eq(Long.class)))
                .thenReturn(List.of());

        new IamRepository(mapper, mock(IamAccountMapper.class), idGenerator, mock(ResourceAccess.class))
                .createTenantRoleWithPermissions(20L, "TENANT_ADMIN", "租户管理员", "tenant");

        ArgumentCaptor<IamRoleEntity> roleCaptor = ArgumentCaptor.forClass(IamRoleEntity.class);
        verify(mapper).insertRole(roleCaptor.capture());
        assertThat(roleCaptor.getValue())
                .extracting(
                        IamRoleEntity::getCode,
                        IamRoleEntity::getTenantId,
                        IamRoleEntity::getBuiltIn,
                        IamRoleEntity::getStatus)
                .containsExactly("TENANT_ADMIN", 20L, true, RoleStatus.ACTIVE);
    }

    @Test
    void shouldRejectRoleReplacementBeforeDeletingRelationsWhenVersionChanged() {
        IamMapper mapper = mock(IamMapper.class);
        IamRepository repository = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class));
        when(mapper.advanceTenantPrincipalVersion(30L, 20L, 4L)).thenReturn(0);

        assertThatThrownBy(() -> repository.replaceTenantPrincipalRoles(30L, 20L, List.of(40L), 4L))
                .isInstanceOf(ConcurrencyConflictException.class);

        verify(mapper, never()).deletePrincipalRoles(30L);
        verify(mapper, never()).insertPrincipalRoles(ArgumentMatchers.any());
    }

    @Test
    void shouldRejectTenantStatusChangeOutsideAtomicTenantScope() {
        IamMapper mapper = mock(IamMapper.class);
        IamRepository repository = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class));
        when(mapper.updateTenantMembershipStatus(30L, 20L, MembershipStatus.DISABLED, 4L))
                .thenReturn(0);

        assertThatThrownBy(() -> repository.updateTenantMembershipStatus(
                30L, 20L, MembershipStatus.DISABLED, 4L))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void shouldIsolateTenantGraphAndRegisterCleanupInFixedCalls() {
        IamMapper mapper = mock(IamMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(mapper.isolateTenant(20L, 3L)).thenReturn(1);
        when(mapper.selectTenantSubjectIds(20L)).thenReturn(List.of(31L, 32L));
        when(idGenerator.nextId()).thenReturn(40L);

        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));
        List<Long> identities = new IamRepository(
                mapper, mock(IamAccountMapper.class), idGenerator, resourceAccess)
                .isolateDeletedTenant(20L, 3L);

        assertThat(identities).containsExactly(31L, 32L);
        verify(mapper).isolateTenant(20L, 3L);
        verify(mapper).selectTenantSubjectIds(20L);
        verify(mapper).isolateTenantMemberships(20L);
        verify(mapper).isolateTenantPrincipals(20L);
        verify(mapper).isolateTenantRoles(20L);
        verify(mapper).insertTenantCleanupTask(40L, 20L);
    }

    @Test
    void shouldSelectOnlySafeCredentialManagementFields() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.selectCredentialRowsBySubjectIds(List.of(10L), Set.of(CredentialType.PASSWORD)))
                .thenReturn(List.of(new CredentialRow()
                        .setCredentialId(20L)
                        .setAccountId(30L)
                        .setSubjectId(10L)
                        .setCredentialType(CredentialType.PASSWORD)
                        .setStatus(CredentialStatus.ACTIVE)
                        .setVersion(3L)));

        var credentials = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .findCredentials(List.of(10L), Set.of(CredentialType.PASSWORD));

        assertThat(credentials.get(10L)).singleElement().satisfies(credential -> {
            assertThat(credential.getId()).isEqualTo(20L);
            assertThat(credential.getAccountId()).isEqualTo(30L);
            assertThat(credential.getCredentialType()).isEqualTo(CredentialType.PASSWORD);
        });
        verify(mapper).selectCredentialRowsBySubjectIds(List.of(10L), Set.of(CredentialType.PASSWORD));
    }

    @Test
    void shouldSelectOnlySafeCredentialReferenceFieldsInOneBatch() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.selectCredentialReferenceRows(Set.of(20L, 21L))).thenReturn(List.of());
        IamRepository repository = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class));

        repository.findCredentialReferences(Set.of(20L, 21L));

        verify(mapper).selectCredentialReferenceRows(Set.of(20L, 21L));
    }

    @Test
    void shouldTreatCredentialVersionMismatchAsConcurrencyConflict() {
        IamMapper mapper = mock(IamMapper.class);
        IamRepository repository = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class));
        when(mapper.updateCredentialStatus(20L, 10L, CredentialStatus.DISABLED, 3L)).thenReturn(0);

        assertThatThrownBy(() -> repository.updateCredentialStatus(
                20L, 10L, CredentialStatus.DISABLED, 3L))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void shouldUpdateSecondFactorRequirementWithAccountVersion() {
        IamAccountMapper accountMapper = mock(IamAccountMapper.class);
        when(accountMapper.updateByQuery(any(IamAccountEntity.class), any(QueryWrapper.class))).thenReturn(1);
        IamRepository repository = new IamRepository(
                mock(IamMapper.class), accountMapper, mock(IdGenerator.class), mock(ResourceAccess.class));
        ArgumentCaptor<IamAccountEntity> updateCaptor = ArgumentCaptor.forClass(IamAccountEntity.class);

        repository.changeSecondFactorRequirement(30L, true, 4L);

        verify(accountMapper).updateByQuery(updateCaptor.capture(), any(QueryWrapper.class));
        assertThat(updateCaptor.getValue().getSecondFactorRequired()).isTrue();
        assertThat(updateCaptor.getValue().getVersion()).isEqualTo(5L);
        assertThat(updateCaptor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectChangedAccountSecondFactorRequirement() {
        IamAccountMapper accountMapper = mock(IamAccountMapper.class);
        when(accountMapper.updateByQuery(any(IamAccountEntity.class), any(QueryWrapper.class))).thenReturn(0);
        IamRepository repository = new IamRepository(
                mock(IamMapper.class), accountMapper, mock(IdGenerator.class), mock(ResourceAccess.class));

        assertThatThrownBy(() -> repository.changeSecondFactorRequirement(30L, true, 4L))
                .isInstanceOf(ConcurrencyConflictException.class)
                .satisfies(exception -> {
                    ConcurrencyConflictException conflict = (ConcurrencyConflictException) exception;
                    assertThat(conflict.getErrorCode()).isEqualTo(IamErrorCode.ACCOUNT_CHANGED);
                    assertThat(conflict.getRefreshTarget()).isEqualTo(IamClientStateTargets.ACCOUNTS);
                });
    }
}
