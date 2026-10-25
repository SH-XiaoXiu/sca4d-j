package com.sca4d.iam.base;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.query.Page;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.iam.identity.IdentityStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 提供框架无关的 Platform Account 分页目录、详情和启用状态修改操作。 */
public class PlatformAccountDirectory {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformAccountDirectory.class);

    private final @NotNull PlatformAccountDirectoryRepository repository;

    private final @Nullable SessionControl sessions;

    /**
     * 创建 Platform Account 目录。
     *
     * @param repository Platform Account 持久化语义
     * @param sessions 可选的 Account Session 终止实现；未安装 Auth 时为空
     */
    public PlatformAccountDirectory(
            @NotNull PlatformAccountDirectoryRepository repository,
            @Nullable SessionControl sessions) {
        this.repository = Objects.requireNonNull(repository, "Platform Account Repository 不能为空");
        this.sessions = sessions;
    }

    /**
     * 按用户名和状态读取一页 Platform Account。
     *
     * @param query 从 1 开始的分页和可选筛选条件
     * @return 当前页账号及完整命中数量
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull Page<PlatformAccountEntry> find(@NotNull PlatformAccountQuery query) {
        PlatformAccountQuery checked = Objects.requireNonNull(query, "Platform Account 查询不能为空");
        Page<PlatformAccountEntry> result = repository.find(checked);
        LOG.debug(
                "Platform Account 目录已读取 | page={} size={} resultCount={} total={}",
                result.getPage(),
                result.getSize(),
                result.getItems().size(),
                result.getTotal());
        return result;
    }

    /**
     * 读取任意状态的 Platform Account。
     *
     * @param accountId Account 标识
     * @return 包含用户名和唯一 PlatformPrincipal 的目录项
     * @throws BusinessException 账号不存在或不属于 Platform 时抛出
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull PlatformAccountEntry loadById(@NotNull Long accountId) {
        Long checkedId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        return Objects.requireNonNull(
                loadByIds(List.of(checkedId)).get(checkedId),
                "批量 Account 查询必须返回请求标识");
    }

    /**
     * 批量读取任意状态的 Platform Account；任一账号无效时不返回部分结果。
     *
     * @param accountIds 不包含空值或重复值的 Account 标识
     * @return 按 Account 标识索引的不可修改目录项
     */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull PlatformAccountEntry> loadByIds(
            @NotNull Collection<@NotNull Long> accountIds) {
        return repository.loadByIds(accountIds);
    }

    /**
     * 修改 Platform Account 状态。
     *
     * <p>停用会终止目标 Account 的全部 Session；当前 Account 和受保护 ROOT Account 不能停用。
     *
     * @param accountId 目标 Account 标识
     * @param status 新状态
     * @param currentAccountId 发起请求的 Account 标识
     * @return 修改后的 Platform Account
     * @throws BusinessException 账号受保护或不存在时抛出
     */
    @CheckReturnValue
    @Transactional
    public @NotNull PlatformAccountEntry changeStatus(
            @NotNull Long accountId,
            @NotNull IdentityStatus status,
            @NotNull Long currentAccountId) {
        PlatformAccountEntry current = loadById(accountId);
        IdentityStatus checkedStatus = Objects.requireNonNull(status, "Account 状态不能为空");
        if (checkedStatus == IdentityStatus.DISABLED) {
            if (current.accountId().equals(Objects.requireNonNull(currentAccountId, "当前 Account 标识不能为空"))) {
                throw new BusinessException(IamErrorCode.CURRENT_ACCOUNT);
            }
            if (current.protectedRoot()) {
                throw new BusinessException(IamErrorCode.PROTECTED_ACCOUNT);
            }
        }
        if (current.status() == checkedStatus) {
            return current;
        }
        repository.updateStatus(current.accountId(), checkedStatus);
        PlatformAccountEntry updated = loadById(current.accountId());
        if (checkedStatus == IdentityStatus.DISABLED && sessions != null) {
            sessions.terminateAccount(updated.accountId(), SessionTerminationReason.IDENTITY_UNAVAILABLE);
        }
        LOG.info(
                "Platform Account 状态已修改 | accountId={} previousStatus={} status={}",
                updated.accountId(),
                current.status().value(),
                updated.status().value());
        return updated;
    }

}
