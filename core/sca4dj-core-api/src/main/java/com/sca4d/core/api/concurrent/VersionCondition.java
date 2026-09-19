package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次条件写入使用的预期版本，以及写入零行时的失败含义。 */
public final class VersionCondition {

    private final @NotNull Version expected;

    private final @NotNull ZeroRowPolicy whenZero;

    private VersionCondition(@NotNull Version expected, @NotNull ZeroRowPolicy whenZero) {
        this.expected = Objects.requireNonNull(expected, "预期版本不能为空");
        this.whenZero = Objects.requireNonNull(whenZero, "零行策略不能为空");
    }

    /**
     * 创建普通内部写入使用的乐观锁条件。
     *
     * @param currentVersion Repository 已读取的当前版本
     * @return 零行时报告内部乐观锁冲突的条件
     */
    @Contract("_ -> new")
    public static @NotNull VersionCondition optimistic(@NotNull Version currentVersion) {
        return new VersionCondition(currentVersion, ZeroRowPolicy.OPTIMISTIC_LOCK);
    }

    /**
     * 创建调用方提交的版本前置条件。
     *
     * @param expectedVersion 调用方读取资源时得到的版本
     * @return 零行时报告版本前置条件失败的条件
     */
    @Contract("_ -> new")
    public static @NotNull VersionCondition precondition(@NotNull Version expectedVersion) {
        return new VersionCondition(expectedVersion, ZeroRowPolicy.VERSION_PRECONDITION);
    }

    /**
     * 返回条件 SQL 应比较的版本。
     *
     * @return 预期版本
     */
    @Contract(pure = true)
    public @NotNull Version expected() {
        return expected;
    }

    /**
     * 返回条件 SQL 没有更新记录时的失败含义。
     *
     * @return 内部乐观锁冲突或调用方版本前置条件失败
     */
    @Contract(pure = true)
    public @NotNull ZeroRowPolicy whenZero() {
        return whenZero;
    }
}
