package com.sca4d.scheduling.quartz;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.ClusterTaskScheduler;
import com.sca4d.core.api.scheduling.CronSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.JobScheduler;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.api.scheduling.ScheduleTiming;
import org.jetbrains.annotations.NotNull;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/** 对账代码声明的持久化 Job 计划和基础设施集群任务，并在对账成功后启动 Quartz。 */
public final class QuartzJobScheduler implements JobScheduler, ClusterTaskScheduler, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobScheduler.class);

    private static final String GROUP = "sca4dj-code";

    private static final String CLUSTER_TASK_GROUP = "sca4dj-cluster-task";

    private static final String FINGERPRINT = "scheduleFingerprint";

    private static final String CONTRACT = "scheduleContract";

    private static final LockKey RECONCILE_LOCK = new LockKey("scheduling", "quartz", "reconcile");

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull Scheduler scheduler;

    private final @NotNull QuartzScheduleRegistry registry;

    private final @NotNull QuartzClusterTaskRegistry clusterTaskRegistry;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull Clock clock;

    private final @NotNull ObjectProvider<JobSchedule<?, ?>> initialSchedules;

    private final @NotNull ObjectProvider<ClusterTaskSchedule> initialClusterTasks;

    private final @NotNull String schedulerName;

    private final @NotNull AtomicBoolean running = new AtomicBoolean();

    /**
     * 创建对账当前代码计划并管理 Quartz 生命周期的调度器。
     *
     * @param scheduler Spring Boot 创建的 JDBC Quartz Scheduler
     * @param registry 对账成功后供 fire 恢复定义的目录
     * @param clusterTaskRegistry 对账成功后供 fire 恢复集群任务的目录
     * @param distributedLock Quartz 计划对账使用的 Redis 分布式锁
     * @param transactionManager 主 PostgreSQL 事务管理器
     * @param objectMapper 脚手架统一 JSON 映射器
     * @param clock 固定间隔首次 fire 使用的时钟
     * @param initialSchedules 延迟读取 Spring 登记的全部代码计划
     * @param initialClusterTasks 延迟读取 Spring 登记的全部集群任务计划
     * @param schedulerName Quartz 集群共享名称
     */
    QuartzJobScheduler(
            @NotNull Scheduler scheduler,
            @NotNull QuartzScheduleRegistry registry,
            @NotNull QuartzClusterTaskRegistry clusterTaskRegistry,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull ObjectMapper objectMapper,
            @NotNull Clock clock,
            @NotNull ObjectProvider<JobSchedule<?, ?>> initialSchedules,
            @NotNull ObjectProvider<ClusterTaskSchedule> initialClusterTasks,
            @NotNull String schedulerName) {
        this.scheduler = Objects.requireNonNull(scheduler, "Quartz Scheduler 不能为空");
        this.registry = Objects.requireNonNull(registry, "QuartzScheduleRegistry 不能为空");
        this.clusterTaskRegistry = Objects.requireNonNull(
                clusterTaskRegistry,
                "QuartzClusterTaskRegistry 不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "Quartz 对账分布式锁不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "Quartz 事务管理器不能为空"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "Quartz ObjectMapper 不能为空");
        this.clock = Objects.requireNonNull(clock, "Quartz 时钟不能为空");
        this.initialSchedules = Objects.requireNonNull(initialSchedules, "Quartz 初始计划 Provider 不能为空");
        this.initialClusterTasks = Objects.requireNonNull(
                initialClusterTasks,
                "Quartz 初始集群任务 Provider 不能为空");
        this.schedulerName = Objects.requireNonNull(schedulerName, "Quartz schedulerName 不能为空");
    }

    @Override
    public void reconcile(@NotNull Collection<@NotNull JobSchedule<?, ?>> schedules) {
        // 在写入 Quartz 前完整校验计划 key，并计算用于阻止同定义版本静默变化的摘要。
        Map<ScheduleKey, JobSchedule<?, ?>> definitions = definitions(schedules);
        Map<ScheduleKey, String> contracts = new HashMap<>();
        Map<ScheduleKey, String> fingerprints = new HashMap<>();
        definitions.forEach((key, schedule) -> {
            String contract = scheduleContract(schedule);
            contracts.put(key, contract);
            fingerprints.put(key, DigestUtil.sha256Hex(contract));
        });
        // 先取得 Redis 锁，再开始数据库事务，使同一应用的多个实例串行修改代码计划。
        Integer deletedCount = distributedLock.execute(
                RECONCILE_LOCK,
                LOCK_WAIT,
                () -> transactions.execute(status -> {
                    try {
                        return reconcileQuartz(definitions, contracts, fingerprints);
                    } catch (SchedulerException exception) {
                        throw new IllegalStateException("Quartz 代码计划对账失败", exception);
                    }
                }));
        // 只有数据库对账全部成功后才替换 fire 可见的定义，避免执行一半新、一半旧的计划。
        registry.replace(definitions);
        log.debug(
                "Quartz 代码计划对账完成 | schedulerName={} definitionCount={} deletedCount={}",
                schedulerName,
                definitions.size(),
                deletedCount);
    }

    @Override
    public void reconcileClusterTasks(@NotNull Collection<@NotNull ClusterTaskSchedule> schedules) {
        // 在写入 Quartz 前完整校验计划 key，并计算用于阻止同定义版本静默变化的摘要。
        Map<ScheduleKey, ClusterTaskSchedule> definitions = clusterTaskDefinitions(schedules);
        Map<ScheduleKey, String> contracts = new HashMap<>();
        Map<ScheduleKey, String> fingerprints = new HashMap<>();
        definitions.forEach((key, schedule) -> {
            String contract = clusterTaskContract(schedule);
            contracts.put(key, contract);
            fingerprints.put(key, DigestUtil.sha256Hex(contract));
        });
        Integer deletedCount = distributedLock.execute(
                RECONCILE_LOCK,
                LOCK_WAIT,
                () -> transactions.execute(status -> {
                    try {
                        return reconcileClusterTasksQuartz(definitions, contracts, fingerprints);
                    } catch (SchedulerException exception) {
                        throw new IllegalStateException("Quartz 集群任务计划对账失败", exception);
                    }
                }));
        // 只有数据库对账全部成功后才替换 fire 可见的定义。
        clusterTaskRegistry.replace(definitions);
        log.debug(
                "Quartz 集群任务计划对账完成 | schedulerName={} definitionCount={} deletedCount={}",
                schedulerName,
                definitions.size(),
                deletedCount);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            // 只有持久化 Job 计划需要 JobExecutor；纯集群任务可以独立使用 Quartz。
            List<JobSchedule<?, ?>> jobSchedules = initialSchedules.orderedStream().toList();
            if (!jobSchedules.isEmpty()
                    && !scheduler.getContext().containsKey(QuartzDispatchJob.EXECUTOR_CONTEXT_KEY)) {
                throw new IllegalStateException("Quartz 存在 JobSchedule，但应用没有提供 JobExecutor");
            }
            reconcile(jobSchedules);
            reconcileClusterTasks(initialClusterTasks.orderedStream().toList());
            scheduler.start();
            log.info("Quartz 集群调度器已启动 | schedulerName={}", schedulerName);
        } catch (SchedulerException | RuntimeException exception) {
            running.set(false);
            throw new IllegalStateException("Quartz 对账或启动失败", exception);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            scheduler.standby();
            log.info("Quartz 集群调度器已停止接收触发 | schedulerName={}", schedulerName);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Quartz 进入 standby 失败", exception);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    /** 更新当前计划并删除代码中已经移除的 JobDetail。 */
    private int reconcileQuartz(
            @NotNull Map<ScheduleKey, JobSchedule<?, ?>> definitions,
            @NotNull Map<ScheduleKey, String> contracts,
            @NotNull Map<ScheduleKey, String> fingerprints) throws SchedulerException {
        for (Map.Entry<ScheduleKey, JobSchedule<?, ?>> entry : definitions.entrySet()) {
            ScheduleKey key = entry.getKey();
            JobSchedule<?, ?> schedule = entry.getValue();
            String fingerprint = fingerprints.get(key);
            JobKey jobKey = quartzJobKey(key);
            TriggerKey triggerKey = quartzTriggerKey(key);
            JobDetail existing = scheduler.getJobDetail(jobKey);
            Trigger.TriggerState triggerState = scheduler.getTriggerState(triggerKey);
            if (existing != null) {
                // 定义版本防止倒退，摘要防止调用方忘记递增已经变化的定义版本。
                JobDataMap existingData = existing.getJobDataMap();
                String storedDefinitionVersion = existingData.getString(
                        QuartzDispatchJob.SCHEDULE_DEFINITION_VERSION);
                if (storedDefinitionVersion != null) {
                    int existingDefinitionVersion = Integer.parseInt(storedDefinitionVersion);
                    String existingFingerprint = existingData.getString(FINGERPRINT);
                    String existingContract = existingData.getString(CONTRACT);
                    if (schedule.definitionVersion() < existingDefinitionVersion) {
                        throw new IllegalStateException(
                                "Quartz 计划 definitionVersion 不能倒退: " + key + ", stored="
                                        + existingDefinitionVersion + ", current=" + schedule.definitionVersion());
                    }
                    if (existingDefinitionVersion == schedule.definitionVersion()) {
                        if (!contracts.get(key).equals(existingContract)
                                || !fingerprint.equals(existingFingerprint)) {
                            throw new IllegalStateException(
                                    "Quartz 计划同 definitionVersion 定义发生变化: " + key
                                            + ", definitionVersion=" + schedule.definitionVersion());
                        }
                    }
                    if (contracts.get(key).equals(existingContract)
                            && fingerprint.equals(existingFingerprint)
                            && scheduler.checkExists(triggerKey)
                            && triggerState != Trigger.TriggerState.ERROR) {
                        continue;
                    }
                }
            }

            // 新计划、摘要变化、trigger 缺失或 ERROR 都用当前代码定义重建。
            JobDetail detail = jobDetail(schedule, contracts.get(key), fingerprint);
            scheduler.addJob(detail, true);
            Trigger trigger = trigger(schedule, jobKey);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        }

        // 完整集合中不存在的代码计划从当前应用的 Quartz scheduler 中删除。
        int deletedCount = 0;
        Set<JobKey> stored = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP));
        for (JobKey key : stored) {
            if (definitions.keySet().stream().noneMatch(definition -> definition.value().equals(key.getName()))) {
                if (scheduler.deleteJob(key)) {
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }

    /** 更新当前集群任务计划并删除代码中已经移除的集群任务 JobDetail。 */
    private int reconcileClusterTasksQuartz(
            @NotNull Map<ScheduleKey, ClusterTaskSchedule> definitions,
            @NotNull Map<ScheduleKey, String> contracts,
            @NotNull Map<ScheduleKey, String> fingerprints) throws SchedulerException {
        for (Map.Entry<ScheduleKey, ClusterTaskSchedule> entry : definitions.entrySet()) {
            ScheduleKey key = entry.getKey();
            ClusterTaskSchedule schedule = entry.getValue();
            String fingerprint = fingerprints.get(key);
            JobKey jobKey = quartzClusterTaskJobKey(key);
            TriggerKey triggerKey = quartzClusterTaskTriggerKey(key);
            JobDetail existing = scheduler.getJobDetail(jobKey);
            Trigger.TriggerState triggerState = scheduler.getTriggerState(triggerKey);
            if (existing != null) {
                JobDataMap existingData = existing.getJobDataMap();
                String storedDefinitionVersion = existingData.getString(
                        QuartzClusterTaskJob.TASK_DEFINITION_VERSION);
                if (storedDefinitionVersion != null) {
                    int existingDefinitionVersion = Integer.parseInt(storedDefinitionVersion);
                    String existingFingerprint = existingData.getString(FINGERPRINT);
                    String existingContract = existingData.getString(CONTRACT);
                    if (schedule.definitionVersion() < existingDefinitionVersion) {
                        throw new IllegalStateException(
                                "Quartz 集群任务 definitionVersion 不能倒退: " + key + ", stored="
                                        + existingDefinitionVersion + ", current=" + schedule.definitionVersion());
                    }
                    if (existingDefinitionVersion == schedule.definitionVersion()) {
                        if (!contracts.get(key).equals(existingContract)
                                || !fingerprint.equals(existingFingerprint)) {
                            throw new IllegalStateException(
                                    "Quartz 集群任务同 definitionVersion 定义发生变化: " + key
                                            + ", definitionVersion=" + schedule.definitionVersion());
                        }
                    }
                    if (contracts.get(key).equals(existingContract)
                            && fingerprint.equals(existingFingerprint)
                            && scheduler.checkExists(triggerKey)
                            && triggerState != Trigger.TriggerState.ERROR) {
                        continue;
                    }
                }
            }

            JobDetail detail = clusterTaskJobDetail(schedule, contracts.get(key), fingerprint);
            scheduler.addJob(detail, true);
            Trigger trigger = trigger(
                    schedule.key(),
                    schedule.timing(),
                    schedule.misfirePolicy(),
                    jobKey,
                    CLUSTER_TASK_GROUP);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        }

        int deletedCount = 0;
        Set<JobKey> stored = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(CLUSTER_TASK_GROUP));
        for (JobKey key : stored) {
            if (definitions.keySet().stream().noneMatch(definition -> definition.value().equals(key.getName()))
                    && scheduler.deleteJob(key)) {
                deletedCount++;
            }
        }
        return deletedCount;
    }

    /** 创建只保存代码计划定位信息的 durable JobDetail。 */
    private static @NotNull JobDetail jobDetail(
            @NotNull JobSchedule<?, ?> schedule,
            @NotNull String contract,
            @NotNull String fingerprint) {
        JobDataMap data = new JobDataMap();
        data.put(QuartzDispatchJob.SCHEDULE_KEY, schedule.key().value());
        data.put(
                QuartzDispatchJob.SCHEDULE_DEFINITION_VERSION,
                Integer.toString(schedule.definitionVersion()));
        data.put(FINGERPRINT, fingerprint);
        data.put(CONTRACT, contract);
        return JobBuilder.newJob(QuartzDispatchJob.class)
                .withIdentity(quartzJobKey(schedule.key()))
                .usingJobData(data)
                .storeDurably()
                .requestRecovery(false)
                .build();
    }

    /** 创建只保存集群任务定位信息的 durable JobDetail。 */
    private static @NotNull JobDetail clusterTaskJobDetail(
            @NotNull ClusterTaskSchedule schedule,
            @NotNull String contract,
            @NotNull String fingerprint) {
        JobDataMap data = new JobDataMap();
        data.put(QuartzClusterTaskJob.TASK_KEY, schedule.key().value());
        data.put(
                QuartzClusterTaskJob.TASK_DEFINITION_VERSION,
                Integer.toString(schedule.definitionVersion()));
        data.put(FINGERPRINT, fingerprint);
        data.put(CONTRACT, contract);
        return JobBuilder.newJob(QuartzClusterTaskJob.class)
                .withIdentity(quartzClusterTaskJobKey(schedule.key()))
                .usingJobData(data)
                .storeDurably()
                .requestRecovery(false)
                .build();
    }

    /** 按 Cron 或固定间隔声明 trigger 和 misfire 行为。 */
    private @NotNull Trigger trigger(
            @NotNull JobSchedule<?, ?> schedule,
            @NotNull JobKey jobKey) {
        return trigger(
                schedule.key(),
                schedule.timing(),
                schedule.misfirePolicy(),
                jobKey,
                GROUP);
    }

    /** 按 Cron 或固定间隔创建指定 Quartz group 中的 trigger。 */
    private @NotNull Trigger trigger(
            @NotNull ScheduleKey key,
            @NotNull ScheduleTiming timing,
            @NotNull MisfirePolicy misfirePolicy,
            @NotNull JobKey jobKey,
            @NotNull String group) {
        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(new TriggerKey(key.value(), group))
                .forJob(jobKey);
        if (timing instanceof CronSchedule cron) {
            CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(cron.expression())
                    .inTimeZone(TimeZone.getTimeZone(cron.zone()));
            cronBuilder = misfirePolicy == MisfirePolicy.SKIP
                    ? cronBuilder.withMisfireHandlingInstructionDoNothing()
                    : cronBuilder.withMisfireHandlingInstructionFireAndProceed();
            return builder.withSchedule(cronBuilder).build();
        }
        if (timing instanceof IntervalSchedule interval) {
            long intervalMillis = interval.interval().toMillis();
            if (intervalMillis < 1) {
                throw new IllegalArgumentException("Quartz 固定间隔不能小于 1 毫秒: " + key);
            }
            SimpleScheduleBuilder simple = SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMilliseconds(intervalMillis)
                    .repeatForever();
            simple = misfirePolicy == MisfirePolicy.SKIP
                    ? simple.withMisfireHandlingInstructionNextWithRemainingCount()
                    : simple.withMisfireHandlingInstructionNowWithExistingCount();
            Instant firstFire = clock.instant().plus(interval.initialDelay());
            return builder.startAt(Date.from(firstFire)).withSchedule(simple).build();
        }
        throw new IllegalArgumentException("不支持的 ScheduleTiming: " + timing.getClass().getName());
    }

    /** 校验完整计划集合中的 key 唯一且定义版本为正数。 */
    private static @NotNull Map<ScheduleKey, JobSchedule<?, ?>> definitions(
            @NotNull Collection<@NotNull JobSchedule<?, ?>> schedules) {
        Map<ScheduleKey, JobSchedule<?, ?>> definitions = new LinkedHashMap<>();
        for (JobSchedule<?, ?> schedule : Objects.requireNonNull(schedules, "Quartz 计划集合不能为空")) {
            JobSchedule<?, ?> checked = Objects.requireNonNull(schedule, "Quartz 计划不能为空");
            JobSchedule<?, ?> previous = definitions.putIfAbsent(checked.key(), checked);
            if (previous != null) {
                throw new IllegalStateException("Quartz 计划 key 重复: " + checked.key());
            }
        }
        return Map.copyOf(definitions);
    }

    /** 校验完整集群任务计划集合中的 key 唯一。 */
    private static @NotNull Map<ScheduleKey, ClusterTaskSchedule> clusterTaskDefinitions(
            @NotNull Collection<@NotNull ClusterTaskSchedule> schedules) {
        Map<ScheduleKey, ClusterTaskSchedule> definitions = new LinkedHashMap<>();
        for (ClusterTaskSchedule schedule : Objects.requireNonNull(schedules, "Quartz 集群任务集合不能为空")) {
            ClusterTaskSchedule checked = Objects.requireNonNull(schedule, "Quartz 集群任务不能为空");
            ClusterTaskSchedule previous = definitions.putIfAbsent(checked.key(), checked);
            if (previous != null) {
                throw new IllegalStateException("Quartz 集群任务 key 重复: " + checked.key());
            }
        }
        return Map.copyOf(definitions);
    }

    /** 计算同定义版本不可静默变化的稳定摘要。 */
    private @NotNull String scheduleContract(@NotNull JobSchedule<?, ?> schedule) {
        String timing;
        if (schedule.timing() instanceof CronSchedule cron) {
            timing = "cron|" + cron.expression() + "|" + cron.zone().getId();
        } else if (schedule.timing() instanceof IntervalSchedule interval) {
            timing = "interval|" + interval.interval() + "|" + interval.initialDelay();
        } else {
            throw new IllegalArgumentException(
                    "不支持的 ScheduleTiming: " + schedule.timing().getClass().getName());
        }
        String input;
        try {
            input = objectMapper.writeValueAsString(schedule.input());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Quartz 计划输入无法序列化: " + schedule.key(), exception);
        }
        return schedule.key().value() + "|" + schedule.definitionVersion()
                + "|" + schedule.job().key().value() + "|" + schedule.job().definitionVersion()
                + "|" + timing + "|" + schedule.misfirePolicy() + "|" + schedule.overlapPolicy()
                + "|" + input;
    }

    /** 计算集群任务不包含实现类名称的稳定调度契约。 */
    private static @NotNull String clusterTaskContract(@NotNull ClusterTaskSchedule schedule) {
        String timing = timingFingerprint(schedule.timing());
        return schedule.key().value() + "|" + schedule.definitionVersion()
                + "|" + timing + "|" + schedule.misfirePolicy();
    }

    /** 返回计划时间定义的稳定摘要片段。 */
    private static @NotNull String timingFingerprint(@NotNull ScheduleTiming timing) {
        if (timing instanceof CronSchedule cron) {
            return "cron|" + cron.expression() + "|" + cron.zone().getId();
        }
        if (timing instanceof IntervalSchedule interval) {
            return "interval|" + interval.interval() + "|" + interval.initialDelay();
        }
        throw new IllegalArgumentException("不支持的 ScheduleTiming: " + timing.getClass().getName());
    }

    /** 返回代码计划对应的 Quartz JobKey。 */
    private static @NotNull JobKey quartzJobKey(@NotNull ScheduleKey key) {
        return new JobKey(key.value(), GROUP);
    }

    /** 返回代码计划对应的 Quartz TriggerKey。 */
    private static @NotNull TriggerKey quartzTriggerKey(@NotNull ScheduleKey key) {
        return new TriggerKey(key.value(), GROUP);
    }

    /** 返回集群任务对应的 Quartz JobKey。 */
    private static @NotNull JobKey quartzClusterTaskJobKey(@NotNull ScheduleKey key) {
        return new JobKey(key.value(), CLUSTER_TASK_GROUP);
    }

    /** 返回集群任务对应的 Quartz TriggerKey。 */
    private static @NotNull TriggerKey quartzClusterTaskTriggerKey(@NotNull ScheduleKey key) {
        return new TriggerKey(key.value(), CLUSTER_TASK_GROUP);
    }
}
