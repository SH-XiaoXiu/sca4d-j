package com.sca4d.iam.rbac;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.core.spring.transaction.TransactionCallbacks;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 校验并修改具体 {@link RbacScope} 中的角色、权限和 Principal 角色关系。
 */
public class RbacService {

    private static final Logger LOG = LoggerFactory.getLogger(RbacService.class);

    private static final int MAX_ROLE_CODE_LENGTH = 128;

    private final @NotNull RbacRepositoryRegistry repositories;

    private final @NotNull RbacSessionInvalidationRegistry invalidations;

    private final @NotNull PermissionSelectionRegistry permissionSelections;

    private final @NotNull RbacPermissionCache permissionCache;

    private final @NotNull IdGenerator idGenerator;

    /**
     * 创建通用 RBAC 服务。
     *
     * @param repositories         按 RBAC 领域登记的角色 Repository
     * @param invalidations        按 RBAC 领域登记的可选 Session 失效实现
     * @param permissionSelections 按 RBAC 领域登记的权限选择规则
     * @param permissionCache      Principal 有效权限选择符缓存
     * @param idGenerator          角色标识生成器
     */
    public RbacService(
            @NotNull RbacRepositoryRegistry repositories,
            @NotNull RbacSessionInvalidationRegistry invalidations,
            @NotNull PermissionSelectionRegistry permissionSelections,
            @NotNull RbacPermissionCache permissionCache,
            @NotNull IdGenerator idGenerator) {
        this.repositories = Objects.requireNonNull(repositories, "RbacRepository 注册表不能为空");
        this.invalidations = Objects.requireNonNull(invalidations, "Session 失效实现注册表不能为空");
        this.permissionSelections = Objects.requireNonNull(permissionSelections, "权限选择规则注册表不能为空");
        this.permissionCache = Objects.requireNonNull(permissionCache, "RBAC 权限缓存不能为空");
        this.idGenerator = Objects.requireNonNull(idGenerator, "ID 生成器不能为空");
    }

