package com.sca4d.core.api.scheduling;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/** 标识代码中长期存在的一项 Job 计划，不使用 Bean 名或 Quartz key。 */
public final class ScheduleKey {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    private final @NotNull String value;

    /**
     * 创建代码计划标识。
     *
     * @param value 不超过 128 个字符，以小写字母开头，可继续使用小写字母、数字、点、横线或下划线
     * @throws NullPointerException 标识为空时抛出
     * @throws IllegalArgumentException 标识超过 128 个字符、包含首尾空白或不符合格式时抛出
     */
    public ScheduleKey(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "ScheduleKey 不能为空").trim();
        if (!checked.equals(value)) {
            throw new IllegalArgumentException("ScheduleKey 不能包含首尾空白");
        }
        if (checked.length() > 128) {
            throw new IllegalArgumentException("ScheduleKey 不能超过 128 个字符: length=" + checked.length());
        }
        if (!FORMAT.matcher(checked).matches()) {
            throw new IllegalArgumentException("ScheduleKey 格式错误: " + checked);
        }
        this.value = checked;
    }

    /**
     * 返回稳定计划标识文本。
     *
     * @return 构造时已经校验的标识
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof ScheduleKey key && value.equals(key.value);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
