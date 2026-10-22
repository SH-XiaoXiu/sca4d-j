package com.sca4d.core.api.retry;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 描述固定、指数或序列退避，按已经开始的尝试次数计算等待时长。 */
public class Backoff {
    /** 可保存和恢复的内置退避类型。 */
    public enum Kind {
        /** 每次使用同一个间隔。 */
        FIXED,
        /** 间隔按倍率增长并在上限封顶。 */
        EXPONENTIAL,
        /** 按顺序读取间隔，用完后重复末项。 */
        SEQUENCE
    }

    private final Duration maximum;

    private final Kind kind;

    private final List<Duration> delays;

    private final double multiplier;

    private Backoff(Kind kind, List<Duration> delays, double multiplier, Duration maximum) {
        this.kind = kind;
        this.delays = List.copyOf(delays);
        this.multiplier = multiplier;
        this.maximum = maximum;
    }

    /**
     * @param delay 非负整数毫秒间隔
     * @return 每次失败使用相同间隔的退避
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull Backoff fixed(@NotNull Duration delay) {
        millis(delay);
        return new Backoff(Kind.FIXED, List.of(delay), 1, delay);
    }

    /**
     * @param initial 首次失败后的正数间隔
     * @param multiplier 大于 1 的有限倍率
     * @param maximum 不小于初始间隔的上限
     * @return 有上限的指数退避
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull Backoff exponential(
            @NotNull Duration initial,
            double multiplier,
            @NotNull Duration maximum) {
        long initialMillis = millis(initial);
        if (initialMillis == 0 || millis(maximum) < initialMillis || !Double.isFinite(multiplier) || multiplier <= 1) {
            throw new IllegalArgumentException("指数退避要求正数初始间隔、有效上限和大于 1 的有限倍率");
        }
        return new Backoff(Kind.EXPONENTIAL, List.of(initial), multiplier, maximum);
    }

    /**
     * @param delays 非空的非负整数毫秒间隔序列
     * @return 用完序列后重复最后一项的退避
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull Backoff sequence(Duration @NotNull... delays) {
        List<Duration> values = List.of(Objects.requireNonNull(delays));
        if (values.isEmpty())
            throw new IllegalArgumentException("重试间隔序列不能为空");
        values.forEach(Backoff::millis);
        return new Backoff(Kind.SEQUENCE, values, 1, values.getLast());
    }

    /**
     * @param attempt 已经开始且失败的尝试次数，从 1 开始
     * @return 下一次执行前的等待时长
     */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull Duration delayAfter(long attempt) {
        if (attempt < 1)
            throw new IllegalArgumentException("尝试次数必须为正数");
        if (kind == Kind.FIXED)
            return delays.getFirst();
        if (kind == Kind.SEQUENCE)
            return delays.get((int) Math.min(attempt - 1, delays.size() - 1));
        long initial = delays.getFirst().toMillis();
        long cap = maximum.toMillis();
        if (attempt == 1)
            return delays.getFirst();
        double value = initial * Math.pow(multiplier, (double) (attempt - 1));
        return Duration.ofMillis(value >= cap ? cap : Math.min(cap, (long) Math.ceil(value)));
    }

    /** @return 退避类型 */
    public @NotNull Kind kind() {
        return kind;
    }

    /** @return 固定或指数模式的初始间隔，或序列模式的全部间隔 */
    public @NotNull List<Duration> delays() {
        return delays;
    }

    /** @return 指数增长倍率；其他模式为 1 */
    public double multiplier() {
        return multiplier;
    }

    /** @return 指数模式的间隔上限，其他模式为末项间隔 */
    public @NotNull Duration maximum() {
        return maximum;
    }

    /** 校验所有执行方式共用的非负整数毫秒时长。 */
    static long millis(Duration value) {
        Objects.requireNonNull(value, "重试间隔不能为空");
        try {
            long millis = value.toMillis();
            if (value.isNegative() || !Duration.ofMillis(millis).equals(value)) {
                throw new IllegalArgumentException("重试间隔必须是非负整数毫秒");
            }
            return millis;
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("重试间隔超过毫秒表示范围", failure);
        }
    }
}
