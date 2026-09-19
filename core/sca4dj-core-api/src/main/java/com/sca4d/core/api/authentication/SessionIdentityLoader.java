package com.sca4d.core.api.authentication;

import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;

/** 认领 Account 并读取认证 Session 共用的 Subject 和 Account。 */
public interface SessionIdentityLoader {

    /**
     * 判断当前实现是否负责指定 Account 类型。
     *
     * @param account 待读取身份的 Account
     * @return 当前实现负责该 Account 时为 {@code true}
     */
    boolean supports(@NotNull Account account);

    /**
     * 重新读取可用 Account 及其 Subject。
     *
     * @param account Session 中原有的 Account，仅用于选择实现和定位当前身份
     * @return 当前数据库状态对应的 Subject 和 Account
     * @throws SessionIdentityUnavailableException Account 已停用或 Subject 关系已经失效时抛出
     */
    @NotNull
    SessionIdentity load(@NotNull Account account);
}
