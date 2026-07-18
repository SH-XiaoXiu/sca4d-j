package com.sca4d.iam.account;

import com.sca4d.entity.IamAccountEntity;
import org.jetbrains.annotations.NotNull;

/** 判断 Account 当前是否至少存在一种可以独立完成登录二次验证的认证方式。 */
public interface AccountSecondFactorAvailability {

    /**
     * 检查当前应用已注册方式及 Account 已绑定资料。
     *
     * @param account 即将启用登录二次验证的 Account
     * @return 至少一种方式可以完成独立第二因素时为 {@code true}
     */
    boolean available(@NotNull IamAccountEntity account);

    /**
     * 检查删除指定 Credential 后是否仍有其他方式可以完成登录第二因素。
     *
     * @param account 正在修改认证材料的 Account
     * @param credentialId 即将删除且不能再计入可用结果的 Credential 主键
     * @return 删除后至少保留一种可验证方式时为 {@code true}
     */
    boolean availableAfterRemovingCredential(
            @NotNull IamAccountEntity account, @NotNull Long credentialId);
}
