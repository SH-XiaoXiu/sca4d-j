package com.sca4d.iam.credential;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;

/**
 * 一次认证材料验证成功时形成的固定结果。
 *
 * <p>Credential ID 与版本用于在创建 Session 前确认认证材料没有被替换、停用或删除，防止验证和会话创建之间的并发变更。
 */
public final class CredentialVerification {

    private final Subject subject;
    private final Account account;
    private final Long credentialId;
    private final Long credentialVersion;

    /**
     * 创建认证材料验证结果。
     *
     * @param subject 认证材料当前绑定且可参与认证的 Subject
     * @param account 认证材料直接所属且可参与认证的 Account
     * @param credentialId 完成本次验证的 Credential 主键
     * @param credentialVersion 完成本次验证时的 Credential 乐观锁版本
     */
    public CredentialVerification(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull Long credentialId,
            @NotNull Long credentialVersion) {
        this.subject = subject;
        this.account = account;
        this.credentialId = credentialId;
        this.credentialVersion = credentialVersion;
    }

    /**
     * 返回已认证 Subject。
     *
     * @return 认证通过且当时处于 ACTIVE 状态的 Subject
     */
    public @NotNull Subject getSubject() {
        return subject;
    }

    /**
     * 返回已认证 Account。
     *
     * @return 认证通过且当时处于 ACTIVE 状态的 Account
     */
    public @NotNull Account getAccount() {
        return account;
    }

    /**
     * 返回完成验证的 Credential 主键。
     *
     * @return Credential 主键
     */
    public @NotNull Long getCredentialId() {
        return credentialId;
    }

    /**
     * 返回完成验证时的 Credential 版本。
     *
     * @return Credential 乐观锁版本
     */
    public @NotNull Long getCredentialVersion() {
        return credentialVersion;
    }
}
