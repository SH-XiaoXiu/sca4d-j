package com.sca4d.app.server.service;

import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationAttempts;
import com.sca4d.core.api.authentication.AuthenticationProgress;
import com.sca4d.core.api.authentication.AuthenticationResult;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.Session;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 为当前 Session 开始、推进并单次消费登录后动作认证。 */
@Service
public class ActionAuthenticationService {

    private final AuthenticationAttempts authentication;

    /** 创建登录后动作认证服务。 */
    public ActionAuthenticationService(@NotNull AuthenticationAttempts authentication) {
        this.authentication = Objects.requireNonNull(authentication, "认证尝试入口不能为空");
    }

    /** 使用策略允许复用的 Session 认证记录开始指定动作认证。 */
    public @NotNull AuthenticationProgress start(
            @NotNull Session session,
            @NotNull AuthenticationActionCode actionCode) {
        Session checkedSession = Objects.requireNonNull(session, "Session 不能为空");
        return authentication.start(
                checkedSession.account(),
                Objects.requireNonNull(actionCode, "认证动作编码不能为空"),
                checkedSession.authentications());
    }

    /** 向动作认证尝试提交一个新认证材料。 */
    public @NotNull AuthenticationProgress submit(
            @NotNull AuthenticationAttemptId attemptId,
            @NotNull AuthenticationSubmission<?> submission) {
        return authentication.submit(
                Objects.requireNonNull(attemptId, "动作认证尝试标识不能为空"),
                Objects.requireNonNull(submission, "动作认证材料不能为空"));
    }

    /**
     * 单次消费动作认证结果，并复核账号和动作与当前接口一致。
     *
     * @throws IllegalArgumentException 认证结果属于其他账号或动作时抛出
     */
    public void consume(
            @NotNull AuthenticationAttemptId attemptId,
            @NotNull Session session,
            @NotNull AuthenticationActionCode actionCode) {
        Session checkedSession = Objects.requireNonNull(session, "Session 不能为空");
        AuthenticationActionCode checkedAction = Objects.requireNonNull(actionCode, "认证动作编码不能为空");
        AuthenticationResult completion = authentication.consume(
                Objects.requireNonNull(attemptId, "动作认证尝试标识不能为空"));
        if (completion.account().accountId() != checkedSession.account().accountId()
                || !completion.actionCode().equals(checkedAction)) {
            throw new IllegalArgumentException("动作认证结果与当前账号或接口不匹配");
        }
    }
}
