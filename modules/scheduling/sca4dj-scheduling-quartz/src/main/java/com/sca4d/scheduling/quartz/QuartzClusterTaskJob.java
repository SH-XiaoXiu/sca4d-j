package com.sca4d.scheduling.quartz;

import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.NotNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

/** 由 Quartz 集群直接执行基础设施任务，不创建持久化 Job run。 */
@DisallowConcurrentExecution
public class QuartzClusterTaskJob implements Job {

    /** Quartz SchedulerContext 中保存集群任务目录的键。 */
    public static final String REGISTRY_CONTEXT_KEY = "sca4djClusterTaskRegistry";

    /** JobDataMap 中保存集群任务定义版本的键。 */
    public static final String TASK_DEFINITION_VERSION = "taskDefinitionVersion";

    /** JobDataMap 中保存集群任务 key 的键。 */
    public static final String TASK_KEY = "taskKey";

    public QuartzClusterTaskJob() {
    }

    @Override
    public void execute(@NotNull JobExecutionContext context) throws JobExecutionException {
        try {
            // JobDataMap 只保存稳定定位信息，实际任务从本实例完成对账后的目录恢复。
            JobDataMap data = context.getMergedJobDataMap();
            String taskKey = data.getString(TASK_KEY);
            int definitionVersion = Integer.parseInt(data.getString(TASK_DEFINITION_VERSION));
            QuartzClusterTaskRegistry registry = (QuartzClusterTaskRegistry) context.getScheduler()
                    .getContext()
                    .get(REGISTRY_CONTEXT_KEY);
            ClusterTaskSchedule schedule = registry.loadByKeyAndVersion(new ScheduleKey(taskKey), definitionVersion);
            schedule.task().execute();
        } catch (SchedulerException | RuntimeException exception) {
            throw new JobExecutionException("Quartz 集群任务执行失败", exception, false);
        }
    }
}
