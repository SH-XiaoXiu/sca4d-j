package com.sca4d.core.api.authentication;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/** 控制指定 Account 的全部权威 Session，不读取或建立 Session。 */
public interface SessionControl {

    /**
     * 标记指定 Account 的全部 Session，使其在下次在线读取时重新装载身份与 Resource。
     *
     * <p>该操作保留现有登录状态。重复调用会继续增加失效代次。调用发生在数据库事务中时，
     * 实现必须在事务提交后执行失效；事务回滚时不得失效 Session。
     *
     * @param accountId Account 标识
     * @throws IllegalArgumentException Account 标识不是正数时抛出
     * @throws RuntimeException 失效代次无法更新时抛出
     */
    void invalidateAccount(long accountId);

    /**
     * 批量标记指定 Account 的全部 Session，使其在下次在线读取时重新装载身份与 Resource。
     *
     * <p>实现必须合并重复标识，并使用底层存储的批量写入方式。空集合不执行任何写入。调用发生在
     * 数据库事务中时，实现必须在事务提交后执行失效；事务回滚时不得失效 Session。
     *
     * @param accountIds Account 标识集合
     * @throws NullPointerException 集合或元素为空时抛出
     * @throws IllegalArgumentException 任一 Account 标识不是正数时抛出
     * @throws RuntimeException 任一失效代次无法更新时抛出；此时不能通知失效监听器
     */
    void invalidateAccounts(@NotNull Collection<@NotNull Long> accountIds);

    /**
     * 终止指定 Account 的全部权威 Session，要求客户端重新认证。
     *
     * @param accountId Account 标识
     * @param reason 终止原因；重复调用必须保持幂等
     * @throws IllegalArgumentException Account 标识不是正数时抛出
     * @throws RuntimeException Session 存储无法更新时抛出
     */
    void terminateAccount(long accountId, @NotNull SessionTerminationReason reason);

    /**
     * 终止指定 Account 除保留 Session 外的全部权威 Session。
     *
     * @param accountId Account 标识
     * @param retainedSessionId 必须保留的当前 Session 标识
     * @param reason 终止原因
     * @throws NullPointerException 保留 Session 标识或终止原因为空时抛出
     * @throws IllegalArgumentException Account 标识不是正数时抛出
     * @throws RuntimeException Session 存储无法更新时抛出
     */
    void terminateOtherSessions(
            long accountId,
            @NotNull SessionId retainedSessionId,
            @NotNull SessionTerminationReason reason);
}
