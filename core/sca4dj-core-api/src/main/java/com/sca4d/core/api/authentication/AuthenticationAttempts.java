package com.sca4d.core.api.authentication;

import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 按具体应用提供的动作策略编排跨请求认证尝试。 */
public interface AuthenticationAttempts {

    /**
     * 使用能够识别账号的首个认证材料开始动作认证。
     *
     * @param actionCode 具体应用定义的动作编码
     * @param submission 能够识别并验证账号的认证材料
     * @return 当前认证进度
     */
    @CheckReturnValue
    @NotNull
    AuthenticationProgress start(
            @NotNull AuthenticationActionCode actionCode,
            @NotNull AuthenticationSubmission<?> submission);

    /**
     * 为已经确定的账号开始动作认证。
     *
     * @param account 要执行动作的账号
     * @param actionCode 具体应用定义的动作编码
     * @param reusableAuthentications 调用方持有且可能允许复用的认证记录
     * @return 当前认证进度；零因素或复用因素已经满足要求时状态可以立即为 {@code SATISFIED}
     */
    @CheckReturnValue
    @NotNull
    AuthenticationProgress start(
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode,
            @NotNull List<Authentication> reusableAuthentications);

    /**
     * 向现有尝试提交一个认证材料。
     *
     * @param attemptId 尝试标识
     * @param submission 认证材料
     * @return 更新后的认证进度
     */
    @CheckReturnValue
    @NotNull
    AuthenticationProgress submit(
            @NotNull AuthenticationAttemptId attemptId,
            @NotNull AuthenticationSubmission<?> submission);

    /**
     * 读取仍然有效的认证尝试已经确定的账号，供 Challenge 类认证方法准备后续材料。
     *
     * @param attemptId 尝试标识
     * @return 尝试开始时确定且仍然有效的 Account
     * @throws RuntimeException 尝试不存在、已过期或正在被其他请求修改时抛出
     */
    @CheckReturnValue
    @NotNull
    Account account(@NotNull AuthenticationAttemptId attemptId);

    /**
     * 单次消费已经满足当前动作策略的可信完成结果。
     *
     * @param attemptId 尝试标识
     * @return 只返回一次的完成结果
     */
    @CheckReturnValue
    @NotNull
    AuthenticationResult consume(@NotNull AuthenticationAttemptId attemptId);
}
