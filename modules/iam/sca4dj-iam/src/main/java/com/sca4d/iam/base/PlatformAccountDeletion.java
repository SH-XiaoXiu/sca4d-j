package com.sca4d.iam.base;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 描述一次 Platform Account 删除实际隔离的身份对象，供具体应用继续清理自己拥有的数据。 */
@SuppressWarnings("ClassCanBeRecord")
public final class PlatformAccountDeletion {

    private final @NotNull Long accountId;

    private final @NotNull Long subjectId;

    private final @NotNull Long principalId;

    private final boolean principalDeleted;

    private final boolean subjectDeleted;

    /** 创建已经完成 IAM 隔离的删除结果。 */
    public PlatformAccountDeletion(
            @NotNull Long accountId,
            @NotNull Long subjectId,
            @NotNull Long principalId,
            boolean principalDeleted,
            boolean subjectDeleted) {
        this.accountId = Objects.requireNonNull(accountId, "Account 标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "Subject 标识不能为空");
        this.principalId = Objects.requireNonNull(principalId, "Principal 标识不能为空");
        this.principalDeleted = principalDeleted;
        this.subjectDeleted = subjectDeleted;
    }

    /** 返回已删除的 Account 标识。 */
    @Contract(pure = true)
    public @NotNull Long accountId() {
        return accountId;
    }

    /** 返回 Account 所属 Subject 标识。 */
    @Contract(pure = true)
    public @NotNull Long subjectId() {
        return subjectId;
    }

    /** 返回删除前与 Account 关联的 PlatformPrincipal 标识。 */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /** 返回 PlatformPrincipal 是否因没有其他 Account 使用而被逻辑删除。 */
    @Contract(pure = true)
    public boolean principalDeleted() {
        return principalDeleted;
    }

    /** 返回 Subject 是否因没有其他 Account 而被逻辑删除。 */
    @Contract(pure = true)
    public boolean subjectDeleted() {
        return subjectDeleted;
    }
}
