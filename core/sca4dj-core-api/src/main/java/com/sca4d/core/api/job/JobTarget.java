package com.sca4d.core.api.job;

import com.sca4d.core.api.runtime.InstanceId;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/** 指定一项工作由任一合格实例、某个实例或当前全部合格实例执行。 */
public class JobTarget {

    /** 目标选择方式。 */
    public enum Kind {
        ANY,
        INSTANCE,
        ALL
    }

    private static final JobTarget ANY = new JobTarget(Kind.ANY, null, null);

    private final @Nullable Duration waitForInstance;

    private final @Nullable InstanceId instanceId;

    private final @NotNull Kind kind;

    private JobTarget(@NotNull Kind kind, @Nullable InstanceId instanceId, @Nullable Duration waitForInstance) {
        this.kind = kind;
        this.instanceId = instanceId;
        this.waitForInstance = waitForInstance;
    }

    /** 由任一启用所需能力的实例执行一次。 */
    @Contract(pure = true)
    public static @NotNull JobTarget any() {
        return ANY;
    }

    /** 只由指定实例执行；实例不就绪时最多等待给定时长。 */
    @Contract(pure = true)
    public static @NotNull JobTarget instance(@NotNull InstanceId instanceId, @NotNull Duration waitForInstance) {
        return new JobTarget(Kind.INSTANCE, Objects.requireNonNull(instanceId), positive(waitForInstance));
    }

    /** 由提交时在线且具备所需能力的每个实例各执行一次。 */
    @Contract(pure = true)
    public static @NotNull JobTarget all(@NotNull Duration waitForInstance) {
        return new JobTarget(Kind.ALL, null, positive(waitForInstance));
    }

    private static @NotNull Duration positive(@NotNull Duration duration) {
        Duration checked = Objects.requireNonNull(duration, "目标等待时间不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException("目标等待时间必须大于零");
        }
        return checked;
    }

    /** @return 目标选择方式 */
    public @NotNull Kind kind() {
        return kind;
    }

    /** @return 固定目标的实例标识；其他方式返回 null */
    public @Nullable InstanceId instanceId() {
        return instanceId;
    }

    /** @return 固定目标或广播份额的等待期限；ANY 返回 null */
    public @Nullable Duration waitForInstance() {
        return waitForInstance;
    }
}
