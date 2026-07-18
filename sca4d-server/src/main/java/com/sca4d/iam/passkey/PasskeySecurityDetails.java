package com.sca4d.iam.passkey;

import com.sca4d.iam.credential.CredentialStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/** 账号安全页可读取且不包含 WebAuthn 公钥、credential ID 和 user handle 的 Passkey 资料。 */
public class PasskeySecurityDetails {

    private final Long credentialId;
    private final Long accountId;
    private final CredentialStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final Long version;
    private final String displayName;
    private final boolean backupEligible;
    private final boolean backupState;
    private final OffsetDateTime lastUsedAt;

    /**
     * 创建 Passkey 的非敏感展示数据。
     *
     * @param credentialId 公共 Credential 主键
     * @param accountId Passkey 所属 Account 主键
     * @param status 当前是否可以参与认证
     * @param createdAt 注册时间
     * @param updatedAt 公共 Credential 或认证器状态最近修改时间
     * @param version 删除时提交的乐观锁版本
     * @param displayName 用户设置的展示名称
     * @param backupEligible 认证器是否允许同步或备份该 Passkey
     * @param backupState 最近一次 WebAuthn ceremony 确认该 Passkey 是否已经备份
     * @param lastUsedAt 最近一次成功 assertion 的时间；尚未使用时为空
     */
    public PasskeySecurityDetails(
            @NotNull Long credentialId,
            @NotNull Long accountId,
            @NotNull CredentialStatus status,
            @NotNull OffsetDateTime createdAt,
            @NotNull OffsetDateTime updatedAt,
            @NotNull Long version,
            @NotNull String displayName,
            boolean backupEligible,
            boolean backupState,
            @Nullable OffsetDateTime lastUsedAt) {
        this.credentialId = credentialId;
        this.accountId = accountId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.displayName = displayName;
        this.backupEligible = backupEligible;
        this.backupState = backupState;
        this.lastUsedAt = lastUsedAt;
    }

    /** @return 公共 Credential 主键 */
    public @NotNull Long getCredentialId() {
        return credentialId;
    }

    /** @return Passkey 所属 Account 主键 */
    public @NotNull Long getAccountId() {
        return accountId;
    }

    /** @return 当前 Credential 状态 */
    public @NotNull CredentialStatus getStatus() {
        return status;
    }

    /** @return Passkey 注册时间 */
    public @NotNull OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** @return 公共 Credential 或认证器状态最近修改时间 */
    public @NotNull OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** @return 删除时提交的乐观锁版本 */
    public @NotNull Long getVersion() {
        return version;
    }

    /** @return 用户设置的 Passkey 展示名称 */
    public @NotNull String getDisplayName() {
        return displayName;
    }

    /** @return 认证器是否允许同步或备份该 Passkey */
    public boolean isBackupEligible() {
        return backupEligible;
    }

    /** @return 最近一次 WebAuthn ceremony 确认该 Passkey 是否已经备份 */
    public boolean isBackupState() {
        return backupState;
    }

    /** @return 最近一次成功 assertion 的时间；尚未使用时为空 */
    public @Nullable OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }
}
