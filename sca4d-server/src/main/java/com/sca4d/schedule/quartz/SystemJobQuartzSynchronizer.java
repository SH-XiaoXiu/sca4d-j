package com.sca4d.schedule.quartz;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import com.sca4d.common.messaging.outbox.MessageOutboxDispatcher;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.config.properties.Sca4djIamCleanupProperties;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.file.FileCleanupJob;
import com.sca4d.iam.cleanup.SubjectCleanupJob;
import com.sca4d.iam.cleanup.TenantCleanupJob;
import org.jetbrains.annotations.NotNull;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** 注册当前启用能力的固定系统 Job，并重复执行同步而不创建重复 Quartz 记录。 */
@Component
public class SystemJobQuartzSynchronizer {

    /** 固定系统 Job 在 Quartz 中使用的 Job 和 Trigger group。 */
    public static final String GROUP = "system";

    private final Scheduler scheduler;
    private final List<SystemJobDefinition> definitions;

    /**
     * @param scheduler JDBC Quartz Scheduler
     * @param iamProperties Subject 清理间隔
     * @param messagingProperties Outbox 轮询间隔
     * @param fileProperties 文件清理间隔
     */
    public SystemJobQuartzSynchronizer(
            @NotNull Scheduler scheduler,
            @NotNull Sca4djIamCleanupProperties iamProperties,
            @NotNull Sca4djMessagingProperties messagingProperties,
            @NotNull Sca4djFileProperties fileProperties) {
        this.scheduler = scheduler;
        List<SystemJobDefinition> enabledDefinitions = new ArrayList<>();
        enabledDefinitions.add(definition(
                "subject-cleanup",
                "subject-cleanup-trigger",
                "清理已进入删除终态的 Subject 关系",
                SubjectCleanupJob.class,
                iamProperties.getCleanupInterval()));
        enabledDefinitions.add(definition(
                "tenant-cleanup",
                "tenant-cleanup-trigger",
                "清理已进入删除终态的 Tenant 运行时关系",
                TenantCleanupJob.class,
                iamProperties.getCleanupInterval()));
        if (messagingProperties.isEnabled() && messagingProperties.getOutbox().isEnabled()) {
            enabledDefinitions.add(definition(
                    "message-outbox-dispatch",
                    "message-outbox-dispatch-trigger",
                    "投递到期的消息 Outbox",
                    MessageOutboxDispatcher.class,
                    messagingProperties.getOutbox().getPollInterval()));
        }
        if (fileProperties.isEnabled()) {
            enabledDefinitions.add(definition(
                    "file-cleanup",
                    "file-cleanup-trigger",
                    "清理过期或等待删除的文件对象",
                    FileCleanupJob.class,
                    fileProperties.getCleanupInterval()));
        }
        this.definitions = List.copyOf(enabledDefinitions);
    }

    /**
     * 在 PostgreSQL 启动锁内幂等对账全部系统 Job。
     *
     * @throws SchedulerException Quartz 持久化或定义读取失败时抛出
     */
    public void reconcile() throws SchedulerException {
        Set<JobKey> enabledJobKeys = new HashSet<>();
        for (SystemJobDefinition definition : definitions) {
            enabledJobKeys.add(new JobKey(definition.getJobName(), GROUP));
        }
        for (JobKey storedJobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
            if (!enabledJobKeys.contains(storedJobKey)) {
                scheduler.deleteJob(storedJobKey);
            }
        }

        for (SystemJobDefinition definition : definitions) {
            JobKey jobKey = new JobKey(definition.getJobName(), GROUP);
            TriggerKey triggerKey = new TriggerKey(definition.getTriggerName(), GROUP);
            JobDetail expectedJob = newJob(definition.getJobClass())
                    .withIdentity(jobKey)
                    .withDescription(definition.getDescription())
                    .storeDurably()
                    .requestRecovery(true)
                    .usingJobData("definitionRevision", definition.getRevision())
                    .usingJobData("definitionFingerprint", definition.getFingerprint())
                    .build();
            Trigger expectedTrigger = newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withDescription(definition.getDescription())
                    .withSchedule(simpleSchedule()
                            .withIntervalInMilliseconds(definition.getInterval().toMillis())
                            .repeatForever()
                            .withMisfireHandlingInstructionNextWithRemainingCount())
                    .build();

            JobDetail storedJob = scheduler.getJobDetail(jobKey);
            if (storedJob == null) {
                scheduler.scheduleJob(expectedJob, expectedTrigger);
                continue;
            }
            if (!storedJob.getJobClass().equals(definition.getJobClass())) {
                throw new SchedulerException("系统 Job 类型冲突: " + jobKey);
            }
            int storedRevision = storedJob.getJobDataMap().getIntValue("definitionRevision");
            String storedFingerprint = storedJob.getJobDataMap().getString("definitionFingerprint");
            if (storedRevision > definition.getRevision()) {
                continue;
            }
            if (storedRevision == definition.getRevision() && !definition.getFingerprint().equals(storedFingerprint)) {
                throw new SchedulerException("同一系统 Job 定义版本出现配置漂移: " + jobKey);
            }
            if (storedRevision == definition.getRevision()
                    && (!storedJob.isDurable()
                            || !storedJob.requestsRecovery()
                            || storedJob.getJobDataMap().size() != 2
                            || !definition.getDescription().equals(storedJob.getDescription()))) {
                throw new SchedulerException("同一系统 Job 定义版本出现不可变属性漂移: " + jobKey);
            }
            if (storedRevision < definition.getRevision()) {
                scheduler.addJob(expectedJob, true, true);
            }

            Trigger storedTrigger = scheduler.getTrigger(triggerKey);
            if (storedTrigger == null) {
                scheduler.scheduleJob(expectedTrigger);
                continue;
            }
            if (!(storedTrigger instanceof SimpleTrigger simpleTrigger)
                    || !simpleTrigger.getJobKey().equals(jobKey)
                    || !definition.getDescription().equals(simpleTrigger.getDescription())
                    || simpleTrigger.getRepeatInterval() != definition.getInterval().toMillis()
                    || simpleTrigger.getMisfireInstruction() != SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT) {
                scheduler.rescheduleJob(triggerKey, expectedTrigger);
            }
        }
    }

    /** @return 只读系统 Job 定义 */
    public @NotNull List<SystemJobDefinition> definitions() {
        return definitions;
    }

    /**
     * 根据系统 Job 的完整持久化定义生成确定性指纹。
     *
     * @param jobName 固定 Job 名称
     * @param triggerName 固定 Trigger 名称
     * @param description 管理端安全说明
     * @param jobClass Quartz 执行类型
     * @param interval 固定触发周期
     * @return 修订号和 SHA-256 指纹已经固化的定义
     */
    private static @NotNull SystemJobDefinition definition(
            @NotNull String jobName,
            @NotNull String triggerName,
            @NotNull String description,
            @NotNull Class<? extends Job> jobClass,
            @NotNull Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("系统 Job 间隔必须为正数: " + jobName);
        }
        int revision = 1;
        String source = jobName + "|" + triggerName + "|" + description + "|" + jobClass.getName() + "|"
                + interval + "|durable=true|requestsRecovery=true|misfire=next-with-remaining-count|" + revision;
        try {
            String fingerprint = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
            return new SystemJobDefinition(
                    jobName, triggerName, description, jobClass, interval, revision, fingerprint);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256", exception);
        }
    }

}
