package com.sca4d.iam.recovery;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;

/** 恢复码被单次消费后形成的 Subject、Account 和恢复码集合版本。 */
public final class RecoveryCodeVerification {

    private final Subject subject;
    private final Account account;
    private final Long codeId;
    private final Long setVersion;

    /**
     * 创建不包含恢复码明文或摘要的验证结果。
     *
     * @param subject 当前仍可认证的 Subject
     * @param account 当前仍要求第二因子的 Account
     * @param codeId 已原子标记使用的恢复码记录主键
     * @param setVersion 验证时的当前恢复码集合版本
     */
    public RecoveryCodeVerification(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull Long codeId,
            @NotNull Long setVersion) {
        this.subject = subject;
        this.account = account;
        this.codeId = codeId;
        this.setVersion = setVersion;
    }

    /** @return 恢复码所属且当前仍可认证的 Subject */
    public @NotNull Subject getSubject() {
        return subject;
    }

    /** @return 恢复码所属且当前仍要求第二因子的 Account */
    public @NotNull Account getAccount() {
        return account;
    }

    /** @return 已使用的恢复码记录主键 */
    public @NotNull Long getCodeId() {
        return codeId;
    }

    /** @return 验证时的恢复码集合版本 */
    public @NotNull Long getSetVersion() {
        return setVersion;
    }
}
