package com.sca4d.iam.rbac;

import com.sca4d.core.api.permission.Operation;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 描述一组 Principal 必须获得全部或任一 Operation 的授权筛选条件。 */
public final class PrincipalGrantQuery {

    private final @NotNull @Unmodifiable List<@NotNull Long> principalIds;

    private final @NotNull @Unmodifiable List<@NotNull Operation> operations;

    private final boolean allOf;

    /** 保存 Builder 已校验的不可变授权查询。 */
    private PrincipalGrantQuery(
            @NotNull List<@NotNull Long> principalIds,
            @NotNull List<@NotNull Operation> operations,
            boolean allOf) {
        this.principalIds = principalIds;
        this.operations = operations;
        this.allOf = allOf;
    }

    /**
     * 从有界的 Principal 标识集合开始声明授权查询。
     *
     * @param principalIds 待筛选的正数标识；重复值只保留第一次出现的位置
     * @return 查询 Builder
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

    /** @return 待匹配的 Operation */
    @Contract(pure = true)
    @NotNull
    @Unmodifiable
    List<@NotNull Operation> operations() {
        return operations;
    }

    /** @return {@code true} 表示必须拥有全部 Operation，{@code false} 表示拥有任意一个即可 */
    @Contract(pure = true)
    boolean matchesAll() {
        return allOf;
    }

    /** 逐项声明 Principal 授权筛选条件。 */
    public static final class Builder {

        private final @NotNull @Unmodifiable List<@NotNull Long> principalIds;

        private List<Operation> operations;

        private boolean allOf;

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
         * 要求 Principal 同时获得给定的全部 Operation。
         *
         * @param operationCodes Operation 常量；至少包含一个非空白编码
         * @return 当前 Builder
         * @throws IllegalStateException 已经声明任一匹配条件时抛出
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder allOf(@NotNull String... operationCodes) {
            setOperations(true, operationCodes);
            return this;
        }

        /**
         * 要求 Principal 至少获得给定 Operation 中的一个。
         *
         * @param operationCodes Operation 常量；至少包含一个非空白编码
         * @return 当前 Builder
         * @throws IllegalStateException 已经声明全部匹配条件时抛出
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder anyOf(@NotNull String... operationCodes) {
            setOperations(false, operationCodes);
            return this;
        }

        /** @return 使用当前条件创建的不可变查询 */
        @Contract(value = "-> new", pure = true)
        @CheckReturnValue
        public @NotNull PrincipalGrantQuery build() {
            if (operations == null) {
                throw new IllegalStateException("必须声明 Principal 需要的 Operation");
            }
            return new PrincipalGrantQuery(principalIds, operations, allOf);
        }

        /** 校验并保存唯一一种 Operation 匹配方式。 */
        private void setOperations(boolean all, @NotNull String... operationCodes) {
            if (operations != null) {
                throw new IllegalStateException("Principal 授权查询只能声明一种 Operation 匹配方式");
            }
            String[] source = Objects.requireNonNull(operationCodes, "Operation 编码不能为空");
            if (source.length == 0) {
                throw new IllegalArgumentException("Principal 授权查询至少需要一个 Operation");
            }
            LinkedHashSet<Operation> unique = new LinkedHashSet<>();
            for (String code : source) {
                unique.add(new Operation(code));
            }
            this.operations = List.copyOf(unique);
            this.allOf = all;
        }
    }
}