    /**
     * @return 指定角色空间中按角色编码排序的全部角色
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable List<@NotNull Role> findRoles(@NotNull RbacScope scope) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        List<Role> roles = repository(checkedScope).findRoles(checkedScope);
        LOG.debug("角色列表读取完成 | scope={} roleCount={}", checkedScope, roles.size());
        return roles;
    }

    /**
     * 读取指定角色空间中的角色。
     *
     * @throws BusinessException 角色不存在时抛出
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull Role loadRoleById(@NotNull RbacScope scope, @NotNull Long roleId) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        Role role = repository(checkedScope)
                .findRole(checkedScope, Objects.requireNonNull(roleId, "角色标识不能为空"))
                .orElseThrow(() -> new BusinessException(RbacErrorCode.ROLE_NOT_FOUND));
        LOG.trace("角色读取完成 | scope={} roleId={}", checkedScope, role.roleId());
        return role;
    }

    /**
     * 创建自定义角色并保存当前领域允许的初始权限编码。
     */
    @CheckReturnValue
    @Transactional
    public @NotNull RolePermissions createRole(
            @NotNull RbacScope scope,
            @NotNull String code,
            @NotNull Collection<@NotNull TreeCode> permissionCodes) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        String checkedCode = validateRoleCode(code);
        PermissionSelections selections = permissionSelections.selection(checkedScope.domain());
        List<PermissionSelector> selectors = selections.validateGrantableCodes(permissionCodes);
        Role role = repository(checkedScope)
                .createRole(
                        checkedScope,
                        idGenerator.nextId().value(),
                        checkedCode,
                        RoleStatus.ACTIVE,
                        false,
                        selectors);
        LOG.debug(
                "角色创建已写入事务 | scope={} roleId={} permissionCount={}",
                checkedScope,
                role.roleId(),
                selectors.size());
        return new RolePermissions(role, selections.expand(selectors));
    }

    /**
     * 修改角色状态，并在事务提交后清除所有使用者的授权缓存和可选 Session。
     */
    @CheckReturnValue
    @Transactional
    public @NotNull Role updateRoleStatus(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull RoleStatus status) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacRepository repository = repository(checkedScope);
        Role current = loadRoleById(checkedScope, roleId);
        RoleStatus checkedStatus = Objects.requireNonNull(status, "角色状态不能为空");
        if (current.builtIn() && checkedStatus != RoleStatus.ACTIVE) {
            throw new BusinessException(RbacErrorCode.BUILT_IN_ROLE_PROTECTED);
        }
        Role updated = repository.updateRoleStatus(checkedScope, roleId, checkedStatus);
        if (current.status() != updated.status()) {
            invalidateRolePrincipals(repository, checkedScope, roleId);
        }
        LOG.debug(
                "角色状态已写入事务 | scope={} roleId={} status={}",
                checkedScope,
                updated.roleId(),
                updated.status().value());
        return updated;
    }

    /**
     * 删除没有分配给 Principal 的自定义角色。
     */
    @Transactional
    public void deleteRole(@NotNull RbacScope scope, @NotNull Long roleId) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacRepository repository = repository(checkedScope);
        Role role = loadRoleById(checkedScope, Objects.requireNonNull(roleId, "角色标识不能为空"));
        if (role.builtIn()) {
            throw new BusinessException(RbacErrorCode.BUILT_IN_ROLE_PROTECTED);
        }
        repository.deleteRole(checkedScope, role.roleId());
        LOG.debug("角色删除已写入事务 | scope={} roleId={}", checkedScope, role.roleId());
    }

    /**
     * @return 角色及其当前得到的具体 Operation
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull RolePermissions findRolePermissions(@NotNull RbacScope scope, @NotNull Long roleId) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacRepository repository = repository(checkedScope);
        Role role = loadRoleById(checkedScope, roleId);
        return new RolePermissions(
                role,
                permissionSelections.selection(checkedScope.domain())
                        .expand(repository.findPermissionSelectors(checkedScope, roleId)));
    }

    /**
     * 原子替换角色的具体权限编码。
     */
    @CheckReturnValue
    @Transactional
    public @NotNull RolePermissions replaceRolePermissions(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull Collection<@NotNull TreeCode> permissionCodes) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacRepository repository = repository(checkedScope);
        Role current = loadRoleById(checkedScope, roleId);
        if (repository.findPermissionSelectors(checkedScope, current.roleId()).contains(PermissionSelections.ALL)) {
            throw new BusinessException(RbacErrorCode.BUILT_IN_ROLE_PROTECTED);
        }
        PermissionSelections selections = permissionSelections.selection(checkedScope.domain());
        List<PermissionSelector> selectors = selections.validateGrantableCodes(permissionCodes);
        Role updated = repository.replacePermissionSelectors(checkedScope, roleId, selectors);
        invalidateRolePrincipals(repository, checkedScope, roleId);
        LOG.debug(
                "角色权限已写入事务 | scope={} roleId={} permissionCount={}",
                checkedScope,
                updated.roleId(),
                selectors.size());
        return new RolePermissions(updated, selections.expand(selectors));
    }

    /**
     * @return Principal 当前角色和角色集合版本
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull PrincipalRoles findPrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Long principalId) {
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "Principal 标识不能为空");
        return Objects.requireNonNull(
                findPrincipalRoles(scope, List.of(checkedPrincipalId)).get(checkedPrincipalId),
                "RbacRepository 必须返回输入 Principal");
    }

    /**
     * 一次读取同一角色空间中多个 Principal 的角色关系。
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull PrincipalRoles> findPrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        LinkedHashSet<Long> unique = uniquePrincipalIds(principalIds);
        if (unique.isEmpty()) {
            return Map.of();
        }
        Map<Long, PrincipalRoles> result = repository(checkedScope).findPrincipalRoles(checkedScope, unique);
        validatePrincipalRolesResult(checkedScope, unique, result);
        return Map.copyOf(result);
    }

    /**
     * 原子替换 Principal 的非保护角色关系。
     */
    @CheckReturnValue
    @Transactional
    public @NotNull PrincipalRoles replacePrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Long principalId,
            @NotNull Collection<@NotNull Long> roleIds) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacRepository repository = repository(checkedScope);
        LinkedHashSet<Long> unique = uniqueRoleIds(roleIds);
        if (repository.findRoles(checkedScope, unique).size() != unique.size()) {
            throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
        }
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "Principal 标识不能为空");
        PrincipalRoles result = repository.replacePrincipalRoles(checkedScope, checkedPrincipalId, unique);
        invalidatePermissions(checkedScope, List.of(checkedPrincipalId));
        LOG.debug(
                "Principal 角色已写入事务 | scope={} principalId={} roleCount={}",
                checkedScope,
                result.principalId(),
                result.roles().size());
        return result;
    }

    /**
     * 原子批量增加或移除一个角色的 Principal 关系，并保留各 Principal 的其他角色。
     *
     * <p>空集合不执行持久化写入。任一标识重复、同时出现在添加和移除集合，或者角色和
     * Principal 无效时整批失败。只有实际变化的 Principal 会在事务提交后失效权限缓存和 Session。
     *
     * @param scope              角色空间
     * @param roleId             目标角色标识
     * @param addPrincipalIds    需要增加目标角色的 Principal 标识
     * @param removePrincipalIds 需要移除目标角色的 Principal 标识
     * @return 每个请求 Principal 修改后的角色关系
     */
    @CheckReturnValue
    @Transactional
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull PrincipalRoles> changeRoleAssignments(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull Collection<@NotNull Long> addPrincipalIds,
            @NotNull Collection<@NotNull Long> removePrincipalIds) {
        // 完整校验两个集合，避免同一 Principal 在一个命令中得到相反要求。
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        LinkedHashSet<Long> additions = uniquePrincipalIds(addPrincipalIds);
        LinkedHashSet<Long> removals = uniquePrincipalIds(removePrincipalIds);
        if (additions.stream().anyMatch(removals::contains)) {
            throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
        }
        LinkedHashSet<Long> requested = new LinkedHashSet<>(additions);
        requested.addAll(removals);
        if (requested.isEmpty()) {
            return Map.of();
        }

        // Repository 批量修改目标角色，并只返回实际发生变化的 Principal。
        Role role = loadRoleById(checkedScope, Objects.requireNonNull(roleId, "角色标识不能为空"));
        RbacRepository repository = repository(checkedScope);
        LinkedHashSet<Long> changed = uniquePrincipalIds(
                repository.changeRoleAssignments(
                        checkedScope,
                        role.roleId(),
                        additions,
                        removals));
        if (!requested.containsAll(changed)) {
            throw new IllegalStateException("RbacRepository 返回了输入外的角色关系变化");
        }

        // 一次批量读取修改后结果，并只为实际变化者登记提交后的缓存和 Session 失效。
        Map<Long, PrincipalRoles> result = repository.findPrincipalRoles(checkedScope, requested);
        validatePrincipalRolesResult(checkedScope, requested, result);
        invalidatePermissions(checkedScope, changed);
        LOG.debug(
                "角色 Principal 批量关系已写入事务 | scope={} roleId={} requestedCount={} changedCount={}",
                checkedScope,
                role.roleId(),
                requested.size(),
                changed.size());
        return Map.copyOf(result);
    }

    /**
     * 批量读取同一角色空间中 Principal 通过启用角色得到的具体 Operation。
     *
     * @return 每个输入 Principal 对应的具体 Operation；无授权时对应空列表
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull Operation>> findGrantedOperations(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        Map<RbacScope, Map<Long, List<Operation>>> result = findGrantedOperations(
                Map.of(checkedScope, uniquePrincipalIds(principalIds)));
        return result.getOrDefault(checkedScope, Map.of());
    }

    /**
     * 筛选在同一角色空间中获得全部或任一指定 Operation 的 Principal。
     *
     * <p>该方法复用 {@link #findGrantedOperations(RbacScope, Collection)} 的缓存和按领域回源流程。查询中的
     * Operation 必须属于当前 RBAC 领域允许分配的目录。
     *
     * @param scope 角色空间
     * @param query 有界 Principal 标识和 Operation 匹配方式
     * @return 保持查询标识顺序的匹配子集；无授权的 Principal 被排除
     * @throws BusinessException 任一 Operation 不属于当前 RBAC 领域时抛出
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable Set<@NotNull Long> findPrincipalIds(
            @NotNull RbacScope scope,
            @NotNull PrincipalGrantQuery query) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        PrincipalGrantQuery checkedQuery = Objects.requireNonNull(query, "Principal 授权查询不能为空");
        PermissionSelections selections = permissionSelections.selection(checkedScope.domain());
        List<Operation> requiredOperations = selections.expand(
                selections.validateGrantableOperations(checkedQuery.operations()));

        Map<Long, List<Operation>> granted = findGrantedOperations(checkedScope, checkedQuery.principalIds());
        LinkedHashSet<Long> matched = new LinkedHashSet<>();
        for (Long principalId : checkedQuery.principalIds()) {
            List<Operation> principalOperations = granted.getOrDefault(principalId, List.of());
            boolean accepted = checkedQuery.matchesAll()
                    ? new HashSet<>(principalOperations).containsAll(requiredOperations)
                    : requiredOperations.stream().anyMatch(principalOperations::contains);
            if (accepted) {
                matched.add(principalId);
            }
        }
        LOG.debug(
                "Principal 授权条件查询完成 | scope={} requestedCount={} operationCount={} matchedCount={}",
                checkedScope,
                checkedQuery.principalIds().size(),
                requiredOperations.size(),
                matched.size());
        return Collections.unmodifiableSet(matched);
    }

    /**
     * 批量读取多个 RBAC 范围中 Principal 通过启用角色得到的具体 Operation。
     *
     * <p>缓存使用一次 {@code multiGet}；每个 RBAC 领域的全部缺失范围由对应 Repository 一次回源。
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull List<@NotNull Operation>>> findGrantedOperations(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
        LinkedHashMap<RbacScope, Collection<Long>> requested = normalizeRequests(principalIdsByScope);
        if (requested.isEmpty()) {
            return Map.of();
        }

        RbacPermissionCacheLookup cacheLookup = permissionCache.find(requested);
        Map<RbacScope, Map<Long, List<PermissionSelector>>> cached = cacheLookup.cachedSelectors();
        LinkedHashMap<RbacDomain, Map<RbacScope, Collection<Long>>> missingByDomain = new LinkedHashMap<>();
        for (Map.Entry<RbacScope, Collection<Long>> scopeEntry : requested.entrySet()) {
            Map<Long, List<PermissionSelector>> scopeCache = cached.getOrDefault(scopeEntry.getKey(), Map.of());
            List<Long> missing = scopeEntry.getValue()
                    .stream()
                    .filter(principalId -> !scopeCache.containsKey(principalId))
                    .toList();
            if (!missing.isEmpty()) {
                missingByDomain
                        .computeIfAbsent(scopeEntry.getKey().domain(), ignored -> new LinkedHashMap<>())
                        .put(scopeEntry.getKey(), missing);
            }
        }

        LinkedHashMap<RbacScope, Map<Long, List<PermissionSelector>>> loaded = new LinkedHashMap<>();
        for (Map.Entry<RbacDomain, Map<RbacScope, Collection<Long>>> domainEntry : missingByDomain.entrySet()) {
            Map<RbacScope, Map<Long, List<PermissionSelector>>> domainResult = repositories
                    .repository(domainEntry.getKey())
                    .findActivePermissionSelectors(domainEntry.getValue());
            validateSelectorResult(domainEntry.getValue(), domainResult);
            loaded.putAll(domainResult);
        }
        permissionCache.put(cacheLookup, loaded);

        LinkedHashMap<RbacScope, Map<Long, List<Operation>>> result = new LinkedHashMap<>();
        for (Map.Entry<RbacScope, Collection<Long>> scopeEntry : requested.entrySet()) {
            RbacScope scope = scopeEntry.getKey();
            PermissionSelections selections = permissionSelections.selection(scope.domain());
            LinkedHashMap<Long, List<Operation>> operations = new LinkedHashMap<>();
            for (Long principalId : scopeEntry.getValue()) {
                List<PermissionSelector> selectors = cached.getOrDefault(scope, Map.of()).get(principalId);
                if (selectors == null) {
                    selectors = Objects.requireNonNull(
                            loaded.getOrDefault(scope, Map.of()).get(principalId),
                            "RbacRepository 必须返回每个缓存缺失的 Principal");
                }
                operations.put(principalId, selections.expand(selectors));
            }
            result.put(scope, Map.copyOf(operations));
        }
        LOG.debug(
                "Principal 授权批量读取完成 | scopeCount={} principalCount={} cacheMissCount={}",
                requested.size(),
                requested.values().stream().mapToInt(Collection::size).sum(),
                missingByDomain.values()
                        .stream()
                        .flatMap(value -> value.values().stream())
                        .mapToInt(Collection::size)
                        .sum());
        return Map.copyOf(result);
    }

    /**
     * 创建尚不存在的内置角色，并校验已有角色的保护状态和可选固定权限。
     */
    @Transactional
    public void initializeBuiltInRole(
            @NotNull RbacScope scope,
            @NotNull String code,
            @NotNull Collection<@NotNull PermissionSelector> initialSelectors,
            boolean fixedPermissions) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacRepository repository = repository(checkedScope);
        String checkedCode = validateRoleCode(code);
        List<PermissionSelector> checkedSelectors = List.copyOf(
                Objects.requireNonNull(initialSelectors, "初始选择符不能为空"));
        int permissionCount = permissionSelections
                .selection(checkedScope.domain())
                .expand(checkedSelectors)
                .size();
        Role role = repository.findRole(checkedScope, checkedCode).orElseGet(() -> {
            Role created = repository.createRole(
                    checkedScope,
                    idGenerator.nextId().value(),
                    checkedCode,
                    RoleStatus.ACTIVE,
                    true,
                    checkedSelectors);
            LOG.debug(
                    "内置角色创建已写入事务 | scope={} roleId={} permissionCount={}",
                    checkedScope,
                    created.roleId(),
                    permissionCount);
            return created;
        });
        if (!role.builtIn() || role.status() != RoleStatus.ACTIVE) {
            throw new IllegalStateException("内置角色数据不符合要求: " + checkedCode);
        }
        if (fixedPermissions
                && !repository.findPermissionSelectors(checkedScope, role.roleId()).equals(checkedSelectors)) {
            throw new IllegalStateException("内置角色权限数据不符合要求: " + checkedCode);
        }
    }

    /**
     * 按角色编码为 Principal 建立关系。
     */
    @Transactional
    public void bindRole(
            @NotNull RbacScope scope,
            @NotNull Long principalId,
            @NotNull String roleCode,
            boolean protectedRelation) {
        RbacScope checkedScope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacRepository repository = repository(checkedScope);
        Role role = repository.findRole(checkedScope, Objects.requireNonNull(roleCode, "角色编码不能为空"))
                .orElseThrow(() -> new BusinessException(RbacErrorCode.ROLE_NOT_FOUND));
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "Principal 标识不能为空");
        repository.bindRole(checkedScope, checkedPrincipalId, role.roleId(), protectedRelation);
        invalidatePermissions(checkedScope, List.of(checkedPrincipalId));
        LOG.debug(
                "Principal 角色绑定已写入事务 | scope={} principalId={} roleId={} protected={}",
                checkedScope,
                checkedPrincipalId,
                role.roleId(),
                protectedRelation);
    }

    /**
     * 在当前事务提交后精确清除指定 Principal 的授权缓存和可选 Session。
     */
    public void invalidatePermissions(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds) {
        invalidateAfterCommit(
                Objects.requireNonNull(scope, "RBAC 范围不能为空"),
                uniquePrincipalIds(principalIds));
    }

    /**
     * 查询角色使用者，并在事务提交后清除其授权缓存和可选 Session。
     */
    private void invalidateRolePrincipals(
            @NotNull RbacRepository repository,
            @NotNull RbacScope scope,
            @NotNull Long roleId) {
        List<Long> principalIds = repository.findRolePrincipalIds(scope, roleId);
        LOG.trace(
                "角色授权失效范围解析完成 | scope={} roleId={} principalCount={}",
                scope,
                roleId,
                principalIds.size());
        invalidatePermissions(scope, principalIds);
    }

    /**
     * 注册事务提交后的批量精确缓存删除和可选 Session 失效。
     */
    private void invalidateAfterCommit(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds) {
        List<Long> checkedIds = List.copyOf(new LinkedHashSet<>(principalIds));
        if (checkedIds.isEmpty()) {
            return;
        }
        Runnable action = () -> {
            permissionCache.evict(Map.of(scope, checkedIds));
            invalidations.invalidate(scope, checkedIds);
        };
        TransactionCallbacks.afterCommitOrNow(action);
    }

    /**
     * @return 与指定 RBAC 范围领域匹配的 Repository
     */
    private @NotNull RbacRepository repository(@NotNull RbacScope scope) {
        return repositories.repository(scope.domain());
    }

    /**
     * 校验并复制多个 RBAC 范围及其不重复 Principal 标识。
     */
    private @NotNull LinkedHashMap<RbacScope, Collection<Long>> normalizeRequests(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
        LinkedHashMap<RbacScope, Collection<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<RbacScope, ? extends Collection<Long>> entry : Objects
                .requireNonNull(principalIdsByScope, "RBAC 批量授权请求不能为空")
                .entrySet()) {
            RbacScope scope = Objects.requireNonNull(entry.getKey(), "RBAC 范围不能为空");
            LinkedHashSet<Long> ids = uniquePrincipalIds(entry.getValue());
            if (!ids.isEmpty()) {
                result.put(scope, ids);
            }
        }
        return result;
    }

    /**
     * 校验 Repository 为每个请求坐标返回且只返回一个选择符列表。
     */
    private static void validateSelectorResult(
            @NotNull Map<RbacScope, Collection<Long>> requested,
            @NotNull Map<RbacScope, Map<Long, List<PermissionSelector>>> result) {
        if (!result.keySet().equals(requested.keySet())) {
            throw new IllegalStateException("RbacRepository 返回了错误的 RBAC 范围");
        }
        for (Map.Entry<RbacScope, Collection<Long>> entry : requested.entrySet()) {
            Map<Long, List<PermissionSelector>> principals = Objects.requireNonNull(
                    result.get(entry.getKey()),
                    "RbacRepository 返回的 RBAC 范围结果不能为空");
            if (!principals.keySet().equals(new LinkedHashSet<>(entry.getValue()))) {
                throw new IllegalStateException("RbacRepository 没有完整返回输入 Principal");
            }
            for (List<PermissionSelector> selectors : principals.values()) {
                Objects.requireNonNull(selectors, "权限选择符列表不能为空")
                        .forEach(selector -> Objects.requireNonNull(selector, "权限选择符不能为空"));
            }
        }
    }

    /**
     * @return 保持输入顺序且不包含空值或重复值的 Principal 标识
     */
    private static @NotNull LinkedHashSet<Long> uniquePrincipalIds(
            @NotNull Collection<@NotNull Long> principalIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long principalId : Objects.requireNonNull(principalIds, "Principal 标识集合不能为空")) {
            Long checked = Objects.requireNonNull(principalId, "Principal 标识不能为空");
            if (!unique.add(checked)) {
                throw new IllegalArgumentException("Principal 标识不能重复");
            }
        }
        return unique;
    }

    /**
     * @return 保持输入顺序且不包含空值或重复值的角色标识
     */
    private static @NotNull LinkedHashSet<Long> uniqueRoleIds(
            @NotNull Collection<@NotNull Long> roleIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long roleId : Objects.requireNonNull(roleIds, "角色标识集合不能为空")) {
            Long checked = Objects.requireNonNull(roleId, "角色标识不能为空");
            if (!unique.add(checked)) {
                throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
            }
        }
        return unique;
    }

    /**
     * 校验 Repository 为全部请求 Principal 返回同一范围的角色结果。
     */
    private static void validatePrincipalRolesResult(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds,
            @NotNull Map<@NotNull Long, @NotNull PrincipalRoles> result) {
        if (!result.keySet().equals(new LinkedHashSet<>(principalIds))
                || result.values().stream().anyMatch(value -> !scope.equals(value.scope()))) {
            throw new IllegalStateException("RbacRepository 没有完整返回指定范围内的 PrincipalRoles");
        }
    }

    /**
     * @return 去除首尾空白且长度符合要求的角色编码
     */
    @CheckReturnValue
    private static @NotNull String validateRoleCode(@NotNull String value) {
        final String checked;
        try {
            checked = TextUtils.requireText(value, "角色编码").trim();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(RbacErrorCode.ROLE_INVALID, exception);
        }
        if (checked.length() > MAX_ROLE_CODE_LENGTH) {
            throw new BusinessException(
                    RbacErrorCode.ROLE_INVALID,
                    new IllegalArgumentException("角色编码超过最大长度"));
        }
        return checked;
    }
}
