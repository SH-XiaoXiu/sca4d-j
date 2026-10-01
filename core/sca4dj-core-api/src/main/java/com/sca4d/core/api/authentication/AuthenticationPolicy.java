package com.sca4d.core.api.authentication;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;

/**
 * 由具体应用定义账号执行各项动作时需要的认证因素和可用认证方法。
 */
public interface AuthenticationPolicy {

    /**
     * 返回指定账号执行动作前必须满足的认证要求。
     *
     * @param account    要执行动作的账号
     * @param actionCode 具体应用定义的动作编码
     * @return 因素数量以及是否允许复用已有认证
     */
    @CheckReturnValue
    @NotNull
    AuthenticationRequirement requirementFor(
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode);

    /**
     * 返回当前认证进度下允许继续提交的方法。
     *
     * @param account                  要执行动作的账号
     * @param actionCode               具体应用定义的动作编码
     * @param completedAuthentications 本次尝试已经接受的认证记录
     * @return 具体应用允许使用的方法编码；返回值不得包含重复编码
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Set<TreeCode> allowedMethods(
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode,
            @NotNull List<Authentication> completedAuthentications);
}
