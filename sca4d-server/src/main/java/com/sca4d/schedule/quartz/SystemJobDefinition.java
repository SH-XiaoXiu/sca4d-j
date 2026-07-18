package com.sca4d.schedule.quartz;

import org.jetbrains.annotations.NotNull;
import org.quartz.Job;

import java.time.Duration;

/** Quartz 对账使用的确定性系统 Job 定义。 */
public final class SystemJobDefinition {

    private final String jobName;
    private final String triggerName;
    private final String description;
    private final Class<? extends Job> jobClass;
    private final Duration interval;
    private final int revision;
    private final String fingerprint;

    /**
     * 创建一个由名称、类型、周期和修订号共同确定的系统 Job 定义。
     *
     * @param jobName system 分组内的固定 Job 名称
     * @param triggerName system 分组内的固定 Trigger 名称
     * @param description 管理端展示的安全说明
     * @param jobClass Quartz 执行类型
     * @param interval 两次触发之间的固定周期
     * @param revision 定义不兼容变化时单调增加的修订号
     * @param fingerprint 同一修订号和配置必须固定一致的 SHA-256 指纹
     */
    public SystemJobDefinition(
            @NotNull String jobName,
            @NotNull String triggerName,
            @NotNull String description,
            @NotNull Class<? extends Job> jobClass,
            @NotNull Duration interval,
            int revision,
            @NotNull String fingerprint) {
        this.jobName = jobName;
        this.triggerName = triggerName;
        this.description = description;
        this.jobClass = jobClass;
        this.interval = interval;
        this.revision = revision;
        this.fingerprint = fingerprint;
    }

    /** @return system 分组内的固定 Job 名称 */
    public @NotNull String getJobName() {
        return jobName;
    }

    /** @return system 分组内的固定 Trigger 名称 */
    public @NotNull String getTriggerName() {
        return triggerName;
    }

    /** @return 管理端展示的安全说明 */
    public @NotNull String getDescription() {
        return description;
    }

    /** @return Quartz 执行类型 */
    public @NotNull Class<? extends Job> getJobClass() {
        return jobClass;
    }

    /** @return 两次触发之间的固定周期 */
    public @NotNull Duration getInterval() {
        return interval;
    }

    /** @return 定义不兼容变化时单调增加的修订号 */
    public int getRevision() {
        return revision;
    }

    /** @return 同一修订号和配置固定一致的 SHA-256 指纹 */
    public @NotNull String getFingerprint() {
        return fingerprint;
    }
}
