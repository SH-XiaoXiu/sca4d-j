package com.sca4d.core.api.job;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceId;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 向 DirectJob 提供当前执行、系统身份和尝试事实。 */
public class JobExecution {

    private final @Nullable Id runId;

    private final @NotNull InstanceId instanceId;

    private final @NotNull Instant startedAt;

    private final @NotNull SystemPrincipal systemPrincipal;

    private final @NotNull TreeCode capability;

    private final @NotNull UUID executionId;

    private final int attempt;

    /**
     * 创建一次 Job Attempt 的执行事实。
     *
     * @param executionId 本次实际执行标识
     * @param runId 所属持久化 Run；无记录 Job 为 null
     * @param attempt 从 1 开始的尝试次数
     * @param startedAt 本次尝试开始时间
     * @param instanceId 实际执行实例
     * @param capability 当前实例启用的 Job 能力
     * @param systemPrincipal 该能力对应的系统执行主体
     */
    public JobExecution(
            @NotNull UUID executionId,
            @Nullable Id runId,
            int attempt,
            @NotNull Instant startedAt,
            @NotNull InstanceId instanceId,
            @NotNull TreeCode capability,
            @NotNull SystemPrincipal systemPrincipal) {
        this.executionId = Objects.requireNonNull(executionId, "Job 执行标识不能为空");
        this.runId = runId;
        if (attempt < 1) {
            throw new IllegalArgumentException("Job 尝试次数不能小于 1");
        }
        this.attempt = attempt;
        this.startedAt = Objects.requireNonNull(startedAt, "Job 开始时间不能为空");
        this.instanceId = Objects.requireNonNull(instanceId, "Job 实例标识不能为空");
        this.capability = Objects.requireNonNull(capability, "Job 能力不能为空");
        this.systemPrincipal = Objects.requireNonNull(systemPrincipal, "Job 系统主体不能为空");
    }

    /**
     * 所属持久化 Run。
     *
     * @return 当前尝试所属的 Run；无记录 Job 返回空
     */
    @Contract(pure = true)
    public @NotNull Optional<Id> runId() {
        return Optional.ofNullable(runId);
    }

    /** @return 本次实际执行的唯一标识 */
    @Contract(pure = true)
    public @NotNull UUID executionId() {
        return executionId;
    }

    /** @return 实际执行本次 Attempt 的实例标识 */
    @Contract(pure = true)
    public @NotNull InstanceId instanceId() {
        return instanceId;
    }

    /** @return 本次执行使用的实例能力 */
    @Contract(pure = true)
    public @NotNull TreeCode capability() {
        return capability;
    }

    /** @return 本次执行使用的系统主体 */
    @Contract(pure = true)
    public @NotNull SystemPrincipal systemPrincipal() {
        return systemPrincipal;
    }

    /**
     * 当前尝试序号。
     *
     * @return 从 1 开始的尝试次数
     */
    @Contract(pure = true)
    public int attempt() {
        return attempt;
    }

    /**
     * 当前尝试开始时间。
     *
     * @return Job runtime 开始本次尝试时使用的时间
     */
    @Contract(pure = true)
    public @NotNull Instant startedAt() {
        return startedAt;
    }
}
