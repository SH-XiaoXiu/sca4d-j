package com.sca4d.role.service;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.validation.ValidationPatterns;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.RoleStatus;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.role.ManagedPermission;
import com.sca4d.role.ManagedRole;
import com.sca4d.role.RoleErrorCode;
import com.sca4d.role.RoleService;
import com.sca4d.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 角色管理业务规则实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultRoleService implements RoleService {

    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";
    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final IamRepository repository;
    private final PrincipalService principalService;
    private final LocalizedMessages messages;
    private final TenantService tenantService;

    @Override
    public @NotNull PageData<ManagedRole> page(
            @Nullable Long tenantId,
            @Nullable String search,
            @Nullable RoleStatus status,
            long page,
            long size) {
        if (tenantId != null) {
            tenantService.require(tenantId);
        }
        return repository.pageRoles(
                tenantId, StringUtils.hasText(search) ? search.trim() : null, status, page, size);
    }

    @Override
    public @NotNull ManagedRole detail(@Nullable Long tenantId, @NotNull Long roleId) {
        if (tenantId != null) {
            tenantService.require(tenantId);
        }
        ManagedRole role = repository.findManagedRole(tenantId, roleId);
        if (role == null) {
            throw new BizException(RoleErrorCode.ROLE_NOT_FOUND);
        }
        // 详情固定由三条 SQL 完成；每项只查询内存 MessageSource，不追加数据库或外部调用。
        for (ManagedPermission permission : role.getPermissions()) {
            permission.setName(messages.get(permission.getNameKey()));
        }
        return role;
    }

    @Override
    public @NotNull List<ManagedPermission> assignablePermissions(@Nullable Long tenantId) {
        if (tenantId != null) {
            tenantService.require(tenantId);
        }
        List<ManagedPermission> permissions = repository.findAssignableRolePermissions(tenantId != null);
        // 权限目录固定由一条 SQL 读取；每项只解析一次当前语言名称，不形成持久化 N+1。
        for (ManagedPermission permission : permissions) {
            permission.setName(messages.get(permission.getNameKey()));
        }
        return permissions;
    }

    @Override
    @Transactional
    public @NotNull Long create(
            @Nullable Long tenantId,
            @NotNull String code,
            @NotNull String name,
            @NotNull List<Long> permissionIds) {
        // 租户角色写入先持有租户共享行锁，再连续完成编码、名称、权限范围校验和关系写入。
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        String checkedCode = code.trim();
        if (checkedCode.length() > 64 || !ValidationPatterns.isBizCode(checkedCode)) {
            throw new BizException(RoleErrorCode.ROLE_CODE_INVALID);
        }
        if ((tenantId == null && PLATFORM_ADMIN_ROLE.equals(checkedCode))
                || (tenantId != null && TENANT_ADMIN_ROLE.equals(checkedCode))) {
            throw new BizException(RoleErrorCode.ROLE_BUILT_IN_CODE_RESERVED);
        }
        String checkedName = name.trim();
        if (checkedName.isEmpty() || checkedName.length() > 128) {
            throw new BizException(RoleErrorCode.ROLE_NAME_INVALID);
        }
        List<Long> checkedPermissionIds = List.copyOf(new LinkedHashSet<>(permissionIds));
        Set<Long> allowedIds = assignablePermissions(tenantId).stream()
                .map(ManagedPermission::getPermissionId)
                .collect(Collectors.toSet());
        if (!allowedIds.containsAll(checkedPermissionIds)) {
            throw new BizException(RoleErrorCode.ROLE_PERMISSION_OUT_OF_SCOPE);
        }
        Long roleId = repository.createManagedRole(tenantId, checkedCode, checkedName);
        repository.replaceManagedRolePermissions(roleId, checkedPermissionIds);
        log.info(
                "角色创建已写入事务 | roleId={} tenantId={} status={} permissionCount={}",
                roleId,
                tenantId,
                RoleStatus.ACTIVE,
                checkedPermissionIds.size());
        return roleId;
    }

    @Override
    @Transactional
    public void update(
            @Nullable Long tenantId,
            @NotNull Long roleId,
            @NotNull String name,
            @NotNull RoleStatus status,
            long version) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        // 角色资料更新先确认作用域内目标存在，再校验名称并以乐观锁提交状态变化。
        ManagedRole role = detail(tenantId, roleId);
        String checkedName = name.trim();
        if (checkedName.isEmpty() || checkedName.length() > 128) {
            throw new BizException(RoleErrorCode.ROLE_NAME_INVALID);
        }
        repository.updateManagedRole(tenantId, roleId, checkedName, status, version);
        if (role.getStatus() != status) {
            principalService.refreshRoleSessions(roleId);
        }
        log.info(
                "角色资料更新已写入事务 | roleId={} tenantId={} status={} version={}",
                roleId,
                tenantId,
                status,
                version + 1);
    }

    @Override
    @Transactional
    public void replacePermissions(
            @Nullable Long tenantId,
            @NotNull Long roleId,
            @NotNull List<Long> permissionIds,
            long version) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        // 权限替换连续完成角色保护、权限范围校验、版本推进和关联 Session 刷新。
        ManagedRole role = detail(tenantId, roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BizException(RoleErrorCode.ROLE_BUILT_IN_PERMISSIONS_READONLY);
        }
        List<Long> checkedPermissionIds = List.copyOf(new LinkedHashSet<>(permissionIds));
        Set<Long> allowedIds = assignablePermissions(tenantId).stream()
                .map(ManagedPermission::getPermissionId)
                .collect(Collectors.toSet());
        if (!allowedIds.containsAll(checkedPermissionIds)) {
            throw new BizException(RoleErrorCode.ROLE_PERMISSION_OUT_OF_SCOPE);
        }
        repository.updateManagedRole(tenantId, roleId, role.getName(), role.getStatus(), version);
        repository.replaceManagedRolePermissions(roleId, checkedPermissionIds);
        principalService.refreshRoleSessions(roleId);
        log.info(
                "角色权限更新已写入事务 | roleId={} tenantId={} version={} permissionCount={}",
                roleId,
                tenantId,
                version + 1,
                checkedPermissionIds.size());
    }

    @Override
    @Transactional
    public void delete(@Nullable Long tenantId, @NotNull Long roleId, long version) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        ManagedRole role = detail(tenantId, roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BizException(RoleErrorCode.ROLE_BUILT_IN_DELETE_FORBIDDEN);
        }
        if (role.getPrincipalCount() > 0) {
            throw new BizException(RoleErrorCode.ROLE_IN_USE);
        }
        repository.deleteManagedRole(tenantId, roleId, version);
        log.info("角色删除已写入事务 | roleId={} tenantId={}", roleId, tenantId);
    }
}
