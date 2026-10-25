package com.sca4d.iam.identity;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.persistence.exception.AffectedRowsMismatchException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 管理 IAM 表中的身份与关系；Subject、Account 直接使用 Core 类型，Principal 由领域按标识构造。
 */
@SuppressWarnings("unused")
public class IdentityService {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);

    private final @NotNull IdentityRepository repository;

    private final @NotNull IdGenerator idGenerator;

    private final @NotNull PrincipalTypes principalTypes;

    private final @NotNull @Unmodifiable Map<@NotNull AccountIdentifierType, @NotNull AccountIdentifierNormalizer> normalizers;

    /** 创建身份服务并建立登录标识规范化器索引。 */
    @ApiStatus.Internal
    public IdentityService(
            @NotNull IdentityRepository repository,
            @NotNull IdGenerator idGenerator,
            @NotNull PrincipalTypes principalTypes,
            @NotNull List<@NotNull AccountIdentifierNormalizer> normalizers) {
        this.repository = Objects.requireNonNull(repository, "身份 Repository 不能为空");
        this.idGenerator = Objects.requireNonNull(idGenerator, "ID 生成器不能为空");
        this.principalTypes = Objects.requireNonNull(principalTypes, "Principal 类型不能为空");
        LinkedHashMap<AccountIdentifierType, AccountIdentifierNormalizer> values = new LinkedHashMap<>();
        // 标识类型是开放扩展点，但每种类型只能有一个规范化规则，否则登录结果会依赖 Bean 顺序。
        for (AccountIdentifierNormalizer normalizer : Objects.requireNonNull(normalizers, "登录标识规范化器不能为空")) {
            AccountIdentifierNormalizer checked = Objects.requireNonNull(normalizer, "登录标识规范化器不能为空");
            AccountIdentifierType type = Objects.requireNonNull(checked.type(), "登录标识规范化器类型不能为空");
            if (values.putIfAbsent(type, checked) != null) {
                throw new IllegalStateException("同一登录标识类型只能注册一个规范化器: " + type);
            }
        }
        this.normalizers = Map.copyOf(values);
        LOG.debug("登录标识规范化器已注册 | normalizerCount={}", this.normalizers.size());
    }

    /**
     * 创建一个启用的 Subject 事实。
     *
     * @param type      固定 Subject 类型
     * @return 新建的 Subject
     */
    @Transactional
    @CheckReturnValue
    public @NotNull Subject createSubject(@NotNull SubjectType type) {
        SubjectType checkedType = Objects.requireNonNull(type, "接入者类型不能为空");
        if (checkedType == SubjectType.SYSTEM) {
            throw new IllegalArgumentException("SYSTEM 由启动初始化登记，不能创建第二个系统 Subject");
        }
        Long id = idGenerator.nextId().value();
        repository.insertSubject(id, checkedType, IdentityStatus.ACTIVE);
        Subject subject = new Subject(id, checkedType);
        LOG.info("Subject 已创建 | subjectId={} type={}", id, checkedType);
        return subject;
    }

    /**
     * 按标识读取任意状态的 Subject。
     *
     * @param id        Subject 标识
     * @return 匹配 Subject；不存在时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<Subject> findSubject(@NotNull Long id) {
        return repository.findSubject(Objects.requireNonNull(id, "接入者标识不能为空"));
    }

    /**
     * 读取 Subject 当前状态。
     *
     * @param id Subject 标识
     * @return 当前状态；Subject 不存在时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<IdentityStatus> findSubjectStatus(@NotNull Long id) {
        return repository.findSubjectStatus(Objects.requireNonNull(id, "接入者标识不能为空"));
    }

    /**
     * 读取必须存在且启用的 Subject。
     *
     * @param id        Subject 标识
     * @return 启用的 Subject
     * @throws BusinessException Subject 不存在或未启用时抛出
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Subject activeSubjectById(@NotNull Long id) {
        return repository.findActiveSubject(Objects.requireNonNull(id, "接入者标识不能为空"))
                .orElseThrow(() -> new BusinessException(IamErrorCode.SUBJECT_UNAVAILABLE));
    }

    /**
     * 后写覆盖 Subject 状态。
     *
     * @param id        Subject 标识
     * @param status    新状态
     * @throws BusinessException 目标不存在时抛出
     */
    @Transactional
    public void changeSubjectStatus(
            @NotNull Long id,
            @NotNull IdentityStatus status) {
        if (id == Subject.SYSTEM.subjectId()) {
            throw new IllegalArgumentException("SYSTEM 状态不能通过普通身份 API 修改");
        }
        if (!repository.updateSubjectStatus(
                Objects.requireNonNull(id, "接入者标识不能为空"),
                Objects.requireNonNull(status, "接入者状态不能为空"))) {
            throw new BusinessException(IamErrorCode.SUBJECT_UNAVAILABLE);
        }
        LOG.info("Subject 状态已修改 | subjectId={} status={}", id, status.value());
    }

    /**
     * 为启用的 Subject 创建一个 Account。
     *
     * @param subjectId 所属 Subject 标识
     * @param realm     固定认证域
     * @return 新建的 Account
     * @throws BusinessException Subject 不存在或未启用时抛出
     */
    @Transactional
    @CheckReturnValue
    public @NotNull Account createAccount(@NotNull Long subjectId, @NotNull RealmKey realm) {
        return createAccount(subjectId, realm, IdentityStatus.ACTIVE);
    }

    /**
     * 为已存在的 Subject 创建一个具有明确初始状态的 Account。
     *
     * <p>创建启用 Account 时 Subject 必须启用；创建停用 Account 时 Subject 必须存在。
     *
     * @param subjectId 所属 Subject 标识
     * @param realm 固定认证域
     * @param initialStatus Account 写入数据库的初始状态
     * @return 新建的 Account
     * @throws BusinessException Subject 不存在，或创建启用 Account 时 Subject 已停用
     */
    @Transactional
    @CheckReturnValue
    public @NotNull Account createAccount(
            @NotNull Long subjectId,
            @NotNull RealmKey realm,
            @NotNull IdentityStatus initialStatus) {
        Long checkedSubjectId = Objects.requireNonNull(subjectId, "接入者标识不能为空");
        if (checkedSubjectId == Subject.SYSTEM.subjectId()) {
            throw new IllegalArgumentException("SYSTEM 自主执行不使用登录 Account");
        }
        RealmKey checkedRealm = Objects.requireNonNull(realm, "认证域不能为空");
        IdentityStatus checkedStatus = Objects.requireNonNull(initialStatus, "账号初始状态不能为空");
        if (checkedStatus == IdentityStatus.ACTIVE) {
            repository.findActiveSubject(checkedSubjectId)
                    .orElseThrow(() -> new BusinessException(IamErrorCode.SUBJECT_UNAVAILABLE));
        } else {
            repository.findSubject(checkedSubjectId)
                    .orElseThrow(() -> new BusinessException(IamErrorCode.SUBJECT_UNAVAILABLE));
        }
        Long id = idGenerator.nextId().value();
        repository.insertAccount(id, checkedSubjectId, checkedRealm, checkedStatus);
        Account account = new Account(id, checkedSubjectId, checkedRealm);
        LOG.info(
                "Account 已创建 | accountId={} subjectId={} realm={} status={}",
                id,
                checkedSubjectId,
                checkedRealm.value(),
                checkedStatus.value());
        return account;
    }

    /**
     * 按标识读取任意状态的 Account。
     *
     * @param id        Account 标识
     * @return 匹配 Account；不存在时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<Account> findAccount(@NotNull Long id) {
        return repository.findAccount(Objects.requireNonNull(id, "账号标识不能为空"));
    }

    /**
     * 读取 Account 当前状态。
     *
     * @param id Account 标识
     * @return 当前状态；Account 不存在时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<IdentityStatus> findAccountStatus(@NotNull Long id) {
        return repository.findAccountStatus(Objects.requireNonNull(id, "账号标识不能为空"));
    }

    /** @return 按标识索引的未删除 Subject，包含停用状态；缺失标识不包含在结果中 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Map<Long, Subject> findSubjects(@NotNull Collection<Long> subjectIds) {
        return repository.findSubjects(List.copyOf(Objects.requireNonNull(subjectIds, "Subject 标识集合不能为空")));
    }

    /**
     * 读取一个 Subject 的全部 Account。
     *
     * @param subjectId Subject 标识
     * @return 不可修改的 Account 列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Account> findAccounts(@NotNull Long subjectId) {
        return repository.findAccounts(Objects.requireNonNull(subjectId, "接入者标识不能为空"));
    }

    /** 按 Subject 标识批量读取 Account。 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull Account>> findAccounts(
            @NotNull Collection<@NotNull Long> subjectIds) {
        return repository.findAccounts(Objects.requireNonNull(subjectIds, "接入者标识集合不能为空"));
    }

    /**
     * 读取必须存在且启用的 Account。
     *
     * @param id        Account 标识
     * @return 启用的 Account
     * @throws BusinessException Account 不存在或未启用时抛出
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Account activeAccountById(@NotNull Long id) {
        return repository.findActiveAccount(Objects.requireNonNull(id, "账号标识不能为空"))
                .orElseThrow(() -> new BusinessException(IamErrorCode.ACCOUNT_UNAVAILABLE));
    }

    /**
     * 后写覆盖 Account 状态。
     *
     * @param id        Account 标识
     * @param status    新状态
     * @throws BusinessException 目标不存在时抛出
     */
    @Transactional
    public void changeAccountStatus(
            @NotNull Long id,
            @NotNull IdentityStatus status) {
        if (!repository.updateAccountStatus(
                Objects.requireNonNull(id, "账号标识不能为空"),
                Objects.requireNonNull(status, "账号状态不能为空"))) {
            throw new BusinessException(IamErrorCode.ACCOUNT_UNAVAILABLE);
        }
        LOG.info("Account 状态已修改 | accountId={} status={}", id, status.value());
    }

    /**
     * 创建已登记 Java 类型的 Principal。
     *
     * @param type      Principal 精确 Java 类型
     * @return 新建的 Principal 标识
     * @throws BusinessException Java 类型没有 Principal Codec 时抛出
     */
    @Transactional
    @CheckReturnValue
    public @NotNull Long createPrincipal(@NotNull Class<? extends Principal> type) {
        if (type == SystemPrincipal.class) {
            throw new IllegalArgumentException("系统 Principal 必须使用配置 ID 在启动时登记");
        }
        PrincipalType discriminator = principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空"));
        Long id = idGenerator.nextId().value();
        repository.insertPrincipal(id, discriminator, IdentityStatus.ACTIVE);
        LOG.info(
                "Principal 已创建 | principalId={} principalType={}",
                id,
                discriminator);
        return id;
    }

    /**
     * 按标识和精确 Java 类型读取任意状态的 Principal。
     *
     * @param id        Principal 标识
     * @param type      Principal 精确 Java 类型
     * @return 匹配 Principal 标识；不存在或类型不匹配时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<Long> findPrincipal(
            @NotNull Long id,
            @NotNull Class<? extends Principal> type) {
        return repository.findPrincipalId(
                Objects.requireNonNull(id, "业务主体标识不能为空"),
                principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空")));
    }

    /**
     * 读取指定类型 Principal 的当前状态。
     *
     * @param id Principal 标识
     * @param type Principal 精确 Java 类型
     * @return 当前状态；不存在或类型不匹配时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<IdentityStatus> findPrincipalStatus(
            @NotNull Long id,
            @NotNull Class<? extends Principal> type) {
        return repository.findPrincipalStatus(
                Objects.requireNonNull(id, "业务主体标识不能为空"),
                principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空")));
    }

    /**
     * 按 IAM 公开条件筛选一组已知 Principal 标识。
     *
     * <p>该查询只读取 IAM 负责的 Principal、Account 和 Subject 事实，不判断角色或具体业务权限。
     *
     * @param query 有界 Principal 标识及可选类型、状态和 Account 关系条件
     * @return 保持查询标识顺序的匹配子集；缺失或不满足条件的标识被排除
     * @throws BusinessException 查询指定的 Java 类型没有 Principal Codec 时抛出
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable Set<Long> findPrincipalIds(@NotNull PrincipalQuery query) {
        PrincipalQuery checked = Objects.requireNonNull(query, "Principal 查询不能为空");
        PrincipalType type = null;
        var principalClass = checked.type();
        if (principalClass != null) {
            type = principalTypes.principalType(principalClass);
        }
        Set<Long> matched = repository.findPrincipalIds(
                checked.principalIds(),
                type,
                checked.status(),
                checked.linkedAccount());
        LOG.debug(
                "Principal 条件查询完成 | requestedCount={} matchedCount={}",
                checked.principalIds().size(),
                matched.size());
        return matched;
    }

    /**
     * 读取必须存在、类型匹配且启用的 Principal。
     *
     * @param id        Principal 标识
     * @param type      Principal 精确 Java 类型
     * @return 启用的 Principal 标识
     * @throws BusinessException Principal 不可用或类型未登记时抛出
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Long activePrincipalById(
            @NotNull Long id,
            @NotNull Class<? extends Principal> type) {
        return repository.findActivePrincipalId(
                Objects.requireNonNull(id, "业务主体标识不能为空"),
                principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空")))
                .orElseThrow(() -> new BusinessException(IamErrorCode.PRINCIPAL_UNAVAILABLE));
    }

    /**
     * 读取 Account 关联且启用的指定类型 Principal 标识。
     *
     * @param accountId Account 标识
     * @param type      Principal 精确 Java 类型
     * @return 不可修改的启用 Principal 标识列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Long> findActiveLinkedPrincipalIds(
            @NotNull Long accountId,
            @NotNull Class<? extends Principal> type) {
        return repository.findActiveLinkedPrincipalIds(
                Objects.requireNonNull(accountId, "账号标识不能为空"),
                principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空")));
    }

    /**
     * 后写覆盖 Principal 状态。Principal 类型不可修改。
     *
     * @param id        Principal 标识
     * @param status    新状态
     * @throws BusinessException 目标不存在时抛出
     */
    @Transactional
    public void changePrincipalStatus(
            @NotNull Long id,
            @NotNull IdentityStatus status) {
        if (repository.findPrincipalId(id, new PrincipalType(SystemPrincipalCodec.TYPE)).isPresent()) {
            throw new IllegalArgumentException("系统身份状态必须人工维护，不能通过普通身份 API 修改");
        }
        if (!repository.updatePrincipalStatus(
                Objects.requireNonNull(id, "业务主体标识不能为空"),
                Objects.requireNonNull(status, "业务主体状态不能为空"))) {
            throw new BusinessException(IamErrorCode.PRINCIPAL_UNAVAILABLE);
        }
        LOG.info(
                "Principal 状态已修改 | principalId={} status={}",
                id,
                status.value());
    }

    /**
     * 批量逻辑删除精确 Java 类型的 Principal，并保留 Account 与 Principal 的关系。
     *
     * <p>每个 Principal 都必须存在、类型匹配且尚未删除；任一目标不满足条件时事务整体失败。删除后
     * Principal 状态固定为 {@link IdentityStatus#DISABLED}。空集合不执行数据库写入。
     *
     * @param ids Principal 标识集合；重复标识只处理一次
     * @param type Principal 精确 Java 类型
     * @throws BusinessException Java 类型没有 Principal Codec 时抛出
     * @throws AffectedRowsMismatchException 任一 Principal 不存在、类型不匹配或已经删除时抛出
     */
    @Transactional
    public void deletePrincipals(
            @NotNull Collection<@NotNull Long> ids,
            @NotNull Class<? extends Principal> type) {
        if (type == SystemPrincipal.class) {
            throw new IllegalArgumentException("系统身份只能人工迁移，不能通过普通身份 API 删除");
        }
        LinkedHashSet<Long> unique = uniquePrincipalIds(ids);
        PrincipalType discriminator = principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空"));
        if (unique.isEmpty()) {
            return;
        }
        repository.deletePrincipals(unique, discriminator);
        LOG.debug(
                "Principal 批量删除已写入事务 | principalType={} principalCount={}",
                discriminator,
                unique.size());
    }

    /**
     * 批量恢复精确 Java 类型且已经逻辑删除的 Principal，使保留的 Account 关系重新可用。
     *
     * <p>每个 Principal 都必须存在、类型匹配且已经删除；任一目标不满足条件时事务整体失败。恢复后
     * Principal 状态固定为 {@link IdentityStatus#ACTIVE}。空集合不执行数据库写入。
     *
     * @param ids Principal 标识集合；重复标识只处理一次
     * @param type Principal 精确 Java 类型
     * @throws BusinessException Java 类型没有 Principal Codec 时抛出
     * @throws AffectedRowsMismatchException 任一 Principal 不存在、类型不匹配或未删除时抛出
     */
    @Transactional
    public void restorePrincipals(
            @NotNull Collection<@NotNull Long> ids,
            @NotNull Class<? extends Principal> type) {
        if (type == SystemPrincipal.class) {
            throw new IllegalArgumentException("系统身份只能人工迁移，不能通过普通身份 API 恢复");
        }
        LinkedHashSet<Long> unique = uniquePrincipalIds(ids);
        PrincipalType discriminator = principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空"));
        if (unique.isEmpty()) {
            return;
        }
        repository.restorePrincipals(unique, discriminator);
        LOG.debug(
                "Principal 批量恢复已写入事务 | principalType={} principalCount={}",
                discriminator,
                unique.size());
    }

    /**
     * 幂等建立 Account 可以使用 Principal 的关系。
     *
     * @param accountId   Account 标识
     * @param principalId Principal 标识
     * @throws BusinessException Account 或 Principal 不可用时抛出
     */
    @Transactional
    public void link(@NotNull Long accountId, @NotNull Long principalId) {
        if (repository.findPrincipalId(principalId, new PrincipalType(SystemPrincipalCodec.TYPE)).isPresent()) {
            throw new IllegalArgumentException("系统身份不能授权给登录 Account");
        }
        repository.findActiveAccount(Objects.requireNonNull(accountId, "账号标识不能为空"))
                .orElseThrow(() -> new BusinessException(IamErrorCode.ACCOUNT_UNAVAILABLE));
        if (!repository.isActivePrincipal(Objects.requireNonNull(principalId, "业务主体标识不能为空"))) {
            throw new BusinessException(IamErrorCode.PRINCIPAL_UNAVAILABLE);
        }
        repository.link(accountId, principalId);
        LOG.info(
                "Account Principal 关系已建立 | accountId={} principalId={}",
                accountId,
                principalId);
    }

    /**
     * 删除 Account 与 Principal 的关系。
     *
     * @param accountId   Account 标识
     * @param principalId Principal 标识
     * @throws BusinessException 关系不存在时抛出
     */
    @Transactional
    public void unlink(@NotNull Long accountId, @NotNull Long principalId) {
        if (!repository.unlink(
                Objects.requireNonNull(accountId, "账号标识不能为空"),
                Objects.requireNonNull(principalId, "业务主体标识不能为空"))) {
            throw new BusinessException(IamErrorCode.RELATION_NOT_FOUND);
        }
        LOG.info(
                "Account Principal 关系已删除 | accountId={} principalId={}",
                accountId,
                principalId);
    }

    /**
     * 读取 Account 关联的全部 Principal 标识。
     *
     * @param accountId Account 标识
     * @return 不可修改的 Principal 标识列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Long> findLinkedPrincipalIds(
            @NotNull Long accountId) {
        return repository.findLinkedPrincipalIds(
                Objects.requireNonNull(accountId, "账号标识不能为空"),
                null);
    }

    /**
     * 读取 Account 关联的指定 Java 类型 Principal 标识。
     *
     * @param accountId Account 标识
     * @param type      Principal 精确 Java 类型
     * @return 不可修改的 Principal 标识列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Long> findLinkedPrincipalIds(
            @NotNull Long accountId,
            @NotNull Class<? extends Principal> type) {
        return repository.findLinkedPrincipalIds(
                Objects.requireNonNull(accountId, "账号标识不能为空"),
                principalTypes.principalType(Objects.requireNonNull(type, "业务主体类型不能为空")));
    }

    /**
     * 读取 Principal 关联的全部 Account 标识。
     *
     * @param principalId Principal 标识
     * @return 不可修改的 Account 标识列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Long> findLinkedAccountIds(
            @NotNull Long principalId) {
        return repository.findLinkedAccountIds(
                Objects.requireNonNull(principalId, "业务主体标识不能为空"));
    }

    /**
     * 读取 Principal 关联且启用的全部 Account。
     *
     * @param principalId Principal 标识
     * @return 不可修改的启用 Account 列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Account> findActiveLinkedAccounts(
            @NotNull Long principalId) {
        return repository.findActiveLinkedAccounts(
                Objects.requireNonNull(principalId, "业务主体标识不能为空"));
    }

    /**
     * 批量读取 Principal 关联的 Account 标识。
     *
     * @param principalIds Principal 标识集合
     * @return 包含每个输入 Principal 的不可修改映射
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull Long>> findLinkedAccountIds(
            @NotNull Collection<@NotNull Long> principalIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>(
                Objects.requireNonNull(principalIds, "业务主体标识集合不能为空"));
        if (unique.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("业务主体标识不能为空");
        }
        if (unique.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> result = repository.findLinkedAccountIds(unique);
        LOG.debug(
                "Principal Account 关系已批量读取 | principalCount={} relationCount={}",
                unique.size(),
                result.values().stream().mapToInt(List::size).sum());
        return result;
    }

    /** @return 保持输入顺序且去除重复值的 Principal 标识 */
    private static @NotNull LinkedHashSet<@NotNull Long> uniquePrincipalIds(
            @NotNull Collection<@NotNull Long> principalIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long principalId : Objects.requireNonNull(principalIds, "业务主体标识集合不能为空")) {
            unique.add(Objects.requireNonNull(principalId, "业务主体标识不能为空"));
        }
        return unique;
    }

    /**
     * 为启用 Account 新增登录标识。
     *
     * @param accountId Account 标识
     * @param type      登录标识类型
     * @param rawValue  原始登录标识；不会写入日志
     * @return 新建登录标识
     * @throws BusinessException Account 不可用、类型未支持或规范值重复时抛出
     */
    @Transactional
    @CheckReturnValue
    public @NotNull AccountIdentifier addIdentifier(
            @NotNull Long accountId,
            @NotNull AccountIdentifierType type,
            @NotNull String rawValue) {
        Account account = repository.findActiveAccount(Objects.requireNonNull(accountId, "账号标识不能为空"))
                .orElseThrow(() -> new BusinessException(IamErrorCode.ACCOUNT_UNAVAILABLE));
        String normalized = normalize(type, rawValue);
        AccountIdentifier identifier = new AccountIdentifier(
                idGenerator.nextId().value(),
                accountId,
                account.realmKey(),
                type,
                rawValue.trim(),
                normalized,
                IdentityStatus.ACTIVE);
        try {
            repository.insertIdentifier(identifier);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_EXISTS, exception);
        }
        LOG.info(
                "Account 登录标识已新增 | identifierId={} accountId={} realm={} type={}",
                identifier.id(),
                accountId,
                account.realmKey().value(),
                type);
        return identifier;
    }

    /**
     * 修改登录标识原值与规范值。
     *
     * @param identifierId 登录标识主键
     * @param rawValue     新原始值；不会写入日志
     * @return 修改后的登录标识
     * @throws BusinessException 标识不存在或规范值重复时抛出
     */
    @Transactional
    @CheckReturnValue
    public @NotNull AccountIdentifier changeIdentifierValue(long identifierId, @NotNull String rawValue) {
        AccountIdentifier current = identifierById(identifierId);
        String normalized = normalize(current.type(), rawValue);
        AccountIdentifier updated;
        try {
            updated = repository.changeIdentifierValue(identifierId, rawValue.trim(), normalized)
                    .orElseThrow(() -> new BusinessException(IamErrorCode.IDENTIFIER_NOT_FOUND));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_EXISTS, exception);
        }
        LOG.info(
                "Account 登录标识值已修改 | identifierId={} accountId={} type={}",
                identifierId,
                updated.accountId(),
                updated.type());
        return updated;
    }

    /**
     * 修改登录标识状态。
     *
     * @param identifierId 登录标识主键
     * @param status       新状态
     * @return 修改后的登录标识
     * @throws BusinessException 标识不存在时抛出
     */
    @Transactional
    @CheckReturnValue
    public @NotNull AccountIdentifier changeIdentifierStatus(
            long identifierId,
            @NotNull IdentityStatus status) {
        if (!repository.updateIdentifierStatus(
                identifierId,
                Objects.requireNonNull(status, "登录标识状态不能为空"))) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_NOT_FOUND);
        }
        AccountIdentifier updated = identifierById(identifierId);
        LOG.info(
                "Account 登录标识状态已修改 | identifierId={} accountId={} type={} status={}",
                identifierId,
                updated.accountId(),
                updated.type(),
                updated.status().value());
        return updated;
    }

    /**
     * 删除登录标识。
     *
     * @param identifierId 登录标识主键
     * @throws BusinessException 标识不存在时抛出
     */
    @Transactional
    public void removeIdentifier(long identifierId) {
        AccountIdentifier current = identifierById(identifierId);
        if (!repository.deleteIdentifier(identifierId)) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_NOT_FOUND);
        }
        LOG.info(
                "Account 登录标识已删除 | identifierId={} accountId={} type={}",
                identifierId,
                current.accountId(),
                current.type());
    }

    /**
     * 读取 Account 的全部登录标识。
     *
     * @param accountId Account 标识
     * @return 不可修改的登录标识列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull AccountIdentifier> findIdentifiers(
            @NotNull Long accountId) {
        return repository.findIdentifiers(Objects.requireNonNull(accountId, "账号标识不能为空"));
    }

    /**
     * 用 Realm、类型和原始值解析完整可用身份链中的 Account。
     *
     * @param realm    认证域
     * @param type     登录标识类型
     * @param rawValue 原始查询值
     * @return 可用 Account 标识；没有匹配时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<Long> resolveAccount(
            @NotNull RealmKey realm,
            @NotNull AccountIdentifierType type,
            @NotNull String rawValue) {
        return repository.resolveActiveAccountId(
                Objects.requireNonNull(realm, "认证域不能为空"),
                Objects.requireNonNull(type, "登录标识类型不能为空"),
                normalize(type, rawValue));
    }

    /**
     * 判断任意已登记类型的 Principal 是否启用。
     *
     * @param id Principal 标识
     * @return Principal 存在且启用时为 {@code true}
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public boolean isActivePrincipal(@NotNull Long id) {
        return repository.isActivePrincipal(Objects.requireNonNull(id, "业务主体标识不能为空"));
    }

    /** 返回指定登录标识；不存在时使用稳定业务错误，而不是泄漏持久化空值。 */
    @CheckReturnValue
    private @NotNull AccountIdentifier identifierById(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("登录标识主键必须为正数");
        }
        return repository.findIdentifier(id)
                .orElseThrow(() -> new BusinessException(IamErrorCode.IDENTIFIER_NOT_FOUND));
    }

    /** 路由到对应开放标识类型的规范化器，并守住持久化列的统一长度契约。 */
    @CheckReturnValue
    private @NotNull String normalize(
            @NotNull AccountIdentifierType type,
            @NotNull String value) {
        AccountIdentifierNormalizer normalizer = normalizers.get(
                Objects.requireNonNull(type, "登录标识类型不能为空"));
        if (normalizer == null) {
            throw new BusinessException(IamErrorCode.IDENTIFIER_TYPE_UNSUPPORTED);
        }
        String normalized = Objects.requireNonNull(
                normalizer.normalize(value),
                "登录标识规范化器不能返回 null");
        if (normalized.isBlank() || normalized.length() > 320) {
            throw new IllegalArgumentException("登录标识规范值必须为 1 到 320 个字符");
        }
        LOG.trace(
                "登录标识已规范化 | identifierType={} normalizedLength={}",
                type,
                normalized.length());
        return normalized;
    }

}
