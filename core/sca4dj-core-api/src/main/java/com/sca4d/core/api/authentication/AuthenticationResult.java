package com.sca4d.core.api.authentication;

import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 认证引擎单次消费满足结果后返回的可信动作认证数据。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class AuthenticationResult {

    private final Account account;

    private final AuthenticationActionCode actionCode;

    private final List<Authentication> authentications;

    /**
     * 创建已经满足动作要求且等待调用方使用的完成数据。
     *
     * @param account 已验证账号
     * @param actionCode 已满足认证要求的动作
     * @param authentications 本次动作接受的认证记录；零因素动作可以为空
     * @throws IllegalArgumentException 任一认证记录不属于指定账号时抛出
     */
    public AuthenticationResult(
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode,
            @NotNull List<Authentication> authentications) {
        this.account = Objects.requireNonNull(account, "认证账号不能为空");
        this.actionCode = Objects.requireNonNull(actionCode, "认证动作编码不能为空");
        this.authentications = List.copyOf(Objects.requireNonNull(authentications, "认证记录不能为空"));
        if (this.authentications.stream()
                .anyMatch(authentication -> authentication.accountId() != account.accountId())) {
            throw new IllegalArgumentException("认证记录不属于指定账号");
        }
    }

    /**
     * 返回全部认证记录共同验证的账号。
     *
     * @return 已验证账号
     */
    public @NotNull Account account() {
        return account;
    }

    /**
     * 返回本次认证允许执行的动作。
     *
     * @return 具体应用定义的动作编码
     */
    public @NotNull AuthenticationActionCode actionCode() {
        return actionCode;
    }

    /**
     * 返回满足动作要求的认证记录。
     *
     * @return 构造时复制的不可修改列表；零因素动作返回空列表
     */
    public @NotNull @Unmodifiable List<Authentication> authentications() {
        return authentications;
    }
}
