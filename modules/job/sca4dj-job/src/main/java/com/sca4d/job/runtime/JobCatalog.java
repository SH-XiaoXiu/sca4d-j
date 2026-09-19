package com.sca4d.job.runtime;

import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 校验 Spring 登记的 Job，并按稳定 key 恢复当前定义版本。 */
public final class JobCatalog {

    private final @NotNull Map<@NotNull JobKey, @NotNull Job<?, ?>> jobs;

    /**
     * 创建 Job 目录并拒绝重复 key 或无效定义版本。
     *
     * @param jobs 当前应用登记的全部 Job Bean
     */
    public JobCatalog(@NotNull Collection<@NotNull Job<?, ?>> jobs) {
        this.jobs = Objects.requireNonNull(jobs, "Job 集合不能为空")
                .stream()
                .peek(JobCatalog::validate)
                .collect(
                        Collectors.toUnmodifiableMap(
                                Job::key,
                                Function.identity(),
                                (left, right) -> {
                                    throw new IllegalStateException("JobKey 重复: " + left.key());
                                }));
    }

    /**
     * 按提交时保存的 key 和定义版本恢复 Job。
     *
     * @param key 稳定 Job 标识
     * @param definitionVersion 提交时的定义版本
     * @return 完全匹配的当前代码定义
     * @throws IllegalStateException Job 缺失或定义版本已不兼容时抛出
     */
    public @NotNull Job<?, ?> loadByKeyAndVersion(@NotNull JobKey key, int definitionVersion) {
        Job<?, ?> job = jobs.get(Objects.requireNonNull(key, "JobKey 不能为空"));
        if (job == null) {
            throw new IllegalStateException("Job 定义不存在: " + key);
        }
        if (job.definitionVersion() != definitionVersion) {
            throw new IllegalStateException(
                    "Job definitionVersion 不可恢复: " + key + ", expected=" + definitionVersion
                            + ", actual=" + job.definitionVersion());
        }
        return job;
    }

    /** 校验一个 Job 的公共定义。 */
    private static void validate(@NotNull Job<?, ?> job) {
        Job<?, ?> checked = Objects.requireNonNull(job, "Job Bean 不能为空");
        Objects.requireNonNull(checked.key(), "JobKey 不能为空");
        Objects.requireNonNull(checked.inputType(), "Job 输入类型不能为空");
        Objects.requireNonNull(checked.outputType(), "Job 输出类型不能为空");
        if (checked.definitionVersion() < 1) {
            throw new IllegalStateException("Job definitionVersion 必须为正数: " + checked.key());
        }
        if (checked instanceof PipelineJob<?, ?> pipelineJob) {
            PipelineJobPlan<?, ?> definition = pipelineJob.definition();
            if (definition.nodes().isEmpty()) {
                throw new IllegalStateException("PipelineJob 至少需要一个 Pipeline 节点: " + checked.key());
            }
        }
    }
}
