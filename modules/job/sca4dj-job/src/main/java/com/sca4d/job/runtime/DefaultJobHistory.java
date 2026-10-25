package com.sca4d.job.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.job.JobHistory;
import com.sca4d.core.api.job.JobNodeOutput;
import com.sca4d.core.api.job.ScheduledJobRun;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.job.exception.JobException;
import com.sca4d.job.persistence.JobRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 使用当前 App 隔离名称的 Job Repository 返回类型化调度历史。 */
public class DefaultJobHistory implements JobHistory {

    private final @NotNull JobRepository repository;

    private final @NotNull Sca4djMapper objectMapper;

    public DefaultJobHistory(
            @NotNull JobRepository repository,
            @NotNull Sca4djMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "JobRepository 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Job ObjectMapper 不能为空");
    }

    @Override
    public <T> @NotNull Page<ScheduledJobRun<T>> findScheduledRuns(
            @NotNull ScheduleKey scheduleKey,
            @NotNull JobNodeOutput<T> output,
            @NotNull PageRequest page) {
        ScheduleKey checkedSchedule = Objects.requireNonNull(scheduleKey, "ScheduleKey 不能为空");
        JobNodeOutput<T> checkedOutput = Objects.requireNonNull(output, "Job 节点输出不能为空");
        return repository.findScheduledRuns(
                checkedSchedule,
                checkedOutput.jobKey(),
                checkedOutput.definitionVersion(),
                checkedOutput.nodeKey(),
                Objects.requireNonNull(page, "Job 历史分页要求不能为空"))
                .map(
                        record -> new ScheduledJobRun<>(
                                Id.of(record.runId()),
                                record.scheduledAt(),
                                record.status(),
                                record.completedAt(),
                                decode(record.outputJson(), checkedOutput.outputType())));
    }

    /** 把成功节点的 JSON 恢复为声明类型；节点没有成功时保留空值。 */
    private <T> @Nullable T decode(
            @Nullable String json,
            @NotNull Class<T> outputType) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, outputType);
        } catch (JsonProcessingException exception) {
            throw new JobException("Job 节点输出无法恢复: " + outputType.getName(), exception);
        }
    }
}
