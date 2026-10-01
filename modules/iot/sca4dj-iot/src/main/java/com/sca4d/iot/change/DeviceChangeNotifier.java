package com.sca4d.iot.change;

import com.sca4d.core.spring.transaction.TransactionCallbacks;
import com.sca4d.iot.api.change.DeviceChange;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.change.DeviceChangePublisher;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 在事务提交后把 Device 数据失效通知交给具体应用安装的发布器。 */
public class DeviceChangeNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceChangeNotifier.class);

    private final @NotNull List<DeviceChangePublisher> publishers;

    /** 创建允许具体应用不安装任何通知通道的变化通知器。 */
    public DeviceChangeNotifier(@NotNull Collection<DeviceChangePublisher> publishers) {
        this.publishers = List.copyOf(Objects.requireNonNull(publishers, "Device 变化发布器不能为空"));
    }

    /** 事务存在时在提交后发布；Redis 等非事务事实变化时立即发布。 */
    public void changed(
            long deviceId,
            @NotNull Collection<DeviceChangeArea> areas,
            @NotNull Instant occurredAt) {
        DeviceChange change = new DeviceChange(deviceId, areas, occurredAt);
        TransactionCallbacks.afterCommitOrNow(() -> publish(change));
    }

    /** 单独通知一个变化区域。 */
    public void changed(
            long deviceId,
            @NotNull DeviceChangeArea area,
            @NotNull Instant occurredAt) {
        changed(deviceId, List.of(Objects.requireNonNull(area, "Device 变化区域不能为空")), occurredAt);
    }

    /** 逐个调用发布器；一个允许丢失的通知通道失败不影响业务提交和其他通道。 */
    private void publish(@NotNull DeviceChange change) {
        for (DeviceChangePublisher publisher : publishers) {
            try {
                publisher.publish(change);
            } catch (RuntimeException exception) {
                LOG.warn(
                        "Device 失效通知发布失败 | deviceId={} areas={}",
                        change.deviceId(),
                        change.areas(),
                        exception);
            }
        }
    }
}
