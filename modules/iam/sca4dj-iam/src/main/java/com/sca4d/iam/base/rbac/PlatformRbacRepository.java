package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.base.mapper.PlatformPrincipalPermission;
import com.sca4d.iam.base.mapper.PlatformPrincipalRole;
import com.sca4d.iam.base.mapper.PlatformRbacMapper;
import com.sca4d.iam.base.mapper.PlatformRoleState;
import com.sca4d.iam.identity.PrincipalType;
import com.sca4d.iam.rbac.PermissionSelector;
import com.sca4d.iam.rbac.PrincipalRoles;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacErrorCode;
import com.sca4d.iam.rbac.RbacRepository;
import com.sca4d.iam.rbac.RbacScope;
import com.sca4d.iam.rbac.Role;
import com.sca4d.iam.rbac.RoleStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.dao.DuplicateKeyException;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 使用 {@link PlatformRbacMapper} 读写 Platform 角色和 PlatformPrincipal 角色关系。 */
public class PlatformRbacRepository implements RbacRepository {
    /** Platform 角色 Repository 的固定领域。 */
    public static final @NotNull RbacDomain PLATFORM_DOMAIN = new RbacDomain("PLATFORM");

    /** Platform 角色使用的全局 RBAC 范围。 */
    public static final @NotNull RbacScope PLATFORM = RbacScope.global(PLATFORM_DOMAIN);

    private final @NotNull PlatformRbacMapper mapper;

    private final @NotNull LocaleCatalog locales;

    private final @NotNull PrincipalType principalType;

    private final @NotNull SqlBatches sqlBatches;

    private final @NotNull VersionConditions versionConditions;

