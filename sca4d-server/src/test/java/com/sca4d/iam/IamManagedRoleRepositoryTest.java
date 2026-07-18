package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.mapper.IamAccountMapper;
import com.sca4d.mapper.IamMapper;
import com.sca4d.role.ManagedPermission;
import com.sca4d.role.ManagedRole;
import com.sca4d.role.repository.RoleUsageCount;
import com.sca4d.tenant.policy.TenantAccessCriteria;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;

/** 验证管理角色读取、权限集合和使用数量查询。 */
class IamManagedRoleRepositoryTest {

    @Test
    void shouldPageExactPlatformScopeAndAggregateWholePageUsageOnce() {
        IamMapper mapper = mock(IamMapper.class);
        ManagedRole first = new ManagedRole().setRoleId(10L).setCode("AUDITOR");
        ManagedRole second = new ManagedRole().setRoleId(11L).setCode("OPERATOR");
        Page<ManagedRole> mapperPage = new Page<>(2, 20);
        mapperPage.setRecords(List.of(first, second));
        when(mapper.paginateAs(eq(2L), eq(20L), any(QueryWrapper.class), eq(ManagedRole.class)))
                .thenReturn(mapperPage);
        when(mapper.selectRoleUsageCounts(List.of(10L, 11L)))
                .thenReturn(List.of(
                        new RoleUsageCount().setRoleId(10L).setPrincipalCount(0L).setPermissionCount(2L),
                        new RoleUsageCount().setRoleId(11L).setPrincipalCount(3L).setPermissionCount(4L)));

        var page = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .pageRoles(null, " audit ", null, 2, 20);

        assertThat(page.getRecords()).containsExactly(first, second);
        assertThat(first.getPrincipalCount()).isZero();
        assertThat(first.getPermissionCount()).isEqualTo(2);
        assertThat(second.getPrincipalCount()).isEqualTo(3);
        assertThat(second.getPermissionCount()).isEqualTo(4);
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).paginateAs(eq(2L), eq(20L), queryCaptor.capture(), eq(ManagedRole.class));
        verify(mapper).selectRoleUsageCounts(List.of(10L, 11L));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void shouldPageExactTenantScopeWithoutUsageQueryForEmptyPage() {
        IamMapper mapper = mock(IamMapper.class);
        Page<ManagedRole> mapperPage = new Page<>(1, 20);
        mapperPage.setRecords(List.of());
        when(mapper.paginateAs(eq(1L), eq(20L), any(QueryWrapper.class), eq(ManagedRole.class)))
                .thenReturn(mapperPage);

        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));
        new IamRepository(mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), resourceAccess)
                .pageRoles(30L, null, null, 1, 20);

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).paginateAs(eq(1L), eq(20L), queryCaptor.capture(), eq(ManagedRole.class));
        verify(mapper, never()).selectRoleUsageCounts(any());
    }

    @Test
    void shouldUseTypedTenantPermissionPrefixBoundary() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.selectListByQueryAs(any(QueryWrapper.class), eq(ManagedPermission.class)))
                .thenReturn(List.of());

        new IamRepository(mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .findAssignableRolePermissions(true);

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectListByQueryAs(queryCaptor.capture(), eq(ManagedPermission.class));
    }

    @Test
    void shouldOnlyTranslateRoleCodeUniqueConstraint() {
        IamMapper mapper = mock(IamMapper.class);
        doThrow(new DataIntegrityViolationException(
                "duplicate role code",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key\0nuk_iam_role_code_tenant\0\0"))))
                .when(mapper)
                .insertRole(any());

        assertThatThrownBy(() -> new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), mock(ResourceAccess.class))
                .createManagedRole(null, "AUDITOR", "审计员"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.role.code-exists"));

        DataIntegrityViolationException primaryKeyFailure = new DataIntegrityViolationException(
                "duplicate role id",
                new PSQLException(new ServerErrorMessage(
                        "SERROR\0C23505\0Mduplicate key\0niam_role_pkey\0\0")));
        IamMapper primaryKeyMapper = mock(IamMapper.class);
        doThrow(primaryKeyFailure).when(primaryKeyMapper).insertRole(any());

        assertThatThrownBy(() -> new IamRepository(
                primaryKeyMapper,
                mock(IamAccountMapper.class),
                mock(IdGenerator.class),
                mock(ResourceAccess.class))
                .createManagedRole(null, "AUDITOR", "审计员"))
                .isSameAs(primaryKeyFailure);
    }

    @Test
    void shouldRejectChangedRoleBeforeDeletingPermissionRelations() {
        IamMapper mapper = mock(IamMapper.class);
        when(mapper.deleteCustomRole(10L, 20L, 3L)).thenReturn(0);
        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));
        IamRepository repository = new IamRepository(
                mapper, mock(IamAccountMapper.class), mock(IdGenerator.class), resourceAccess);

        assertThatThrownBy(() -> repository.deleteManagedRole(20L, 10L, 3L))
                .isInstanceOf(ConcurrencyConflictException.class);

        verify(mapper, never()).deleteRolePermissions(10L);
    }

}
