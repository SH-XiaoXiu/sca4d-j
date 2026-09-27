package com.sca4d.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 配置 RabbitMQ 发布确认以及 Outbox 派发、租约、退避和清理。 */
@ConfigurationProperties("sca4dj.messaging")
public class MessagingProperties {

    private Duration confirmTimeout = Duration.ofSeconds(10);

    private Duration dispatchInterval = Duration.ofSeconds(1);

    private int dispatchBatchSize = 100;

    private Duration leaseDuration = Duration.ofMinutes(1);

    private Duration initialBackoff = Duration.ofSeconds(1);

    private Duration maximumBackoff = Duration.ofMinutes(5);

    private Duration sentRetention = Duration.ofDays(7);

    private Duration cleanupInterval = Duration.ofHours(1);

    private int cleanupBatchSize = 500;

    /** 创建使用默认发布确认、派发和清理参数的配置。 */
    public MessagingProperties() {
    }

    /**
     * 返回等待 RabbitMQ publisher confirm 的最长时间。
     *
     * @return 等待 RabbitMQ publisher confirm 的最长时间
     */
    public Duration getConfirmTimeout() {
        return confirmTimeout;
    }

    /**
     * 修改 publisher confirm 超时。
     *
     * @param confirmTimeout 等待 publisher confirm 的正数时长
     * @throws IllegalArgumentException 时长为空、非正数、短于 1 毫秒或毫秒值溢出时抛出
     */
    public void setConfirmTimeout(Duration confirmTimeout) {
        this.confirmTimeout = positiveMillis(confirmTimeout, "消息发布确认超时");
    }

    /**
     * 返回相邻两次 Outbox 派发计划之间的时间。
     *
     * @return 相邻两次 Outbox 派发扫描之间的时间
     */
    public Duration getDispatchInterval() {
        return dispatchInterval;
    }

    /**
     * 修改 Outbox 派发计划的提交间隔。
     *
     * @param dispatchInterval Outbox 派发扫描的正数间隔
     * @throws IllegalArgumentException 时长为空或不是正数时抛出
     */
    public void setDispatchInterval(Duration dispatchInterval) {
        this.dispatchInterval = positive(dispatchInterval, "Outbox 派发间隔");
    }

    /**
     * 返回每次最多领取的 Outbox 记录数。
     *
     * @return 每次最多领取的 Outbox 记录数
     */
    public int getDispatchBatchSize() {
        return dispatchBatchSize;
    }

    /**
     * 修改单次 Outbox 领取上限。
     *
     * @param dispatchBatchSize 每次领取的正数记录上限
     * @throws IllegalArgumentException 上限不是正数时抛出
     */
    public void setDispatchBatchSize(int dispatchBatchSize) {
        this.dispatchBatchSize = positive(dispatchBatchSize, "Outbox 派发批量");
    }

    /**
     * 返回 Outbox 处于 SENDING 时的租约时长。
     *
     * @return Outbox 处于 SENDING 时的租约时长
     */
    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    /**
     * 修改 Outbox SENDING 租约时长。
     *
     * @param leaseDuration Outbox SENDING 租约的正数时长
     * @throws IllegalArgumentException 时长为空、非正数、短于 1 毫秒或毫秒值溢出时抛出
     */
    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = positiveMillis(leaseDuration, "Outbox 租约时长");
    }

    /**
     * 返回第一次发布失败后的退避时长。
     *
     * @return 第一次发布失败后的退避时长
     */
    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    /**
     * 修改第一次发布失败后的退避时长。
     *
     * @param initialBackoff 第一次发布失败后的正数退避时长
     * @throws IllegalArgumentException 时长为空、非正数、短于 1 毫秒或毫秒值溢出时抛出
     */
    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = positiveMillis(initialBackoff, "Outbox 初始退避");
    }

    /**
     * 返回发布失败退避时长的上限。
     *
     * @return 发布失败退避时长的上限
     */
    public Duration getMaximumBackoff() {
        return maximumBackoff;
    }

    /**
     * 修改发布失败退避时长的上限。
     *
     * @param maximumBackoff 发布失败退避时长的正数上限
     * @throws IllegalArgumentException 时长为空、非正数、短于 1 毫秒或毫秒值溢出时抛出
     */
    public void setMaximumBackoff(Duration maximumBackoff) {
        this.maximumBackoff = positiveMillis(maximumBackoff, "Outbox 最大退避");
    }

    /**
     * 返回 SENT Outbox 记录的保留时间。
     *
     * @return SENT Outbox 记录的保留时间
     */
    public Duration getSentRetention() {
        return sentRetention;
    }

    /**
     * 修改 SENT Outbox 记录的保留时间。
     *
     * @param sentRetention SENT Outbox 记录的正数保留时间
     * @throws IllegalArgumentException 时长为空、非正数、短于 1 毫秒或毫秒值溢出时抛出
     */
    public void setSentRetention(Duration sentRetention) {
        this.sentRetention = positiveMillis(sentRetention, "Outbox 已发送保留时间");
    }

    /**
     * 返回相邻两次 SENT Outbox 清理计划之间的间隔。
     *
     * @return 相邻两次 SENT Outbox 清理之间的时间
     */
    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    /**
     * 修改 SENT Outbox 清理计划的提交间隔。
     *
     * @param cleanupInterval Outbox 清理计划使用的正数间隔
     * @throws IllegalArgumentException 时长为空或不是正数时抛出
     */
    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = positive(cleanupInterval, "Outbox 清理间隔");
    }

    /**
     * 返回每次最多删除的 SENT Outbox 记录数。
     *
     * @return 每次最多删除的 SENT Outbox 记录数
     */
    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    /**
     * 修改单次 SENT Outbox 删除上限。
     *
     * @param cleanupBatchSize 每次删除的正数记录上限
     * @throws IllegalArgumentException 上限不是正数时抛出
     */
    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = positive(cleanupBatchSize, "Outbox 清理批量");
    }

    /** 校验 Duration 参数是正数。 */
    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return value;
    }

    /** 校验时长转换为毫秒后仍是正数且没有溢出。 */
    private static Duration positiveMillis(Duration value, String name) {
        Duration checked = positive(value, name);
        try {
            if (checked.toMillis() < 1) {
                throw new IllegalArgumentException(name + "不能短于 1 毫秒");
            }
            return checked;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + "超过毫秒表示范围", exception);
        }
    }

    /** 校验 int 参数是正数。 */
    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return value;
    }
}
