package com.sca4d.iot.emqx.transport;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 返回当前进程 MQTT 上行队列和累计处理结果，供健康检查和运行排障使用。 */
@SuppressWarnings("ClassCanBeRecord") // 运行状态类型遵守项目禁用 record 的规则。
public final class MqttInboundStatus {

    private final int pending;

    private final int retrying;

    private final long processed;

    private final long rejected;

    private final long quarantined;

    private final long unacknowledged;

    private final long overloaded;

    private final long acknowledgementFailures;

    /** 创建一份不可变的 MQTT 上行处理状态快照。 */
    public MqttInboundStatus(
            int pending,
            int retrying,
            long processed,
            long rejected,
            long quarantined,
            long unacknowledged,
            long overloaded,
            long acknowledgementFailures) {
        this.pending = pending;
        this.retrying = retrying;
        this.processed = processed;
        this.rejected = rejected;
        this.quarantined = quarantined;
        this.unacknowledged = unacknowledged;
        this.overloaded = overloaded;
        this.acknowledgementFailures = acknowledgementFailures;
    }

    /** @return 尚在分片队列、处理中或等待再次处理的消息数量 */
    @Contract(pure = true)
    public int pending() {
        return pending;
    }

    /** @return 当前正在等待下一次处理的消息数量 */
    @Contract(pure = true)
    public int retrying() {
        return retrying;
    }

    /** @return 当前进程启动后处理完成并调用 ACK 的累计消息数量 */
    @Contract(pure = true)
    public long processed() {
        return processed;
    }

    /** @return 当前进程启动后明确拒绝并调用 ACK 的累计消息数量 */
    @Contract(pure = true)
    public long rejected() {
        return rejected;
    }

    /** @return 当前进程启动后可靠保存失败并调用 ACK 的累计消息数量 */
    @Contract(pure = true)
    public long quarantined() {
        return quarantined;
    }

    /** @return 因最终失败没有可靠保存而保持未确认的累计消息数量 */
    @Contract(pure = true)
    public long unacknowledged() {
        return unacknowledged;
    }

    /** @return 因本地分片队列已满而未进入处理的累计消息数量 */
    @Contract(pure = true)
    public long overloaded() {
        return overloaded;
    }

    /** @return 业务处理结束后调用 MQTT ACK 失败的累计消息数量 */
    @Contract(pure = true)
    public long acknowledgementFailures() {
        return acknowledgementFailures;
    }

    /** @return 尚未启动或已经停止的 MQTT 上行处理状态 */
    @Contract(pure = true)
    public static @NotNull MqttInboundStatus stopped() {
        return new MqttInboundStatus(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
