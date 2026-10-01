package com.sca4d.iam.identity;

import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 描述一组已知 Principal 标识需要满足的 IAM 身份和 Account 关系条件。 */
public class PrincipalQuery {

    private final @Nullable Class<? extends Principal> type;

    private final @Nullable IdentityStatus status;

    private final @Nullable LinkedAccountCondition linkedAccount;

    private final @NotNull @Unmodifiable List<@NotNull Long> principalIds;

    /** 保存 Builder 已校验的不可变查询条件。 */
    private PrincipalQuery(
            @NotNull List<@NotNull Long> principalIds,
            @Nullable Class<? extends Principal> type,
            @Nullable IdentityStatus status,
            @Nullable LinkedAccountCondition linkedAccount) {
        this.principalIds = principalIds;
        this.type = type;
        this.status = status;
        this.linkedAccount = linkedAccount;
    }

    /**
     * 从有界的 Principal 标识集合开始声明查询。
     *
     * @param principalIds 待读取的正数标识；重复值只保留第一次出现的位置
     * @return 查询 Builder
     * @throws NullPointerException 集合或元素为空时抛出
     * @throws IllegalArgumentException 标识不是正数时抛出
     */
    @Contract(value = "_ -> new", pure = true)
    @CheckReturnValue
    public static @NotNull Builder forIds(@NotNull Collection<@NotNull Long> principalIds) {
        return new Builder(principalIds);
    }

    /** @return 去重并保持首次出现顺序的 Principal 标识 */
    @Contract(pure = true)
    @NotNull
    @Unmodifiable
    List<@NotNull Long> principalIds() {
        return principalIds;
    }

    /** @return 需要精确匹配的 Principal Java 类型；不限制类型时为空 */
    @Contract(pure = true)
    @Nullable
    Class<? extends Principal> type() {
        return type;
    }

    /** @return 需要匹配的 Principal 状态；不限制状态时为空 */
    @Contract(pure = true)
    @Nullable
    IdentityStatus status() {
        return status;
    }

    /** @return 需要满足的 Account 关系；不检查 Account 关系时为空 */
    @Contract(pure = true)
    @Nullable
    LinkedAccountCondition linkedAccount() {
        return linkedAccount;
    }

    /** 逐项声明 Principal 查询条件。 */
    public static class Builder {

        private final @NotNull @Unmodifiable List<@NotNull Long> principalIds;

        private @Nullable Class<? extends Principal> type;

        private @Nullable IdentityStatus status;

        private @Nullable LinkedAccountCondition linkedAccount;

        /** 复制、校验并去重查询标识。 */
        private Builder(@NotNull Collection<@NotNull Long> principalIds) {
            Collection<@NotNull Long> source = Objects.requireNonNull(principalIds, "业务主体标识集合不能为空");
            LinkedHashSet<Long> unique = new LinkedHashSet<>();
            for (Long principalId : source) {
                long checked = Objects.requireNonNull(principalId, "业务主体标识不能为空");
                if (checked < 1) {
                    throw new IllegalArgumentException("业务主体标识必须为正数");
                }
                unique.add(checked);
            }
            this.principalIds = List.copyOf(unique);
        }

        /**
         * 限制 Principal 的精确 Java 类型。
         *
         * @param type 应用已登记 Principal Codec 的 Java 类型
         * @return 当前 Builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder type(@NotNull Class<? extends Principal> type) {
            this.type = Objects.requireNonNull(type, "业务主体类型不能为空");
            return this;
        }

        /**
         * 限制 Principal 当前状态。
         *
         * @param status 待匹配状态
         * @return 当前 Builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder status(@NotNull IdentityStatus status) {
            this.status = Objects.requireNonNull(status, "业务主体状态不能为空");
            return this;
        }

        /**
         * 限制 Principal 必须存在符合条件的 Account 关系。
         *
         * @param condition Account 关系条件
         * @return 当前 Builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder linkedAccount(@NotNull LinkedAccountCondition condition) {
            this.linkedAccount = Objects.requireNonNull(condition, "账号关系条件不能为空");
            return this;
        }

        /** @return 使用当前条件创建的不可变查询 */
        @Contract(value = "-> new", pure = true)
        @CheckReturnValue
        public @NotNull PrincipalQuery build() {
            return new PrincipalQuery(principalIds, type, status, linkedAccount);
        }
    }
}
