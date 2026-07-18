package com.sca4d.config.properties;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 分布式锁、Bucket4j 集群限流和 Redis 幂等保护配置根对象。 */
@ConfigurationProperties(prefix = "sca4dj.concurrent")
public class Sca4djConcurrentProperties {

    private Lock lock = new Lock();
    private RateLimit rateLimit = new RateLimit();
    private Idempotency idempotency = new Idempotency();

    /** @return 分布式锁开关、key 前缀、等待时间和租约策略 */
    public @NotNull Lock getLock() {
        return lock;
    }

    /** @param lock 完整分布式锁配置组 */
    public void setLock(@NotNull Lock lock) {
        this.lock = lock;
    }

    /** @return Bucket4j 集群限流默认规则和 Redis 状态过期配置 */
    public @NotNull RateLimit getRateLimit() {
        return rateLimit;
    }

    /** @param rateLimit 完整集群限流配置组 */
    public void setRateLimit(@NotNull RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    /** @return Redis 幂等处理租约、成功重放窗口和重试配置 */
    public @NotNull Idempotency getIdempotency() {
        return idempotency;
    }

    /** @param idempotency 完整 Redis 幂等配置组 */
    public void setIdempotency(@NotNull Idempotency idempotency) {
        this.idempotency = idempotency;
    }

    /** 分布式锁开关、命名空间、等待时间和 watchdog/fixed lease 选择。 */
    public static class Lock {

        private boolean enabled = true;
        private String keyPrefix = "lock";
        private Duration waitTime = Duration.ofSeconds(5);
        private Duration leaseTime = Duration.ZERO;

        /** @return 是否启用声明式分布式锁 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用声明式分布式锁 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return Redisson 锁名称共用前缀 */
        public @NotNull String getKeyPrefix() {
            return keyPrefix;
        }

        /**
         * 设置 Redisson 锁名称共用前缀。
         *
         * @param keyPrefix 用于区分应用和环境的有效文本
         * @throws IllegalArgumentException 前缀为空白时抛出
         */
        public void setKeyPrefix(@NotNull String keyPrefix) {
            if (keyPrefix.isBlank()) {
                throw new IllegalArgumentException("分布式锁 key 前缀不能为空");
            }
            this.keyPrefix = keyPrefix.trim();
        }

        /** @return 尝试获得锁的最长等待时间，零表示不等待 */
        public @NotNull Duration getWaitTime() {
            return waitTime;
        }

        /**
         * @param waitTime 尝试获得锁的非负等待时间
         * @throws IllegalArgumentException 等待时间为负数时抛出
         */
        public void setWaitTime(@NotNull Duration waitTime) {
            if (waitTime.isNegative()) {
                throw new IllegalArgumentException("分布式锁等待时间不能为负数");
            }
            this.waitTime = waitTime;
        }

        /** @return 固定租约时间，零表示使用 Redisson watchdog 自动续租 */
        public @NotNull Duration getLeaseTime() {
            return leaseTime;
        }

        /**
         * @param leaseTime 非负租约时间；零启用 watchdog，正数使用固定租约
         * @throws IllegalArgumentException 租约时间为负数时抛出
         */
        public void setLeaseTime(@NotNull Duration leaseTime) {
            if (leaseTime.isNegative()) {
                throw new IllegalArgumentException("分布式锁租约时间不能为负数");
            }
            this.leaseTime = leaseTime;
        }
    }

    /** Bucket4j 集群令牌桶默认规则和 Redis 状态保留时间。 */
    public static class RateLimit {

        private boolean enabled = true;
        private String keyPrefix = "rate-limit";
        private long capacity = 60;
        private long refillTokens = 60;
        private Duration timeWindow = Duration.ofMinutes(1);
        private Duration keepAfterRefill = Duration.ofMinutes(5);

        /** @return 是否启用声明式 Bucket4j 集群限流 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用声明式 Bucket4j 集群限流 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return Redis Bucket4j 状态共用 key 前缀 */
        public @NotNull String getKeyPrefix() {
            return keyPrefix;
        }

        /**
         * @param keyPrefix 用于区分应用和环境的有效文本
         * @throws IllegalArgumentException 前缀为空白时抛出
         */
        public void setKeyPrefix(@NotNull String keyPrefix) {
            if (keyPrefix.isBlank()) {
                throw new IllegalArgumentException("限流 key 前缀不能为空");
            }
            this.keyPrefix = keyPrefix.trim();
        }

        /** @return 未被注解覆盖时的令牌桶最大容量 */
        public long getCapacity() {
            return capacity;
        }

        /**
         * @param capacity 正数令牌桶最大容量
         * @throws IllegalArgumentException 容量不是正数时抛出
         */
        public void setCapacity(long capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("限流容量必须大于 0");
            }
            this.capacity = capacity;
        }

        /** @return 每个补充窗口增加的令牌数 */
        public long getRefillTokens() {
            return refillTokens;
        }

        /**
         * @param refillTokens 每个窗口补充的正数令牌数
         * @throws IllegalArgumentException 补充令牌数不是正数时抛出
         */
        public void setRefillTokens(long refillTokens) {
            if (refillTokens <= 0) {
                throw new IllegalArgumentException("限流补充令牌数必须大于 0");
            }
            this.refillTokens = refillTokens;
        }

        /** @return 令牌贪婪补充使用的时间窗口 */
        public @NotNull Duration getTimeWindow() {
            return timeWindow;
        }

        /**
         * @param timeWindow 正数令牌补充窗口
         * @throws IllegalArgumentException 窗口不是正数时抛出
         */
        public void setTimeWindow(@NotNull Duration timeWindow) {
            if (timeWindow.isZero() || timeWindow.isNegative()) {
                throw new IllegalArgumentException("限流窗口必须大于 0");
            }
            this.timeWindow = timeWindow;
        }

        /** @return 令牌补满后继续保留 Redis Bucket 状态的时间 */
        public @NotNull Duration getKeepAfterRefill() {
            return keepAfterRefill;
        }

        /**
         * @param keepAfterRefill 令牌补满后的非负 Redis 状态保留时间
         * @throws IllegalArgumentException 保留时间为负数时抛出
         */
        public void setKeepAfterRefill(@NotNull Duration keepAfterRefill) {
            if (keepAfterRefill.isNegative()) {
                throw new IllegalArgumentException("限流状态保留时间不能为负数");
            }
            this.keepAfterRefill = keepAfterRefill;
        }
    }

