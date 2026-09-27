package com.sca4d.app.server.service;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.LoginResult;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationAttempts;
import com.sca4d.core.api.authentication.AuthenticationProgress;
import com.sca4d.core.api.authentication.AuthenticationResult;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.SessionLifecycle;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 把登录动作认证与 Session 建立组合成 Sca4d Server 登录流程。
 */
@Service
public class PlatformLoginService {

    private final AuthenticationAttempts authentication;

    private final SessionLifecycle sessions;

    /**
     * 创建 Sca4d Server 登录服务。
     */
    public PlatformLoginService(
            @NotNull AuthenticationAttempts authentication,
            @NotNull SessionLifecycle sessions) {
        this.authentication = Objects.requireNonNull(authentication, "认证尝试入口不能为空");
        this.sessions = Objects.requireNonNull(sessions, "Session 生命周期不能为空");
    }

    /**
     * 使用能够识别账号的首个认证材料开始登录。
     */
    public @NotNull LoginResult start(@NotNull AuthenticationSubmission<?> submission) {
        return completeWhenSatisfied(
                authentication.start(
                        AuthenticationActions.LOGIN_ACTION,
                        Objects.requireNonNull(submission, "登录认证材料不能为空")));
    }

    /**
     * 向已有登录尝试提交一个附加认证材料。
     */
    public @NotNull LoginResult submit(
            @NotNull AuthenticationAttemptId attemptId,
            @NotNull AuthenticationSubmission<?> submission) {
        return completeWhenSatisfied(
                authentication.submit(
                        Objects.requireNonNull(attemptId, "登录尝试标识不能为空"),
                        Objects.requireNonNull(submission, "登录认证材料不能为空")));
    }

    /**
     * 在登录要求满足时单次消费认证结果，并确保登录至少包含一种认证。
     */
    private LoginResult completeWhenSatisfied(AuthenticationProgress progress) {
        // 认证要求尚未满足时只返回当前可用方法，不提前消费 attempt 或建立 Session。
        if (!progress.satisfied()) {
            return new LoginResult(progress, null);
        }

        // 因素满足后单次消费认证结果，并复核当前 attempt 确实属于登录动作。
        AuthenticationResult completion = authentication.consume(progress.attemptId());
        if (!AuthenticationActions.LOGIN_ACTION.equals(completion.actionCode())) {
            throw new IllegalStateException("登录尝试返回了其他动作");
        }
        if (completion.authentications().isEmpty()) {
            throw new IllegalStateException("Sca4d Server 不允许零因素登录");
        }

        // 至少一条认证存在时才装载身份并建立 Session；建立结果由 Session 服务记录。
        return new LoginResult(
                progress,
                sessions.establish(completion.account(), completion.authentications()));
    }
}
