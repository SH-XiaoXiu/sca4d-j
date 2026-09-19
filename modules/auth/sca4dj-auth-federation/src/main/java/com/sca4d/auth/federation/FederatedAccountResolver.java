package com.sca4d.auth.federation;

import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;

/** 将已验证的外部资料解析为本地 Account，并由具体应用决定是否 JIT 创建。 */
public interface FederatedAccountResolver {

    /**
     * 解析或创建外部身份对应的本地账号。
     *
     * @param provider Provider 配置
     * @param profile 已验证且不含 Token 的外部资料
     * @return 可以交给认证引擎的本地 Account
     */
    @NotNull
    FederatedAccountResolution resolve(@NotNull FederatedProvider provider, @NotNull FederatedProfile profile);

    /** 把已验证外部资料明确绑定到当前账号，不执行 JIT 或邮箱自动关联。 */
    @NotNull
    FederatedAccountResolution link(
            @NotNull Account account,
            @NotNull FederatedProvider provider,
            @NotNull FederatedProfile profile);

    /** 在动作认证完成前复核外部身份绑定仍然有效。 */
    void verifyCurrentBinding(@NotNull Account account, @NotNull FederatedAccountResolution resolution);
}
