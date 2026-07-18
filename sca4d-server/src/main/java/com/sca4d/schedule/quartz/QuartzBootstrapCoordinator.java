package com.sca4d.schedule.quartz;

import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

import com.sca4d.config.properties.Sca4djSchedulerProperties;
import com.sca4d.schedule.SchedulePlanSynchronizer;
import jakarta.annotation.PreDestroy;
import org.jetbrains.annotations.NotNull;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

/** 在 Scheduler 启动前串行完成集群定义对账的 PostgreSQL 协调入口。 */
@Component
public class QuartzBootstrapCoordinator {

    private static final Logger log = LoggerFactory.getLogger(QuartzBootstrapCoordinator.class);

    private final Scheduler scheduler;
    private final DataSource dataSource;
    private final Sca4djSchedulerProperties properties;
    private final SystemJobQuartzSynchronizer systemJobs;
    private final SchedulePlanSynchronizer schedulePlans;
    private final SystemJobExecutionListener systemJobExecutionListener;
    private final AtomicBoolean reconciliationSucceeded = new AtomicBoolean(false);
    private volatile String failureType;

    public QuartzBootstrapCoordinator(
            @NotNull Scheduler scheduler,
            @NotNull DataSource dataSource,
            @NotNull Sca4djSchedulerProperties properties,
            @NotNull SystemJobQuartzSynchronizer systemJobs,
            @NotNull SchedulePlanSynchronizer schedulePlans,
            @NotNull SystemJobExecutionListener systemJobExecutionListener) {
        this.scheduler = scheduler;
        this.dataSource = dataSource;
        this.properties = properties;
        this.systemJobs = systemJobs;
        this.schedulePlans = schedulePlans;
        this.systemJobExecutionListener = systemJobExecutionListener;
    }

    /** Flyway 和整个应用上下文就绪后，获取集群启动锁、对账并显式启动 Scheduler。 */
    @EventListener(ApplicationReadyEvent.class)
    public void startScheduler() {
        String instanceId;
        try {
            instanceId = scheduler.getSchedulerInstanceId();
        } catch (SchedulerException exception) {
            instanceId = "unavailable";
        }
        String nodeId = ManagementFactory.getRuntimeMXBean().getName();
        long lockKey = advisoryLockKey(properties.getSchedulerName());
        Instant deadline = Instant.now().plus(properties.getBootstrapLockTimeout());
        Connection connection = null;
        boolean locked = false;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            while (!locked && Instant.now().isBefore(deadline)) {
                locked = tryLock(connection, lockKey);
                if (!locked) {
                    log.debug(
                            "Quartz 启动锁正由其他节点使用，继续等待 | schedulerName={} instanceId={} nodeId={}",
                            properties.getSchedulerName(), instanceId, nodeId);
                    waitBeforeRetry(properties.getBootstrapLockRetryInterval());
                }
            }
            if (!locked) {
                throw new IllegalStateException("Quartz 启动锁等待超时");
            }
            log.info(
                    "取得 Quartz 启动锁 | schedulerName={} instanceId={} nodeId={}",
                    properties.getSchedulerName(), instanceId, nodeId);
            scheduler.getListenerManager().addJobListener(
                    systemJobExecutionListener, jobGroupEquals(SystemJobQuartzSynchronizer.GROUP));
            systemJobs.reconcile();
            schedulePlans.reconcileAll();
            reconciliationSucceeded.set(true);
            failureType = null;
            log.info(
                    "Quartz 调度数据同步完成 | schedulerName={} instanceId={} nodeId={}",
                    properties.getSchedulerName(), instanceId, nodeId);
        } catch (Exception exception) {
            reconciliationSucceeded.set(false);
            failureType = exception.getClass().getName();
            log.error(
                    "Quartz 启动准备失败 | schedulerName={} instanceId={} nodeId={} failureType={}",
                    properties.getSchedulerName(), instanceId, nodeId, failureType, exception);
            return;
        } finally {
            if (locked && connection != null) {
                // PostgreSQL advisory lock 归属于会话，必须在取得锁的同一 Connection 上显式释放。
                try {
                    unlock(connection, lockKey);
                } catch (Exception exception) {
                    log.warn(
                            "Quartz 启动锁显式释放失败，连接关闭后由 PostgreSQL 回收 | schedulerName={} instanceId={} nodeId={}",
                            properties.getSchedulerName(), instanceId, nodeId, exception);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception exception) {
                    log.warn(
                            "Quartz 启动锁连接关闭失败 | schedulerName={} instanceId={} nodeId={}",
                            properties.getSchedulerName(), instanceId, nodeId, exception);
                }
            }
        }

