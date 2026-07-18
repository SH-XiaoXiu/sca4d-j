package com.sca4d.iam.recovery;

import org.jetbrains.annotations.NotNull;

/** 当前恢复码集合中尚未使用且可以参加 Argon2id 校验的摘要。 */
public final class RecoveryCodeCandidate {

    private final Long codeId;
    private final Long setId;
    private final Long accountId;
    private final String codeHash;
    private final Long setVersion;

    /**
     * 创建只在 IAM 内部参与恢复码校验的候选项。
     *
     * @param codeId 恢复码记录主键
     * @param setId 当前恢复码集合主键
     * @param accountId 恢复码所属 Account
     * @param codeHash 包含随机盐和参数的 Argon2id 编码摘要
     * @param setVersion 当前集合版本；集合被替换后不再通过复核
     */
    public RecoveryCodeCandidate(
            @NotNull Long codeId,
            @NotNull Long setId,
            @NotNull Long accountId,
            @NotNull String codeHash,
            @NotNull Long setVersion) {
        this.codeId = codeId;
        this.setId = setId;
        this.accountId = accountId;
        this.codeHash = codeHash;
        this.setVersion = setVersion;
    }

    /** @return 恢复码记录主键 */
    public @NotNull Long getCodeId() {
        return codeId;
    }

    /** @return 当前恢复码集合主键 */
    public @NotNull Long getSetId() {
        return setId;
    }

    /** @return 恢复码所属 Account */
    public @NotNull Long getAccountId() {
        return accountId;
    }

    /** @return 只允许 Argon2id 校验器读取的编码摘要 */
    public @NotNull String getCodeHash() {
        return codeHash;
    }

    /** @return 当前恢复码集合版本 */
    public @NotNull Long getSetVersion() {
        return setVersion;
    }
}
