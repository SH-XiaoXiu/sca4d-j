package com.sca4d.core.api.authentication;

import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** 建立、读取和终止单个权威 Session。 */
public interface SessionLifecycle {

    /**
     * 使用已经完成的认证事实建立通用 Session。
     *
     * @param account 已认证 Account；必须仍可用于建立身份
     * @param authentications 本次登录接受的认证事实，至少一条且必须属于该 Account
     * @return 新建 Session 和交付给调用方的一次性 Token 结果
     * @throws IllegalArgumentException 参数为空、认证列表为空或认证记录不属于 Account
     * @throws RuntimeException 身份装载、Session 保存或 Token 签发失败；失败时不得留下可用 Token 或半成品 Session
     */
    @CheckReturnValue
    @NotNull
    EstablishedSession establish(
            @NotNull Account account,
            @NotNull List<Authentication> authentications);

    /**
     * 按 Session 标识重新读取仍在线且已经完成失效刷新检查的 Session。
     *
     * @param sessionId Session 标识
     * @return Session 已终止、过期、失效刷新失败或底层状态不存在时为空
     * @throws RuntimeException Session 存储不可用等基础设施失败；此类失败不能转换成空结果
     */
    @CheckReturnValue
    @NotNull
    Optional<Session> resolve(@NotNull SessionId sessionId);

    /**
     * 按不透明 Token 读取当前仍在线的 Session。
     *
     * @param tokenValue 已去除 Bearer 前缀的非空 Token；空白 Token 视为无效输入
     * @return Token 无效、Session 已终止、过期或身份刷新失败时为空
     */
    @CheckReturnValue
    @NotNull
    Optional<Session> resolveToken(@NotNull String tokenValue);

    /**
     * 终止指定 Session。
     *
     * @param sessionId Session 标识
     * @param reason 终止原因；相同 Session 重复终止必须保持幂等
     * @throws RuntimeException 持久化状态无法更新时抛出；调用方不得把失败伪装成已终止
     */
    void terminate(@NotNull SessionId sessionId, @NotNull SessionTerminationReason reason);

}
