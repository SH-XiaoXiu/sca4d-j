package com.sca4d.gateway.discovery;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * upstream 实例健康状态。
 */
@Component
public class GatewayUpstreamHealthState {

    private final ConcurrentMap<String, InstanceHealth> states = new ConcurrentHashMap<>();

    /**
     * 记录一次健康检查结果。
     *
     * @param key 实例健康状态键
     * @param success 是否成功
     * @param healthyThreshold 连续成功阈值
     * @param unhealthyThreshold 连续失败阈值
     * @return 健康状态变化
     * @throws IllegalArgumentException 任一阈值不大于零时抛出
     */
    public @NotNull HealthChange record(
            @NotNull String key, boolean success, int healthyThreshold, int unhealthyThreshold) {
        if (healthyThreshold <= 0 || unhealthyThreshold <= 0) {
            throw new IllegalArgumentException("健康状态转换阈值必须大于 0");
        }
        return states.computeIfAbsent(key, ignored -> new InstanceHealth())
                .record(success, healthyThreshold, unhealthyThreshold);
    }

    /**
     * 禁用实例健康状态。
     *
     * @param key 实例健康状态键
     */
    public void disable(@NotNull String key) {
        states.computeIfAbsent(key, ignored -> new InstanceHealth()).disable();
    }

    /**
     * 判断实例是否健康。
     *
     * @param key 实例健康状态键
     * @return true 表示健康
     */
    public boolean healthy(@NotNull String key) {
        InstanceHealth health = states.get(key);
        return health != null && health.healthy();
    }

    /**
     * 删除当前 upstream 快照已经不再引用的健康状态。
     *
     * <p>配置热更新可能持续替换实例地址；裁剪旧键可以避免注册表长期运行时积累无效状态。
     *
     * @param activeKeys 新快照中仍有效的全部实例健康键
     */
    public void retain(@NotNull Set<String> activeKeys) {
        states.keySet().removeIf(key -> !activeKeys.contains(key));
    }

    /**
     * 健康状态变化。
     */
    public enum HealthChange {
        /** 本次检查没有改变实例健康状态。 */
        UNCHANGED,
        /** 连续成功次数达到阈值，实例恢复为健康。 */
        BECAME_HEALTHY,
        /** 连续失败次数达到阈值，实例变为不健康。 */
        BECAME_UNHEALTHY
    }

    /** 保存单个 upstream 实例的连续成功次数、连续失败次数和当前健康状态。 */
    private static final class InstanceHealth {

        private int successCount;

        private int failureCount;

        private boolean healthy;

        /**
         * 在同一实例锁内累计一次检查结果并计算健康状态是否改变。
         *
         * @param success 本次检查是否成功
         * @param healthyThreshold 连续成功后恢复健康的阈值
         * @param unhealthyThreshold 连续失败后摘除的阈值
         * @return 本次检查引起的健康状态变化
         */
        private synchronized @NotNull HealthChange record(
                boolean success, int healthyThreshold, int unhealthyThreshold) {
            boolean before = healthy;
            if (success) {
                successCount++;
                failureCount = 0;
                if (successCount >= healthyThreshold) {
                    healthy = true;
                }
            } else {
                failureCount++;
                successCount = 0;
                if (failureCount >= unhealthyThreshold) {
                    healthy = false;
                }
            }
            if (!before && healthy) {
                return HealthChange.BECAME_HEALTHY;
            }
            if (before && !healthy) {
                return HealthChange.BECAME_UNHEALTHY;
            }
            return HealthChange.UNCHANGED;
        }

        /**
         * 清空累计结果并将实例立即标记为不可路由。
         */
        private synchronized void disable() {
            successCount = 0;
            failureCount = 0;
            healthy = false;
        }

        /**
         * 在实例锁内读取当前健康状态。
         *
         * @return 当前实例是否允许进入健康候选集
         */
        private synchronized boolean healthy() {
            return healthy;
        }
    }
}