    /** Redis 幂等所有权租约、成功重放窗口和客户端重试建议。 */
    public static class Idempotency {

        private boolean enabled = true;
        private String keyPrefix = "idempotency";
        private Duration processingTtl = Duration.ofMinutes(5);
        private Duration successTtl = Duration.ofHours(24);
        private Duration retryAfter = Duration.ofSeconds(3);

        /** @return 是否启用方法级集群幂等保护 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用方法级集群幂等保护 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return Redis 幂等记录共用 key 前缀 */
        public @NotNull String getKeyPrefix() {
            return keyPrefix;
        }

        /**
         * 设置 Redis 幂等记录共用 key 前缀。
         *
         * @param keyPrefix 用于区分应用和环境的有效文本
         * @throws IllegalArgumentException 前缀为空白时抛出
         */
        public void setKeyPrefix(@NotNull String keyPrefix) {
            if (keyPrefix.isBlank()) {
                throw new IllegalArgumentException("幂等 key 前缀不能为空");
            }
            this.keyPrefix = keyPrefix.trim();
        }

        /**
         * 返回首次执行拥有 Redis 所有权的最长时间。
         *
         * @return 应大于受保护业务最大预期执行时长的处理租约
         */
        public @NotNull Duration getProcessingTtl() {
            return processingTtl;
        }

        /**
         * 设置首次执行处理租约。
         *
         * <p>当前实现不续租；该值必须覆盖受保护业务的最大预期执行时长，否则租约过期后另一节点可以重新执行同一意图。
         *
         * @param processingTtl 正数处理租约
         * @throws IllegalArgumentException 租约不是正数时抛出
         */
        public void setProcessingTtl(@NotNull Duration processingTtl) {
            if (processingTtl.isZero() || processingTtl.isNegative()) {
                throw new IllegalArgumentException("幂等处理租约必须大于 0");
            }
            this.processingTtl = processingTtl;
        }

        /** @return 成功响应允许重放的保留时间 */
        public @NotNull Duration getSuccessTtl() {
            return successTtl;
        }

        /**
         * 设置成功响应保留时间。
         *
         * @param successTtl 正数重放窗口
         * @throws IllegalArgumentException 重放窗口不是正数时抛出
         */
        public void setSuccessTtl(@NotNull Duration successTtl) {
            if (successTtl.isZero() || successTtl.isNegative()) {
                throw new IllegalArgumentException("幂等成功记录 TTL 必须大于 0");
            }
            this.successTtl = successTtl;
        }

        /** @return 原请求仍在执行时返回客户端的建议重试等待时间 */
        public @NotNull Duration getRetryAfter() {
            return retryAfter;
        }

        /**
         * 设置处理中冲突的建议重试时间。
         *
         * @param retryAfter 正数等待时间
         * @throws IllegalArgumentException 等待时间不是正数时抛出
         */
        public void setRetryAfter(@NotNull Duration retryAfter) {
            if (retryAfter.isZero() || retryAfter.isNegative()) {
                throw new IllegalArgumentException("幂等重试等待时间必须大于 0");
            }
            this.retryAfter = retryAfter;
        }
    }
}