    /**
     * 创建 Platform RBAC Repository。
     *
     * @param mapper 执行 Platform RBAC SQL 的 Mapper
     * @param versionConditions 根资源写入版本条件
     * @param locales 应用支持和默认 Locale
     * @param principalType PlatformPrincipal 持久化类型
     * @param sqlBatches SQL 批处理器
     */
    public PlatformRbacRepository(
            @NotNull PlatformRbacMapper mapper,
            @NotNull VersionConditions versionConditions,
            @NotNull LocaleCatalog locales,
            @NotNull PrincipalType principalType,
            @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "Platform RBAC Mapper 不能为空");
        this.versionConditions = Objects.requireNonNull(versionConditions, "版本条件不能为空");
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
        this.principalType = Objects.requireNonNull(principalType, "PlatformPrincipal 类型不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull RbacDomain domain() {
        return PLATFORM_DOMAIN;
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Role> findRoles(@NotNull RbacScope scope) {
        validatePlatformScope(scope);
        return mapper.selectPlatformRoles(null, null, false)
                .stream()
                .map(PlatformRbacRepository::toRole)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Optional<Role> findRole(@NotNull RbacScope scope, @NotNull Long roleId) {
        validatePlatformScope(scope);
        return mapper.selectPlatformRoles(List.of(Objects.requireNonNull(roleId, "角色标识不能为空")), null, false)
                .stream()
                .findFirst()
                .map(PlatformRbacRepository::toRole);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Optional<Role> findRole(@NotNull RbacScope scope, @NotNull String code) {
        validatePlatformScope(scope);
        return mapper.selectPlatformRoles(null, Objects.requireNonNull(code, "角色编码不能为空"), false)
                .stream()
                .findFirst()
                .map(PlatformRbacRepository::toRole);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Role> findRoles(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> roleIds) {
        validatePlatformScope(scope);
        Collection<@NotNull Long> checked = Objects.requireNonNull(roleIds, "角色标识集合不能为空");
        if (checked.isEmpty()) {
            return List.of();
        }
        List<Role> result = sqlBatches.query(
                checked,
                1,
                0,
                batch -> mapper.selectPlatformRoles(batch, null, false))
                .stream()
                .map(PlatformRbacRepository::toRole)
                .collect(Collectors.toCollection(ArrayList::new));
        result.sort(Comparator.comparing(Role::code));
        return List.copyOf(result);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Role createRole(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull String code,
            @NotNull RoleStatus status,
            boolean builtIn,
            @NotNull Collection<@NotNull PermissionSelector> permissionSelectors) {
        validatePlatformScope(scope);
        String checkedCode = Objects.requireNonNull(code, "角色编码不能为空");
        LocalizedFields i18n = LocalizedFields.of(
                Map.of(
                        locales.defaultLocale(),
                        Map.of("name", checkedCode)));
        return createRole(
                roleId,
                checkedCode,
                i18n,
                status,
                builtIn,
                permissionSelectors)
                .role();
    }

    /**
     * 原子保存带完整多语言字段的 Platform 角色及初始 Operation 选择符。
     *
     * @param roleId 新角色标识
     * @param code 唯一且创建后不可修改的角色编码
     * @param i18n 已按 Platform 角色 schema 校验的完整多语言字段
     * @param status 初始状态
     * @param builtIn 是否为内置角色
     * @param permissionSelectors 初始具体 ACTION 编码或内部全量选择符
     * @return 写入后的 Platform 角色详情
     */
    @CheckReturnValue
    public @NotNull PlatformRoleDetails createRole(
            @NotNull Long roleId,
            @NotNull String code,
            @NotNull LocalizedFields i18n,
            @NotNull RoleStatus status,
            boolean builtIn,
            @NotNull Collection<@NotNull PermissionSelector> permissionSelectors) {
        Collection<@NotNull PermissionSelector> selectors = Objects
                .requireNonNull(permissionSelectors, "Operation 选择符不能为空");
        try {
            Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
            AffectedRowCounts.requireSingle(
                    mapper.insertPlatformRole(
                            checkedRoleId,
                            Objects.requireNonNull(code, "角色编码不能为空"),
                            Objects.requireNonNull(i18n, "角色多语言字段不能为空"),
                            Objects.requireNonNull(status, "角色状态不能为空"),
                            builtIn),
                    ZeroRowPolicy.ERROR,
                    "新增 Platform 角色");
            List<String> selectedValues = selectorValues(selectors);
            sqlBatches.forEach(selectedValues, 2, 0, batch -> {
                AffectedRowCounts.requireExactly(
                        mapper.insertPlatformPermissionSelectors(checkedRoleId, batch),
                        batch.size(),
                        "新增 Platform 角色 Operation 选择符");
            });
            return loadRoleDetailsById(checkedRoleId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(RbacErrorCode.ROLE_CODE_EXISTS, exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void bindRole(
            @NotNull RbacScope scope,
            @NotNull Long principalId,
            @NotNull Long roleId,
            boolean protectedRelation) {
        validatePlatformScope(scope);
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        // 与删除角色及批量分配使用相同锁序，防止检查后角色被删除。
        PlatformRoleState checkedRole = mapper.selectPlatformRoles(List.of(Objects.requireNonNull(roleId)), null, true)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(RbacErrorCode.ROLE_NOT_FOUND));
        Version current = Objects.requireNonNull(
                mapper.selectPlatformPrincipalRolesVersion(checkedPrincipalId),
                "PlatformPrincipal 缺少角色集合版本");
        AffectedRowCounts.requireSingle(
                mapper.updatePlatformPrincipalRolesVersion(checkedPrincipalId, current),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "PlatformPrincipal 角色");
        AffectedRowCounts.requireSingle(
                mapper.upsertPlatformPrincipalRole(checkedPrincipalId, checkedRole.id(), protectedRelation),
                ZeroRowPolicy.ERROR,
                "绑定 PlatformPrincipal 角色");
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Role updateRoleStatus(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull RoleStatus status) {
        validatePlatformScope(scope);
        Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
        VersionCondition condition = versionConditions.resolve(loadRoleStateById(checkedRoleId).version());
        AffectedRowCounts.requireSingle(
                mapper.updatePlatformRole(
                        checkedRoleId,
                        Patch.set(Objects.requireNonNull(status, "角色状态不能为空")),
                        Patch.ignore(),
                        condition.expected()),
                condition.whenZero(),
                "Platform 角色");
        PlatformRoleState updated = loadRoleStateById(checkedRoleId);
        return toRole(updated);
    }

    /**
     * 原子替换 Platform 角色完整多语言字段并增加统一版本。
     *
     * @param roleId 角色标识
     * @param i18n 已按 Platform 角色 schema 校验的完整多语言字段
     * @return 修改后的 Platform 角色详情
     */
    @CheckReturnValue
    public @NotNull PlatformRoleDetails replaceI18n(
            @NotNull Long roleId,
            @NotNull LocalizedFields i18n) {
        Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
        VersionCondition condition = versionConditions.resolve(loadRoleStateById(checkedRoleId).version());
        AffectedRowCounts.requireSingle(
                mapper.updatePlatformRole(
                        checkedRoleId,
                        Patch.ignore(),
                        Patch.set(Objects.requireNonNull(i18n, "角色多语言字段不能为空")),
                        condition.expected()),
                condition.whenZero(),
                "Platform 角色");
        return loadRoleDetailsById(checkedRoleId);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteRole(@NotNull RbacScope scope, @NotNull Long roleId) {
        validatePlatformScope(scope);
        Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
        PlatformRoleState current = mapper.selectPlatformRoles(List.of(checkedRoleId), null, true)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(RbacErrorCode.ROLE_NOT_FOUND));
        VersionCondition condition = versionConditions.resolve(current.version());
        if (!mapper.selectPlatformRolePrincipalIds(checkedRoleId).isEmpty()) {
            throw new BusinessException(RbacErrorCode.ROLE_IN_USE);
        }
        mapper.deletePlatformPermissionSelectors(checkedRoleId);
        AffectedRowCounts.requireSingle(
                mapper.deletePlatformRole(checkedRoleId, condition.expected()),
                condition.whenZero(),
                "Platform 角色");
    }

    /** 物理删除 PlatformPrincipal 的全部角色关系，包括受保护关系。 */
    public void deleteAllPrincipalRoles(@NotNull Long principalId) {
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        if (mapper.lockPlatformPrincipals(List.of(checkedPrincipalId), principalType).isEmpty()) {
            throw new IllegalStateException("PlatformPrincipal 不存在或已删除");
        }
        mapper.deletePlatformPrincipalRoles(checkedPrincipalId, null);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PermissionSelector> findPermissionSelectors(
            @NotNull RbacScope scope,
            @NotNull Long roleId) {
        validatePlatformScope(scope);
        Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
        loadRoleStateById(checkedRoleId);
        return mapper.selectPlatformPermissionSelectors(checkedRoleId)
                .stream()
                .map(PermissionSelector::new)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Role replacePermissionSelectors(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull Collection<@NotNull PermissionSelector> permissionSelectors) {
        validatePlatformScope(scope);
        Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
        PlatformRoleState current = loadRoleStateById(checkedRoleId);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.updatePlatformRoleVersion(checkedRoleId, condition.expected()),
                condition.whenZero(),
                "Platform 角色");
        mapper.deletePlatformPermissionSelectors(checkedRoleId);
        Collection<@NotNull PermissionSelector> selectors = Objects
                .requireNonNull(permissionSelectors, "Operation 选择符不能为空");
        List<String> selectedValues = selectorValues(selectors);
        sqlBatches.forEach(selectedValues, 2, 0, batch -> {
            AffectedRowCounts.requireExactly(
                    mapper.insertPlatformPermissionSelectors(checkedRoleId, batch),
                    batch.size(),
                    "新增 Platform 角色 Operation 选择符");
        });
        PlatformRoleState updated = loadRoleStateById(checkedRoleId);
        return toRole(updated);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull PrincipalRoles> findPrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds) {
        validatePlatformScope(scope);
        LinkedHashSet<Long> unique = new LinkedHashSet<>(
                Objects.requireNonNull(principalIds, "PlatformPrincipal 标识集合不能为空"));
        if (unique.isEmpty()) {
            return Map.of();
        }
        List<PlatformPrincipalRole> principalRoles = sqlBatches.query(
                unique,
                1,
                0,
                mapper::selectPlatformPrincipalRoles);
        LinkedHashSet<Long> returnedPrincipalIds = principalRoles.stream()
                .map(PlatformPrincipalRole::principalId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!returnedPrincipalIds.equals(unique)) {
            throw new IllegalStateException("PlatformPrincipal 不存在或已删除");
        }
        LinkedHashSet<Long> roleIds = principalRoles.stream()
                .map(PlatformPrincipalRole::roleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Role> roles = findRoles(PLATFORM, roleIds)
                .stream()
                .collect(Collectors.toUnmodifiableMap(Role::roleId, role -> role));
        if (!roles.keySet().equals(roleIds)) {
            throw new IllegalStateException("PlatformPrincipal 角色关系引用了不存在的角色");
        }

        LinkedHashMap<Long, List<PlatformPrincipalRole>> rolesByPrincipal = new LinkedHashMap<>();
        unique.forEach(principalId -> rolesByPrincipal.put(principalId, new ArrayList<>()));
        for (PlatformPrincipalRole principalRole : principalRoles) {
            List<PlatformPrincipalRole> rolesForPrincipal = rolesByPrincipal.get(principalRole.principalId());
            if (rolesForPrincipal == null) {
                throw new IllegalStateException("PlatformRbacMapper 返回了输入外的 PlatformPrincipal");
            }
            rolesForPrincipal.add(principalRole);
        }

        LinkedHashMap<Long, PrincipalRoles> result = new LinkedHashMap<>();
        unique.forEach(principalId -> {
            List<PlatformPrincipalRole> roleRelations = rolesByPrincipal.get(principalId);
            Version rolesVersion = roleRelations.getFirst().rolesVersion();
            if (roleRelations.stream().anyMatch(value -> !rolesVersion.equals(value.rolesVersion()))) {
                throw new IllegalStateException("PlatformPrincipal 角色关系版本不一致");
            }
            List<Role> rolesForPrincipal = roleRelations.stream()
                    .map(PlatformPrincipalRole::roleId)
                    .filter(Objects::nonNull)
                    .map(roles::get)
                    .toList();
            Set<Long> protectedRoleIds = roleRelations.stream()
                    .filter(value -> value.roleId() != null && value.protectedRelation())
                    .map(PlatformPrincipalRole::roleId)
                    .collect(Collectors.toUnmodifiableSet());
            result.put(
                    principalId,
                    new PrincipalRoles(PLATFORM, principalId, rolesForPrincipal, protectedRoleIds, rolesVersion));
        });
        return Map.copyOf(result);
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull PrincipalRoles replacePrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Long principalId,
            @NotNull Collection<@NotNull Long> roleIds) {
        validatePlatformScope(scope);
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        List<Long> requestedRoles = new LinkedHashSet<>(Objects.requireNonNull(roleIds, "角色标识集合不能为空"))
                .stream()
                .sorted()
                .toList();
        int lockedRoleCount = sqlBatches.query(
                requestedRoles,
                1,
                0,
                batch -> mapper.selectPlatformRoles(batch, null, true))
                .size();
        if (lockedRoleCount != requestedRoles.size()) {
            throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
        }
        Version currentVersion = Objects.requireNonNull(
                mapper.selectPlatformPrincipalRolesVersion(checkedPrincipalId),
                "PlatformPrincipal 缺少角色集合版本");
        VersionCondition condition = versionConditions.resolve(currentVersion);
        AffectedRowCounts.requireSingle(
                mapper.updatePlatformPrincipalRolesVersion(checkedPrincipalId, condition.expected()),
                condition.whenZero(),
                "PlatformPrincipal 角色");
        PrincipalRoles current = findPrincipalRoles(PLATFORM, List.of(checkedPrincipalId)).get(checkedPrincipalId);
        mapper.deletePlatformPrincipalRoles(current.principalId(), false);
        LinkedHashSet<Long> mutableRoleIds = new LinkedHashSet<>(
                Objects.requireNonNull(roleIds, "角色标识集合不能为空"));
        mutableRoleIds.removeAll(current.protectedRoleIds());
        sqlBatches.forEach(
                mutableRoleIds,
                2,
                0,
                batch -> AffectedRowCounts.requireExactly(
                        mapper.insertPlatformPrincipalRoles(current.principalId(), batch),
                        batch.size(),
                        "新增 PlatformPrincipal 角色关系"));
        return findPrincipalRoles(PLATFORM, List.of(current.principalId())).get(current.principalId());
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Long> changeRoleAssignments(
            @NotNull RbacScope scope,
            @NotNull Long roleId,
            @NotNull Collection<@NotNull Long> addPrincipalIds,
            @NotNull Collection<@NotNull Long> removePrincipalIds) {
        validatePlatformScope(scope);
        Long checkedRoleId = Objects.requireNonNull(roleId, "角色标识不能为空");
        LinkedHashSet<Long> additions = new LinkedHashSet<>(
                Objects.requireNonNull(addPrincipalIds, "添加 Principal 标识不能为空"));
        LinkedHashSet<Long> removals = new LinkedHashSet<>(
                Objects.requireNonNull(removePrincipalIds, "移除 Principal 标识不能为空"));
        if (additions.contains(null)
                || removals.contains(null)
                || additions.stream().anyMatch(removals::contains)) {
            throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
        }
        List<Long> requested = Stream.concat(additions.stream(), removals.stream())
                .distinct()
                .sorted()
                .toList();
        if (requested.isEmpty()) {
            return List.of();
        }

        // 先锁定 Role，阻止删除或状态修改与关系批量写入交叉进行。
        if (mapper.selectPlatformRoles(List.of(checkedRoleId), null, true).isEmpty()) {
            throw new BusinessException(RbacErrorCode.ROLE_NOT_FOUND);
        }

        // 按稳定顺序批量锁定 Principal，避免大批量关系修改之间发生数据库死锁。
        List<Long> locked = sqlBatches.query(
                requested,
                1,
                1,
                batch -> mapper.lockPlatformPrincipals(batch, principalType));
        if (!locked.equals(requested)) {
            throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
        }

        // 在写入前批量拒绝受保护的移除关系，确保整批请求不留下部分结果。
        List<Long> orderedRemovals = removals.stream().sorted().toList();
        sqlBatches.forEach(orderedRemovals, 1, 1, batch -> {
            if (!mapper.selectProtectedPlatformRolePrincipalIds(checkedRoleId, batch).isEmpty()) {
                throw new BusinessException(RbacErrorCode.BUILT_IN_ROLE_PROTECTED);
            }
        });

        // 增加和移除都使用批量 DML 的 RETURNING 结果，只为实际变化的 Principal 递增版本。
        LinkedHashSet<Long> changed = new LinkedHashSet<>();
        List<Long> orderedAdditions = additions.stream().sorted().toList();
        changed.addAll(
                sqlBatches.query(
                        orderedAdditions,
                        2,
                        0,
                        batch -> mapper.insertPlatformRoleAssignments(checkedRoleId, batch)));
        changed.addAll(
                sqlBatches.query(
                        orderedRemovals,
                        1,
                        1,
                        batch -> mapper.deletePlatformRoleAssignments(checkedRoleId, batch)));
        List<Long> changedIds = changed.stream().sorted().toList();
        int versionCount = sqlBatches.query(
                changedIds,
                1,
                0,
                batch -> List.of(mapper.incrementPlatformPrincipalRolesVersions(batch)))
                .stream()
                .mapToInt(Integer::intValue)
                .sum();
        AffectedRowCounts.requireExactly(
                versionCount,
                changedIds.size(),
                "PlatformPrincipal 角色集合版本");
        return changedIds;
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Long> findRolePrincipalIds(
            @NotNull RbacScope scope,
            @NotNull Long roleId) {
        validatePlatformScope(scope);
        return List.copyOf(
                mapper.selectPlatformRolePrincipalIds(
                        Objects.requireNonNull(roleId, "角色标识不能为空")));
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull List<@NotNull PermissionSelector>>> findActivePermissionSelectors(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
        if (principalIdsByScope.size() != 1 || !principalIdsByScope.containsKey(PLATFORM)) {
            throw new IllegalArgumentException("Platform RBAC Repository 只接受全局 Platform 范围");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(
                Objects.requireNonNull(
                        principalIdsByScope.get(PLATFORM),
                        "PlatformPrincipal 标识集合不能为空"));
        LinkedHashMap<Long, List<PermissionSelector>> result = new LinkedHashMap<>();
        unique.forEach(principalId -> result.put(principalId, new ArrayList<>()));
        for (PlatformPrincipalPermission operation : sqlBatches.query(
                unique,
                1,
                0,
                mapper::selectPlatformActiveOperations)) {
            List<PermissionSelector> selectors = result.get(operation.principalId());
            if (selectors == null) {
                throw new IllegalStateException("PlatformRbacMapper 返回了输入外的 PlatformPrincipal");
            }
            selectors.add(new PermissionSelector(operation.permissionSelector()));
        }
        result.replaceAll((principalId, selectors) -> List.copyOf(selectors));
        return Map.of(PLATFORM, Map.copyOf(result));
    }

    /**
     * 将类型安全选择符转换成 Mapper 批量参数。
     *
     * @param selectors Operation 选择符
     * @return 保持输入顺序的不可修改值列表
     */
    @CheckReturnValue
    private static @NotNull @Unmodifiable List<@NotNull String> selectorValues(
            @NotNull Collection<@NotNull PermissionSelector> selectors) {
        return selectors.stream()
                .map(selector -> Objects.requireNonNull(selector, "Operation 选择符不能为空").value())
                .toList();
    }

    /**
     * 把 Platform 角色查询结果转换为通用角色。
     *
     * @return 使用 Platform Repository 编码的角色
     */
    @CheckReturnValue
    private static @NotNull Role toRole(@NotNull PlatformRoleState state) {
        return new Role(
                state.id(),
                PLATFORM,
                state.code(),
                state.status(),
                state.builtIn(),
                state.createdAt(),
                state.updatedAt(),
                state.version());
    }

    /**
     * 读取全部 Platform 角色管理详情。
     *
     * @return 按角色编码排序的不可修改详情列表
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PlatformRoleDetails> findRoleDetails() {
        return mapper.selectPlatformRoles(null, null, false)
                .stream()
                .map(PlatformRbacRepository::toDetails)
                .toList();
    }

    /**
     * 读取必须存在的 Platform 角色管理详情。
     *
     * @param roleId 角色标识
     * @return 匹配详情
     */
    @CheckReturnValue
    public @NotNull PlatformRoleDetails loadRoleDetailsById(@NotNull Long roleId) {
        return toDetails(loadRoleStateById(roleId));
    }

    /** 把 Platform 角色查询结果转换为包含多语言字段的管理详情。 */
    @CheckReturnValue
    private static @NotNull PlatformRoleDetails toDetails(@NotNull PlatformRoleState state) {
        PlatformRoleState checked = Objects.requireNonNull(state, "Platform 角色状态不能为空");
        return new PlatformRoleDetails(toRole(checked), checked.i18n());
    }

    /** 读取必须存在的 Platform 角色持久化状态。 */
    private @NotNull PlatformRoleState loadRoleStateById(@NotNull Long roleId) {
        PlatformRoleState state = mapper.selectPlatformRoles(List.of(roleId), null, false)
                .stream()
                .findFirst()
                .orElse(null);
        if (state == null) {
            throw new BusinessException(RbacErrorCode.ROLE_NOT_FOUND);
        }
        return state;
    }

    /** 拒绝把其他领域或局部范围传给 Platform 角色表。 */
    private static void validatePlatformScope(@NotNull RbacScope scope) {
        if (!PLATFORM.equals(Objects.requireNonNull(scope, "RBAC 范围不能为空"))) {
            throw new IllegalArgumentException("Platform RBAC Repository 只接受全局 Platform 范围");
        }
    }

}
