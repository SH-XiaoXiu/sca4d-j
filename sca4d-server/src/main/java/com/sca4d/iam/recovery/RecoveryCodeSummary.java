package com.sca4d.iam.recovery;

import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** Account 当前恢复码集合的签发时间、剩余数量和版本。 */
public class RecoveryCodeSummary {

    private final Long setId;
    private final Long accountId;
    private final OffsetDateTime issuedAt;
    private final int remainingCount;
    private final Long version;

    /**
     * 创建不包含恢复码摘要或明文的集合状态。
     *
     * @param setId 恢复码集合主键，只在 IAM 内部使用
     * @param accountId 所属 Account 主键
     * @param issuedAt 本集合生成时间
     * @param remainingCount 尚未使用的恢复码数量
     * @param version 集合替换使用的乐观锁版本
     */
    public RecoveryCodeSummary(
            @NotNull Long setId,
            @NotNull Long accountId,
            @NotNull OffsetDateTime issuedAt,
            int remainingCount,
            @NotNull Long version) {
        this.setId = setId;
        this.accountId = accountId;
        this.issuedAt = issuedAt;
        this.remainingCount = remainingCount;
        this.version = version;
    }

    /** @return 只在 IAM 内部关联恢复码使用的集合主键 */
    public @NotNull Long getSetId() {
        return setId;
    }

    /** @return 所属 Account 主键 */
    public @NotNull Long getAccountId() {
        return accountId;
    }

    /** @return 本集合签发时间 */
    public @NotNull OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    /** @return 尚未使用的恢复码数量 */
    public int getRemainingCount() {
        return remainingCount;
    }

    /** @return 当前集合版本 */
    public @NotNull Long getVersion() {
        return version;
    }
}
