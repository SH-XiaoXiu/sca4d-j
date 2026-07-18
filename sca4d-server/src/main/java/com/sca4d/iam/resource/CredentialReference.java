package com.sca4d.iam.resource;

import com.sca4d.common.auth.CredentialType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 审计资源引用展示使用的非秘密 Credential 投影。 */
public class CredentialReference {

    private final Long credentialId;
    private final Long accountId;
    private final Long subjectId;
    private final CredentialType credentialType;
    private final String identifier;
    private final int deleted;

    /**
     * 创建 Credential 安全引用。
     *
     * @param credentialId Credential 主键
     * @param accountId 直接拥有该 Credential 的 Account
     * @param subjectId Account 所属 Subject
     * @param credentialType Credential 协议类型
     * @param identifier 当前管理页面显示的登录标识；协议拆分后可为空
     * @param deleted Credential 是否已经逻辑删除
     */
    public CredentialReference(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            @NotNull Long subjectId,
            @NotNull CredentialType credentialType,
            @Nullable String identifier,
            int deleted) {
        this.credentialId = credentialId;
        this.accountId = accountId;
        this.subjectId = subjectId;
        this.credentialType = credentialType;
        this.identifier = identifier;
        this.deleted = deleted;
    }

    public @NotNull Long getCredentialId() {
        return credentialId;
    }

    public @NotNull Long getAccountId() {
        return accountId;
    }

    public @NotNull Long getSubjectId() {
        return subjectId;
    }

    public @NotNull CredentialType getCredentialType() {
        return credentialType;
    }

    public @Nullable String getIdentifier() {
        return identifier;
    }

    /** @return 已逻辑删除时返回 true */
    public boolean deleted() {
        return deleted != 0;
    }
}