        try {
            scheduler.start();
            log.info(
                    "Quartz 调度器已启动 | schedulerName={} instanceId={} nodeId={}",
                    properties.getSchedulerName(), instanceId, nodeId);
        } catch (SchedulerException exception) {
            reconciliationSucceeded.set(false);
            failureType = exception.getClass().getName();
            log.error(
                    "Quartz 调度器启动失败 | schedulerName={} instanceId={} nodeId={} failureType={}",
                    properties.getSchedulerName(), instanceId, nodeId, failureType, exception);
        }
    }

    /** @return 最近一次启动对账是否成功且之后未发生启动失败 */
    public boolean reconciliationSucceeded() {
        return reconciliationSucceeded.get();
    }

    /** @return 最近一次启动失败类型；未失败时为 null */
    public String failureType() {
        return failureType;
    }

    /**
     * 停止接收新触发，并在配置上限内等待当前节点已经领取的 Job 完成。
     *
     * <p>到达上限后使用非等待关闭，避免容器关闭无限挂起；业务 Job 的租约和 recovery 负责后续收敛。
     */
    @PreDestroy
    public void stopScheduler() {
        try {
            if (scheduler.isShutdown()) {
                return;
            }
            String instanceId = scheduler.getSchedulerInstanceId();
            scheduler.standby();
            Instant deadline = Instant.now().plus(properties.getShutdownWait());
            while (!scheduler.getCurrentlyExecutingJobs().isEmpty() && Instant.now().isBefore(deadline)) {
                Thread.sleep(Duration.ofMillis(100));
            }
            boolean completed = scheduler.getCurrentlyExecutingJobs().isEmpty();
            scheduler.shutdown(completed);
            log.info(
                    "Quartz 调度器已关闭 | schedulerName={} instanceId={} runningJobsCompleted={}",
                    properties.getSchedulerName(), instanceId, completed);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            try {
                scheduler.shutdown(false);
            } catch (SchedulerException shutdownException) {
                exception.addSuppressed(shutdownException);
            }
            log.warn("Quartz 调度器关闭等待被中断 | schedulerName={}", properties.getSchedulerName(), exception);
        } catch (SchedulerException exception) {
            log.warn("Quartz 调度器关闭失败 | schedulerName={}", properties.getSchedulerName(), exception);
        }
    }

    /**
     * 在当前 PostgreSQL 会话上非阻塞尝试取得启动锁。
     *
     * @param connection 启动协调专用且保持打开的 JDBC Connection
     * @param lockKey schedulerName 确定的固定 advisory lock key
     * @return 当前会话取得锁时返回 true
     * @throws Exception JDBC 查询失败时抛出
     */
    private static boolean tryLock(@NotNull Connection connection, long lockKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, lockKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    /**
     * 在取得锁的同一 PostgreSQL 会话上显式释放启动锁。
     *
     * @param connection 当前持有 advisory lock 的 JDBC Connection
     * @param lockKey 获取阶段使用的同一固定 key
     * @throws Exception JDBC 释放查询失败时抛出；Connection 关闭仍会回收会话锁
     */
    private static void unlock(@NotNull Connection connection, long lockKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, lockKey);
            statement.execute();
        }
    }

    /**
     * 按外部化的有界间隔等待下一次非阻塞锁尝试。
     *
     * @param interval 两次 pg_try_advisory_lock 之间的固定等待时长
     * @throws IllegalStateException 当前启动线程被中断时抛出并保留中断标记
     */
    private static void waitBeforeRetry(@NotNull Duration interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Quartz 启动锁等待被中断", exception);
        }
    }

    /**
     * 从集群共用 schedulerName 生成跨节点固定的 64 位 advisory lock key。
     *
     * @param schedulerName 当前数据库内全部 Server 节点共用的名称
     * @return SHA-256 前 64 位形成的 PostgreSQL bigint key
     */
    private static long advisoryLockKey(@NotNull String schedulerName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("sca4d:quartz-bootstrap:" + schedulerName).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256", exception);
        }
    }

}
