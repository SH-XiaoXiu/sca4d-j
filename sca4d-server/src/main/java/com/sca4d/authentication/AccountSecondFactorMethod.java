package com.sca4d.authentication;

import com.sca4d.entity.IamAccountEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 声明一种认证方式判断指定 Account 能否完成登录第二因素的规则。 */
public interface AccountSecondFactorMethod {

    /**
     * 检查该方式需要的当前认证资料。
     *
     * @param account 即将要求登录第二因素的 Account
     * @param excludedCredentialId 即将删除且不能计入结果的 Credential；普通启用检查为 {@code null}
     * @return 该方式当前可以验证此 Account 时为 {@code true}
     */
    boolean available(
            @NotNull IamAccountEntity account, @Nullable Long excludedCredentialId);
}
