package com.sca4d.app.server.iot;

import com.sca4d.app.server.service.IotApplicationService;
import com.sca4d.core.api.scheduling.ClusterTask;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/** 声明 Sca4d Server 自有数据清理任务，不调度 IoT、EMQX 或 Timescale 模块内部任务。 */
@Configuration(proxyBeanMethods = false)
public class IotApplicationScheduling {

    private static final int DEVICE_CREATION_CLEANUP_BATCH_SIZE = 10_000;

    private static final int MAX_DEVICE_CREATION_CLEANUP_BATCHES = 500;

    /**
     * 创建 Device 创建幂等记录的清理计划。
     *
     * @param application 删除 App 自有幂等记录的业务入口
     * @param properties App 声明的清理间隔和批量
     * @return 由 Quartz 集群直接执行且不创建 Job run 的清理计划
     */
    @Bean
    ClusterTaskSchedule deviceCreationRequestCleanupSchedule(
            @NotNull IotApplicationService application,
            @NotNull AppIotProperties properties) {
        properties.validateScheduling();
        return new ClusterTaskSchedule(
                new ScheduleKey("app.iot.device-creation-request.cleanup"),
                1,
                new DeviceCreationRequestCleanupTask(
                        application,
                        DEVICE_CREATION_CLEANUP_BATCH_SIZE),
                new IntervalSchedule(
                        properties.getDeviceCreationCleanupInterval(),
                        properties.getDeviceCreationCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }

    /** 分批删除已经超过重放窗口的 Device 创建幂等记录。 */
    private static final class DeviceCreationRequestCleanupTask implements ClusterTask {

        private static final Logger LOG = LoggerFactory.getLogger(DeviceCreationRequestCleanupTask.class);

        private final @NotNull IotApplicationService application;

        private final int batchSize;

        /**
         * 创建单轮有明确删除上限的 App 数据清理任务。
         *
         * @param application 删除过期 Device 创建幂等记录的业务入口
         * @param batchSize 每批最多删除的记录数
         */
        private DeviceCreationRequestCleanupTask(
                @NotNull IotApplicationService application,
                int batchSize) {
            this.application = Objects.requireNonNull(application, "IoT 应用服务不能为空");
            this.batchSize = batchSize;
        }

        /** 删除多批过期记录；达到单轮上限时把剩余记录留给下一次触发。 */
        @Override
        public void execute() {
            int batches = 0;
            int total = 0;
            int deleted;
            do {
                deleted = application.cleanupExpiredDeviceCreations(batchSize);
                total += deleted;
                batches++;
            } while (deleted == batchSize && batches < MAX_DEVICE_CREATION_CLEANUP_BATCHES);
            if (total > 0) {
                LOG.debug("过期 Device 创建幂等记录已清理 | deleted={} batches={}", total, batches);
            }
            if (deleted == batchSize) {
                LOG.warn(
                        "Device 创建幂等清理达到单轮上限，剩余记录等待下轮处理 | deleted={} batches={}",
                        total,
                        batches);
            }
        }
    }
}
