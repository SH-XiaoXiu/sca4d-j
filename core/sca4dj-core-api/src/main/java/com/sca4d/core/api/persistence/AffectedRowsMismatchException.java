package com.sca4d.core.api.persistence;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示数据库写入的实际影响行数不符合 Repository 声明的范围。 */
public final class AffectedRowsMismatchException extends IllegalStateException {

    private final @NotNull String target;

    private final int minimum;

    private final int maximum;

    private final int actual;

    /**
     * 创建影响行数范围异常。
     *
     * @param target 发生写入的持久化对象说明
     * @param minimum 允许的最小影响行数
     * @param maximum 允许的最大影响行数
     * @param actual 数据库返回的实际影响行数
     */
    public AffectedRowsMismatchException(
            @NotNull String target,
            int minimum,
            int maximum,
            int actual) {
        super(message(target, minimum, maximum, actual));
        this.target = Objects.requireNonNull(target, "影响行数检查对象不能为空");
        this.minimum = minimum;
        this.maximum = maximum;
        this.actual = actual;
    }

    /** @return 发生写入的持久化对象说明 */
    public @NotNull String target() {
        return target;
    }

    /** @return 允许的最小影响行数 */
    public int minimum() {
        return minimum;
    }

    /** @return 允许的最大影响行数 */
    public int maximum() {
        return maximum;
    }

    /** @return 数据库返回的实际影响行数 */
    public int actual() {
        return actual;
    }

    /** 生成同时包含目标、允许范围和实际行数的异常消息。 */
    private static @NotNull String message(
            @NotNull String target,
            int minimum,
            int maximum,
            int actual) {
        String checked = Objects.requireNonNull(target, "影响行数检查对象不能为空");
        if (minimum == maximum) {
            return checked + "要求影响 " + minimum + " 行，实际影响 " + actual + " 行";
        }
        if (maximum == Integer.MAX_VALUE) {
            return checked + "要求至少影响 " + minimum + " 行，实际影响 " + actual + " 行";
        }
        return checked + "要求影响 " + minimum + " 到 " + maximum + " 行，实际影响 " + actual + " 行";
    }
}
