package com.sca4d.schedule.quartz;

import com.sca4d.common.core.TimeSupport;
import com.sca4d.entity.ScheduleJobRuntimeEntity;
import com.sca4d.schedule.SystemJobResult;
import com.sca4d.schedule.repository.ScheduleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.listeners.JobListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 保存系统 Job 最近运行快照并记录低基数执行指标。 */
@Component
public class SystemJobExecutionListener extends JobListenerSupport {

    private static final Logger log = LoggerFactory.getLogger(SystemJobExecutionListener.class);

    private final ScheduleRepository repository;
    private final MeterRegistry meterRegistry;
    private final Map<String, OffsetDateTime> startedAt = new ConcurrentHashMap<>();

    public SystemJobExecutionListener(@NotNull ScheduleRepository repository, @NotNull MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public @NotNull String getName() {
        return "system-job-runtime";
    }

    @Override
    public void jobToBeExecuted(@NotNull JobExecutionContext context) {
        startedAt.put(context.getFireInstanceId(), TimeSupport.now());
        JobKey jobKey = context.getJobDetail().getKey();
        log.trace(
                "Quartz 任务开始 | fireInstanceId={} jobGroup={} jobName={}",
                context.getFireInstanceId(),
                jobKey.getGroup(),
                jobKey.getName());
    }

    @Override
    public void jobWasExecuted(
            @NotNull JobExecutionContext context, JobExecutionException jobException) {
        OffsetDateTime completedAt = TimeSupport.now();
        OffsetDateTime actualStartedAt = startedAt.remove(context.getFireInstanceId());
        if (actualStartedAt == null) {
            actualStartedAt = completedAt.minusNanos(Math.max(0L, context.getJobRunTime()) * 1_000_000L);
        }
        long durationMillis = Math.max(0L, Duration.between(actualStartedAt, completedAt).toMillis());
        JobKey jobKey = context.getJobDetail().getKey();
        boolean success = jobException == null;
        String instanceId;
        try {
            instanceId = context.getScheduler().getSchedulerInstanceId();
        } catch (Exception exception) {
            instanceId = "unknown";
        }
        String safeError = success ? null : safeError(jobException);
        repository.saveJobRuntime(new ScheduleJobRuntimeEntity()
                .setJobGroup(jobKey.getGroup())
                .setJobName(jobKey.getName())
                .setLastActualFireTime(actualStartedAt)
                .setLastResult(success ? SystemJobResult.SUCCESS : SystemJobResult.FAILED)
                .setLastDurationMillis(durationMillis)
                .setSafeErrorSummary(safeError)
                .setExecutedInstanceId(instanceId)
                .setUpdatedAt(completedAt));

        String result = success ? "success" : "failure";
        meterRegistry.counter(
                "sca4dj.scheduler.job.executions", "job.group", jobKey.getGroup(), "job.name", jobKey.getName())
                .increment();
        meterRegistry.counter(
                success ? "sca4dj.scheduler.job.successes" : "sca4dj.scheduler.job.failures",
                "job.group", jobKey.getGroup(),
                "job.name", jobKey.getName())
                .increment();
        Timer.builder("sca4dj.scheduler.job.duration")
                .tags("job.group", jobKey.getGroup(), "job.name", jobKey.getName(), "result", result)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
        if (success) {
            log.trace(
                    "Quartz 任务完成 | fireInstanceId={} result={} durationMs={} jobGroup={} jobName={} instanceId={}",
                    context.getFireInstanceId(),
                    "success",
                    durationMillis,
                    jobKey.getGroup(),
                    jobKey.getName(),
                    instanceId);
        } else {
            Throwable failure = jobException.getCause() == null ? jobException : jobException.getCause();
            log.error(
                    "Quartz 任务失败 | fireInstanceId={} result={} durationMs={} jobGroup={} jobName={} instanceId={}",
                    context.getFireInstanceId(),
                    "failure",
                    durationMillis,
                    jobKey.getGroup(),
                    jobKey.getName(),
                    instanceId,
                    failure);
        }
    }

    /**
     * 把执行异常收敛为不包含 SQL、连接信息和业务敏感内容的类型摘要。
     *
     * @param exception Quartz 捕获的 Job 执行异常
     * @return 最多 500 字符的原始失败类型名称
     */
    private static @NotNull String safeError(@NotNull JobExecutionException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String value = cause.getClass().getName();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
