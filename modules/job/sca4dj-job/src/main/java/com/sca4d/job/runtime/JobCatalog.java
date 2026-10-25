package com.sca4d.job.runtime;

import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunRecording;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** 校验 Spring 登记的 Job，并按提交时保存的 key 和版本恢复定义。 */
public class JobCatalog {

    private volatile @Nullable Map<@NotNull JobKey, Map<Integer, Job<?, ?>>> jobs;

    private final @NotNull Supplier<Collection<@NotNull Job<?, ?>>> definitions;

    /**
     * 创建 Job 目录并拒绝重复 key 与版本组合。
     *
     * @param jobs 当前应用登记的全部 Job Bean
     */
    public JobCatalog(@NotNull Collection<@NotNull Job<?, ?>> jobs) {
        this.definitions = () -> Objects.requireNonNull(jobs, "Job 集合不能为空");
        this.jobs = collect(jobs);
    }

    /** 延迟读取 Spring Job Bean，避免业务扫描 Job 与 JobExecutor 的创建依赖成环。 */
    public JobCatalog(@NotNull Supplier<Collection<@NotNull Job<?, ?>>> definitions) {
        this.definitions = Objects.requireNonNull(definitions, "Job 定义来源不能为空");
    }

    private static @NotNull Map<JobKey, Map<Integer, Job<?, ?>>> collect(
            @NotNull Collection<@NotNull Job<?, ?>> jobs) {
        Map<JobKey, Map<Integer, Job<?, ?>>> byKey = new LinkedHashMap<>();
        for (Job<?, ?> job : Objects.requireNonNull(jobs, "Job 集合不能为空")) {
            validate(job);
            Job<?, ?> previous = byKey.computeIfAbsent(job.key(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(job.definitionVersion(), job);
            if (previous != null) {
                throw new IllegalStateException(
                        "Job key 和 definitionVersion 重复: " + job.key() + '/' + job.definitionVersion());
            }
        }
        byKey.replaceAll((key, versions) -> Map.copyOf(versions));
        return Map.copyOf(byKey);
    }

    private @NotNull Map<JobKey, Map<Integer, Job<?, ?>>> jobs() {
        Map<JobKey, Map<Integer, Job<?, ?>>> current = jobs;
        if (current == null) {
            synchronized (this) {
                current = jobs;
                if (current == null) {
                    current = collect(definitions.get());
                    jobs = current;
                }
            }
        }
        return current;
    }

    /**
     * 按提交时保存的 key 和定义版本恢复 Job。
     *
     * @param key Job 标识
     * @param definitionVersion 提交时的定义版本
     * @return 完全匹配的当前代码定义
     * @throws IllegalStateException Job 缺失或定义版本已不兼容时抛出
     */
    public @NotNull Job<?, ?> loadByKeyAndVersion(@NotNull JobKey key, int definitionVersion) {
        Map<Integer, Job<?, ?>> versions = jobs().get(Objects.requireNonNull(key, "JobKey 不能为空"));
        if (versions == null) {
            throw new IllegalStateException("Job 定义不存在: " + key);
        }
        Job<?, ?> job = versions.get(definitionVersion);
        if (job == null) {
            throw new IllegalStateException(
                    "Job definitionVersion 不可恢复: " + key
                            + ", requested=" + definitionVersion + ", available=" + versions.keySet());
        }
        return job;
    }

    /** 返回当前实例启用的 capability 允许执行的全部 Job key 和定义版本。 */
    public @NotNull Map<JobKey, Map<Integer, InstanceCapability>> availableVersions(
            @NotNull InstanceCapabilities capabilities) {
        InstanceCapabilities checked = Objects.requireNonNull(capabilities, "实例能力不能为空");
        Map<JobKey, Map<Integer, InstanceCapability>> available = new LinkedHashMap<>();
        jobs().forEach((key, versions) -> {
            Map<Integer, InstanceCapability> enabled = new LinkedHashMap<>();
            versions.values()
                    .stream()
                    .sorted(Comparator.comparingInt(Job::definitionVersion))
                    .forEach(job -> {
                        if (checked.enabled(job.requiredCapability())) {
                            InstanceCapability capability = checked.definitions().get(job.requiredCapability());
                            if (capability == null) {
                                throw new IllegalStateException("Job 所需能力未声明: " + job.requiredCapability());
                            }
                            enabled.put(job.definitionVersion(), capability);
                        }
                    });
            if (!enabled.isEmpty()) {
                available.put(key, Map.copyOf(enabled));
            }
        });
        return Map.copyOf(available);
    }

    /** 校验一个 Job 的公共定义。 */
    private static void validate(@NotNull Job<?, ?> job) {
        Job<?, ?> checked = Objects.requireNonNull(job, "Job Bean 不能为空");
        Objects.requireNonNull(checked.key(), "JobKey 不能为空");
        Objects.requireNonNull(checked.inputType(), "Job 输入类型不能为空");
        Objects.requireNonNull(checked.outputType(), "Job 输出类型不能为空");
        Objects.requireNonNull(checked.requiredCapability(), "Job 所需能力不能为空");
        Objects.requireNonNull(checked.runRecording(), "Job 运行记录策略不能为空");
        if (checked instanceof DirectJob<?, ?> direct
                && checked.runRecording() == JobRunRecording.NONE
                && !direct.retryRule().isOnce()) {
            throw new IllegalStateException("无记录 Job 不提供框架重试: " + checked.key());
        }
        if (checked instanceof PipelineJob<?, ?> && checked.runRecording() == JobRunRecording.NONE) {
            throw new IllegalStateException("PipelineJob 必须保存持久化运行记录: " + checked.key());
        }
        if (checked.runRecording() == JobRunRecording.NONE
                && checked.overlapPolicy() == OverlapPolicy.WAIT) {
            throw new IllegalStateException("无记录 Job 不能等待持久化执行队列: " + checked.key());
        }
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
