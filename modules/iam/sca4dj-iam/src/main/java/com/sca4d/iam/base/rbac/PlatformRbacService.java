package com.sca4d.iam.base.rbac;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.LocalizedFieldsSchema;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iam.base.PlatformAccountDirectoryRepository;
import com.sca4d.iam.base.PlatformAccountEntry;
import com.sca4d.iam.base.PlatformAccountQuery;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.rbac.PermissionSelections;
import com.sca4d.iam.rbac.PermissionSelector;
import com.sca4d.iam.rbac.PrincipalGrantQuery;
import com.sca4d.iam.rbac.PrincipalRoles;
import com.sca4d.iam.rbac.RbacErrorCode;
import com.sca4d.iam.rbac.RbacService;
import com.sca4d.iam.rbac.Role;
import com.sca4d.iam.rbac.RolePermissions;
import com.sca4d.iam.rbac.RoleStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 使用固定 Platform Repository 读写角色、Operation 和 PlatformPrincipal 角色关系。
 */
public class PlatformRbacService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformRbacService.class);

    private static final int MAX_ROLE_CODE_LENGTH = 128;

    private static final LockKey PLATFORM_INITIALIZATION_LOCK = new LockKey(TreeCode.of("IAM:RBAC:INITIALIZATION"));

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private static final LocalizedFieldsSchema ROLE_I18N_SCHEMA = new LocalizedFieldsSchema(
            Set.of("name"),
            Set.of("name"),
            Map.of("name", 128));

    /**
     * 拥有 Operation Catalog 中全部 ACTION 的内置角色编码。
     */
    public static final @NotNull String ROOT = "ROOT";

    /**
     * 新建 PlatformPrincipal 自动绑定的内置角色编码。
     */
    public static final @NotNull String DEFAULT = "DEFAULT";

    private final @NotNull RbacService rbac;

    private final @NotNull PlatformRbacRepository repository;

    private final @NotNull PermissionSelections permissionSelections;

    private final @NotNull IdGenerator idGenerator;

    private final @NotNull LocaleCatalog locales;

    private final @NotNull IdentityService identities;

    private final @NotNull PlatformAccountDirectoryRepository accounts;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /**
     * 创建 Platform RBAC 服务。
     *
     * @param rbac                 通用 RBAC 服务
     * @param repository           Platform RBAC Repository
     * @param permissionSelections Operation Catalog 的校验和展开规则
     * @param idGenerator          角色标识生成器
     * @param locales              应用支持和默认 Locale
     * @param identities           PlatformPrincipal 身份管理服务
     * @param accounts             Platform Account 内部持久化边界
     * @param distributedLock      Platform RBAC 跨实例写入锁
     * @param transactions         Platform RBAC 事务模板
     */
    public PlatformRbacService(
            @NotNull RbacService rbac,
            @NotNull PlatformRbacRepository repository,
            @NotNull PermissionSelections permissionSelections,
            @NotNull IdGenerator idGenerator,
            @NotNull LocaleCatalog locales,
            @NotNull IdentityService identities,
            @NotNull PlatformAccountDirectoryRepository accounts,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions) {
        this.rbac = Objects.requireNonNull(rbac, "通用 RBAC 服务不能为空");
        this.repository = Objects.requireNonNull(repository, "Platform RBAC Repository 不能为空");
        this.permissionSelections = Objects.requireNonNull(permissionSelections, "Operation 选择规则不能为空");
        this.idGenerator = Objects.requireNonNull(idGenerator, "ID 生成器不能为空");
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
        this.identities = Objects.requireNonNull(identities, "身份服务不能为空");
        this.accounts = Objects.requireNonNull(accounts, "Platform Account Repository 不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = Objects.requireNonNull(transactions, "事务模板不能为空");
    }

    /**
     * 读取全部 Platform 角色。
     *
     * @return 按角色编码排序的不可修改列表
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable List<@NotNull PlatformRoleDetails> findRoles() {
        return repository.findRoleDetails();
    }

    /**
     * 按角色标识读取 Platform 角色。
     *
     * @param roleId 角色标识
     * @return 匹配角色
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull PlatformRoleDetails loadRoleById(@NotNull Long roleId) {
        return repository.loadRoleDetailsById(roleId);
    }

    /**
     * 创建自定义 Platform 角色并保存初始 ACTION。
     *
     * @param code           唯一且创建后不可修改的角色编码
     * @param i18n           角色完整多语言字段
     * @param operationCodes Operation Catalog 中声明的 ACTION 编码
     * @return 新建角色及具体 Operation
     */
    @CheckReturnValue
    public @NotNull PlatformRoleDetails createRole(
            @NotNull String code,
            @NotNull LocalizedFields i18n,
            @NotNull Collection<@NotNull TreeCode> operationCodes) {
        String checkedCode = validateRoleCode(code);
        LocalizedFields checkedI18n = validateI18n(i18n);
        List<PermissionSelector> selectors = permissionSelections
                .validateGrantableCodes(operationCodes);

        return write(roleCodeLock(checkedCode), () -> {
            PlatformRoleDetails created = repository.createRole(
                    idGenerator.nextId().value(),
                    checkedCode,
                    checkedI18n,
                    RoleStatus.ACTIVE,
                    false,
                    selectors);
            LOG.debug(
                    "Platform 角色创建已写入事务 | roleId={} operationCount={}",
                    created.role().roleId(),
                    selectors.size());
            return created;
        });
    }

    /**
     * 修改 Platform 角色状态。
     *
     * @param roleId 角色标识
     * @param status 新角色状态
     * @return 修改后的角色详情
     */
    @CheckReturnValue
    public @NotNull PlatformRoleDetails updateRoleStatus(
            @NotNull Long roleId,
            @NotNull RoleStatus status) {
        return write(roleLock(roleId), () -> {
            Role updated = rbac.updateRoleStatus(PlatformRbacRepository.PLATFORM, roleId, status);
            return repository.loadRoleDetailsById(updated.roleId());
        });
    }

    /**
     * 原子替换 Platform 角色的完整多语言字段。
     *
     * @param roleId 角色标识
     * @param i18n   仅包含 Platform 角色声明字段的完整多语言内容
     * @return 修改后的角色详情
     * @throws BusinessException 缺少默认 Locale 或字段不符合 schema 时抛出
     */
    @CheckReturnValue
    public @NotNull PlatformRoleDetails replaceRoleI18n(
            @NotNull Long roleId,
            @NotNull LocalizedFields i18n) {
        LocalizedFields checkedI18n = validateI18n(i18n);
        return write(roleLock(roleId), () -> {
            PlatformRoleDetails updated = repository.replaceI18n(roleId, checkedI18n);
            LOG.debug("Platform 角色多语言字段已写入事务 | roleId={}", updated.role().roleId());
            return updated;
        });
    }

    /**
     * 删除没有分配给 Principal 的自定义 Platform 角色。
     *
     * @param roleId 角色标识
     */
    public void deleteRole(@NotNull Long roleId) {
        write(roleLock(roleId), () -> rbac.deleteRole(PlatformRbacRepository.PLATFORM, roleId));
    }

    /**
     * 读取 Platform 角色当前得到的具体 Operation。
     *
     * @param roleId 角色标识
     * @return 角色及具体 Operation
     */
    @CheckReturnValue
    public @NotNull RolePermissions findRolePermissions(@NotNull Long roleId) {
        return rbac.findRolePermissions(PlatformRbacRepository.PLATFORM, roleId);
    }

    /**
     * 原子替换 Platform 角色的具体 ACTION。
     *
     * @param roleId         角色标识
     * @param operationCodes Operation Catalog 中声明的 ACTION 编码
     * @return 修改后的角色及具体 Operation
     */
    @CheckReturnValue
    public @NotNull RolePermissions replaceRolePermissions(
            @NotNull Long roleId,
            @NotNull Collection<@NotNull TreeCode> operationCodes) {
        return write(
                roleLock(roleId),
                () -> rbac.replaceRolePermissions(
                        PlatformRbacRepository.PLATFORM,
                        roleId,
                        operationCodes));
    }

    /**
     * 读取 PlatformPrincipal 当前角色。
     *
     * @param principalId PlatformPrincipal 标识
     * @return 当前角色和受保护关系
     */
    @CheckReturnValue
    public @NotNull PrincipalRoles findPrincipalRoles(@NotNull Long principalId) {
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        if (!checkedPrincipalId
                .equals(identities.activePrincipalById(checkedPrincipalId, PlatformPrincipal.class))) {
            throw new IllegalStateException("PlatformPrincipal 有效性检查返回了不同标识");
        }
        return rbac.findPrincipalRoles(PlatformRbacRepository.PLATFORM, principalId);
    }

    /**
     * 原子替换 PlatformPrincipal 的非保护角色关系。
     *
     * @param principalId PlatformPrincipal 标识
     * @param roleIds     新的完整角色标识集合
     * @return 替换后的角色关系
     */
    @CheckReturnValue
    public @NotNull PrincipalRoles replacePrincipalRoles(
            @NotNull Long principalId,
            @NotNull Collection<@NotNull Long> roleIds) {
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        if (!checkedPrincipalId
                .equals(identities.activePrincipalById(checkedPrincipalId, PlatformPrincipal.class))) {
            throw new IllegalStateException("PlatformPrincipal 有效性检查返回了不同标识");
        }
        return write(
                principalLock(checkedPrincipalId),
                () -> rbac.replacePrincipalRoles(
                        PlatformRbacRepository.PLATFORM,
                        checkedPrincipalId,
                        roleIds));
    }

    /**
     * 查询指定 Platform 角色已分配或未分配的一页 Account。
     *
     * @param roleId   Platform 角色标识
     * @param assigned 查询已分配 Account 时为 {@code true}
     * @param query    分页、用户名和状态筛选
     * @return 当前页 Account 和完整命中数量
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull Page<PlatformAccountEntry> findRoleAccounts(
            @NotNull Long roleId,
            boolean assigned,
            @NotNull PlatformAccountQuery query) {
        Long checkedRoleId = loadRoleById(roleId).role().roleId();
        Page<PlatformAccountEntry> result = accounts.findRoleAccounts(checkedRoleId, assigned, query);
        LOG.debug(
                "Platform 角色 Account 目录已读取 | roleId={} assigned={} resultCount={} total={}",
                checkedRoleId,
                assigned,
                result.getItems().size(),
                result.getTotal());
        return result;
    }

    /**
     * 批量增加或移除一个 Platform 角色的 Account，并保留每个 Account 的其他角色。
     *
     * <p>该方法一次批量读取 Account 到 PlatformPrincipal 的关系，再委托通用 RBAC 批量修改。
     * 任一 Account 无效、重复、关联冲突或同时出现在添加和移除集合时整批失败。
     *
     * @param roleId           Platform 角色标识
     * @param addAccountIds    需要增加目标角色的 Account 标识
     * @param removeAccountIds 需要移除目标角色的 Account 标识
     * @return 按 PlatformPrincipal 标识索引的修改后角色关系
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull PrincipalRoles> changeRoleAccounts(
            @NotNull Long roleId,
            @NotNull Collection<@NotNull Long> addAccountIds,
            @NotNull Collection<@NotNull Long> removeAccountIds) {
        LinkedHashSet<Long> additions = uniqueAccountIds(addAccountIds);
        LinkedHashSet<Long> removals = uniqueAccountIds(removeAccountIds);
        LinkedHashSet<Long> requested = new LinkedHashSet<>(additions);
        requested.addAll(removals);
        if (requested.isEmpty()) {
            return Map.of();
        }
        return Objects.requireNonNull(
                transactions.execute(status -> {
                    // 一次批量读取 Account，并确认每个 Account 唯一对应一个 PlatformPrincipal。
                    Map<Long, PlatformAccountEntry> entries = accounts.loadByIds(requested);
                    LinkedHashSet<Long> addPrincipalIds = principalIds(entries, additions);
                    LinkedHashSet<Long> removePrincipalIds = principalIds(entries, removals);

                    // 由通用 RBAC 统一校验交叉集合、锁定 Principal、批量写入并登记提交后失效。
                    return rbac.changeRoleAssignments(
                            PlatformRbacRepository.PLATFORM,
                            roleId,
                            addPrincipalIds,
                            removePrincipalIds);
                }),
                "Platform 角色 Account 批量修改结果不能为空");
    }

    /**
     * 删除 PlatformPrincipal 的全部角色关系，包括受保护关系。
     *
     * <p>该方法只用于 Principal 生命周期结束后的同步清理，不要求 Principal 仍处于启用状态。
     *
     * @param principalId PlatformPrincipal 标识
     */
    public void revokeAllRoles(@NotNull Long principalId) {
        write(principalLock(principalId), () -> repository.deleteAllPrincipalRoles(principalId));
    }

    /**
     * 批量读取 PlatformPrincipal 通过启用角色得到的具体 Operation。
     *
     * @param principalIds PlatformPrincipal 标识
     * @return 每个输入 Principal 对应的具体 Operation
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull Operation>> findGrantedOperations(
            @NotNull Collection<@NotNull Long> principalIds) {
        return rbac.findGrantedOperations(PlatformRbacRepository.PLATFORM, principalIds);
    }

    /**
     * 筛选通过 Platform 启用角色获得全部或任一指定 Operation 的 Principal。
     *
     * @param query 有界 PlatformPrincipal 标识和 Operation 匹配方式
     * @return 保持查询标识顺序的匹配子集
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable Set<@NotNull Long> findPrincipalIds(@NotNull PrincipalGrantQuery query) {
        return rbac.findPrincipalIds(PlatformRbacRepository.PLATFORM, query);
    }

    /**
     * 确保 {@link #ROOT} 和 {@link #DEFAULT} 存在。
     *
     * <p>{@code ROOT} 固定保存 {@code "*"}，{@code DEFAULT} 只在首次创建时使用空 Operation 集合。
     */
    public void initializeBuiltInRoles() {
        write(PLATFORM_INITIALIZATION_LOCK, () -> {
            initializeBuiltInRole(ROOT, List.of(PermissionSelections.ALL), true);
            initializeBuiltInRole(DEFAULT, List.of(), false);
        });
    }

    /**
     * 为 bootstrap PlatformPrincipal 绑定不能由管理 API 移除的 {@link #ROOT}。
     *
     * @param principalId bootstrap PlatformPrincipal 标识
     */
    public void bindBootstrapRoot(@NotNull Long principalId) {
        write(
                principalLock(principalId),
                () -> rbac.bindRole(PlatformRbacRepository.PLATFORM, principalId, ROOT, true));
    }

    /**
     * 显式为 PlatformPrincipal 绑定普通 DEFAULT 角色。
     *
     * @param principalId PlatformPrincipal 标识
     */
    @SuppressWarnings("unused")
    public void bindDefaultRole(@NotNull Long principalId) {
        write(
                principalLock(principalId),
                () -> rbac.bindRole(PlatformRbacRepository.PLATFORM, principalId, DEFAULT, false));
    }

    /**
     * 返回多个实例初始化 Platform 内置角色和 bootstrap 账号时共用的分布式锁键。
     */
    static @NotNull LockKey initializationLock() {
        return PLATFORM_INITIALIZATION_LOCK;
    }

    /**
     * 先取得指定 Platform RBAC 资源锁，再在新打开的事务中执行有返回值的写操作。
     */
    private <T> @NotNull T write(
            @NotNull LockKey lockKey,
            @NotNull Supplier<@NotNull T> operation) {
        return distributedLock.execute(
                Objects.requireNonNull(lockKey, "Platform RBAC 锁键不能为空"),
                LOCK_WAIT,
                () -> Objects.requireNonNull(
                        transactions.execute(status -> operation.get()),
                        "Platform RBAC 写操作不能为空"));
    }

    /**
     * 先取得指定 Platform RBAC 资源锁，再在新打开的事务中执行无返回值的写操作。
     */
    private void write(@NotNull LockKey lockKey, @NotNull Runnable operation) {
        distributedLock.execute(
                Objects.requireNonNull(lockKey, "Platform RBAC 锁键不能为空"),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> operation.run());
                    return null;
                });
    }

    /**
     * 返回同一角色编码创建请求共用的分布式锁键，不在 Redis 键中暴露原始编码。
     */
    private static @NotNull LockKey roleCodeLock(@NotNull String code) {
        return new LockKey(TreeCode.of("IAM:RBAC:ROLE_CODE"), DigestUtil.sha256Hex(code));
    }

    /**
     * 返回同一 Platform 角色全部修改操作共用的分布式锁键。
     */
    private static @NotNull LockKey roleLock(@NotNull Long roleId) {
        return new LockKey(TreeCode.of("IAM:RBAC:ROLE"), Objects.requireNonNull(roleId, "角色标识不能为空").longValue());
    }

    /**
     * 返回同一 PlatformPrincipal 角色关系写入共用的分布式锁键。
     */
    private static @NotNull LockKey principalLock(@NotNull Long principalId) {
        return new LockKey(
                TreeCode.of("IAM:RBAC:PRINCIPAL"),
                Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空").longValue());
    }

    /**
     * 校验并复制不能包含空值或重复值的 Account 标识。
     */
    private static @NotNull LinkedHashSet<Long> uniqueAccountIds(
            @NotNull Collection<@NotNull Long> accountIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long accountId : Objects.requireNonNull(accountIds, "Account 标识集合不能为空")) {
            Long checkedId = Objects.requireNonNull(accountId, "Account 标识不能为空");
            if (!unique.add(checkedId)) {
                throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
            }
        }
        return unique;
    }

    /**
     * 把 Account 标识按输入顺序转换成唯一 PlatformPrincipal 标识。
     */
    private static @NotNull LinkedHashSet<Long> principalIds(
            @NotNull Map<@NotNull Long, @NotNull PlatformAccountEntry> accounts,
            @NotNull Collection<@NotNull Long> accountIds) {
        LinkedHashMap<Long, Long> principalByAccount = new LinkedHashMap<>();
        for (Long accountId : accountIds) {
            PlatformAccountEntry account = Objects.requireNonNull(
                    accounts.get(accountId),
                    "批量 Account 查询必须返回请求标识");
            principalByAccount.put(accountId, account.principalId());
        }
        LinkedHashSet<Long> principalIds = new LinkedHashSet<>(principalByAccount.values());
        if (principalIds.size() != principalByAccount.size()) {
            throw new BusinessException(RbacErrorCode.PRINCIPAL_ROLE_INVALID);
        }
        return principalIds;
    }

    /**
     * 确保一个 Platform 内置角色存在，并检查其保护状态和固定 Operation。
     */
    private void initializeBuiltInRole(
            @NotNull String code,
            @NotNull List<@NotNull PermissionSelector> selectors,
            boolean fixedOperations) {
        // 已有内置角色保留原名称；缺失时使用默认 Locale 和角色编码建立初始名称。
        Role role = repository.findRole(PlatformRbacRepository.PLATFORM, code).orElseGet(() -> {
            LocalizedFields i18n = LocalizedFields.of(
                    Map.of(
                            locales.defaultLocale(),
                            Map.of("name", code)));
            PlatformRoleDetails created = repository.createRole(
                    idGenerator.nextId().value(),
                    code,
                    i18n,
                    RoleStatus.ACTIVE,
                    true,
                    selectors);
            LOG.debug(
                    "Platform 内置角色创建已写入事务 | roleId={} operationCount={}",
                    created.role().roleId(),
                    selectors.size());
            return created.role();
        });

        // 内置角色必须始终启用；ROOT 的固定全量 Operation 也必须保持不变。
        if (!role.builtIn() || role.status() != RoleStatus.ACTIVE) {
            throw new IllegalStateException("Platform 内置角色数据不符合要求: " + code);
        }
        if (fixedOperations
                && !repository.findPermissionSelectors(PlatformRbacRepository.PLATFORM, role.roleId())
                        .equals(selectors)) {
            throw new IllegalStateException("Platform 内置角色 Operation 数据不符合要求: " + code);
        }
    }

    /**
     * 校验 Platform 角色编码并转换为统一业务失败。
     */
    @CheckReturnValue
    private static @NotNull String validateRoleCode(@NotNull String code) {
        try {
            String checked = TextUtils.requireText(code, "角色编码").trim();
            if (checked.length() > MAX_ROLE_CODE_LENGTH) {
                throw new IllegalArgumentException("角色编码超过最大长度");
            }
            return checked;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(RbacErrorCode.ROLE_INVALID, exception);
        }
    }

    /**
     * 按应用默认 Locale 和 Platform 角色字段 schema 校验完整多语言内容。
     */
    @CheckReturnValue
    private @NotNull LocalizedFields validateI18n(@NotNull LocalizedFields i18n) {
        try {
            return ROLE_I18N_SCHEMA.validate(i18n, locales.defaultLocale());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(RbacErrorCode.ROLE_INVALID, exception);
        }
    }
}
