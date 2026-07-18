package com.sca4d.authentication.recovery;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;

/** RECOVERY 认证 被单次消费后允许修改认证材料的 Subject 和 Account。 */
public final class ConsumedAccountRecovery {

    private final Subject subject;
    private final Account account;

    /**
     * 创建不包含 Proof 内容的恢复消费结果。
     *
     * @param subject 完成恢复证明且当前仍可认证的 Subject
     * @param account 完成恢复证明且当前仍可认证的 Account
     */
    public ConsumedAccountRecovery(@NotNull Subject subject, @NotNull Account account) {
        this.subject = subject;
        this.account = account;
    }

    /** @return 完成恢复证明且当前仍可认证的 Subject */
    public @NotNull Subject getSubject() {
        return subject;
    }

    /** @return 完成恢复证明且当前仍可认证的 Account */
    public @NotNull Account getAccount() {
        return account;
    }
}
