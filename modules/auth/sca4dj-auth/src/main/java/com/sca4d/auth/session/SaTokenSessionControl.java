package com.sca4d.auth.session;

import cn.dev33.satoken.stp.StpLogic;
import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.spring.transaction.TransactionCallbacks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 使用 Sa-Token 和 Redis 失效代次控制指定 Account 的全部 Session。 */
public final class SaTokenSessionControl implements SessionControl {

    private static final Logger LOG = LoggerFactory.getLogger(SaTokenSessionControl.class);

    private final @NotNull RedisSessionGeneration generations;

    private final @NotNull StpLogic stpLogic;

    private final @NotNull RedisSessionTokenIndex tokenIndex;

    /**
     * 创建不依赖身份、Principal 或 Resource Loader 的 Account Session 控制器。
     *
     * @param generations Account Session 失效代次存储
     * @param stpLogic Sa-Token 登录状态操作
     * @param tokenIndex SessionId 到不透明 Token 的索引
     */
    public SaTokenSessionControl(
            @NotNull RedisSessionGeneration generations,
            @NotNull StpLogic stpLogic,
            @NotNull RedisSessionTokenIndex tokenIndex) {
        this.generations = Objects.requireNonNull(generations, "Session 失效代次不能为空");
        this.stpLogic = Objects.requireNonNull(stpLogic, "Sa-Token 登录逻辑不能为空");
        this.tokenIndex = Objects.requireNonNull(tokenIndex, "Session Token 索引不能为空");
    }

    /** {@inheritDoc} */
    @Override
    public void invalidateAccount(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        TransactionCallbacks.afterCommitOrNow(() -> generations.invalidate(accountId));
    }

    /** {@inheritDoc} */
    @Override
    public void invalidateAccounts(@NotNull Collection<@NotNull Long> accountIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long accountId : Objects.requireNonNull(accountIds, "Account 标识集合不能为空")) {
            Long checkedId = Objects.requireNonNull(accountId, "Account 标识不能为空");
            if (checkedId <= 0) {
                throw new IllegalArgumentException("Account 标识必须为正数");
            }
            unique.add(checkedId);
        }
        if (unique.isEmpty()) {
            return;
        }
        List<Long> checkedIds = List.copyOf(unique);
        TransactionCallbacks.afterCommitOrNow(() -> generations.invalidate(checkedIds));
    }

    /** {@inheritDoc} */
    @Override
    public void terminateAccount(long accountId, @NotNull SessionTerminationReason reason) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        SessionTerminationReason checkedReason = Objects.requireNonNull(reason, "Session 终止原因不能为空");

        // 先移除全部 Sa-Token 登录状态，再增加代次阻止并发建立中的旧身份 Session 生效。
        stpLogic.logout(accountId);
        generations.invalidate(accountId);
        LOG.info("Account 全部 Session 已终止 | accountId={} reason={}", accountId, checkedReason.code());
    }

    @Override
    public void terminateOtherSessions(
            long accountId,
            @NotNull SessionId retainedSessionId,
            @NotNull SessionTerminationReason reason) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        SessionId retained = Objects.requireNonNull(retainedSessionId, "保留 Session 标识不能为空");
        SessionTerminationReason checkedReason = Objects.requireNonNull(reason, "Session 终止原因不能为空");
        String retainedToken = tokenIndex.find(retained).orElse(null);
        Runnable termination = () -> {
            for (String token : stpLogic.getTokenValueListByLoginId(accountId)) {
                if (!Objects.equals(token, retainedToken)) {
                    stpLogic.logoutByTokenValue(token);
                }
            }
            generations.invalidate(accountId);
            LOG.info(
                    "Account 其他 Session 已终止 | accountId={} retainedSessionId={} reason={}",
                    accountId,
                    retained.value(),
                    checkedReason.code());
        };
        TransactionCallbacks.afterCommitOrNow(termination);
    }
}
