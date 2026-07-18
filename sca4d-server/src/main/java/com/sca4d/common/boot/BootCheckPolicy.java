package com.sca4d.common.boot;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 启动检查策略。 */
public class BootCheckPolicy {

    private boolean enabled = true;
    private DependencyCriticality criticality = DependencyCriticality.REQUIRED;
    private Duration timeout = Duration.ofSeconds(3);

    /** 创建默认启动检查策略。 */
    public BootCheckPolicy() {
    }

    /**
     * 创建启动检查策略。
     *
     * @param enabled 是否启用检查
     * @param criticality 依赖严重等级
     * @param timeout 检查超时时间
     */
    public BootCheckPolicy(boolean enabled, @NotNull DependencyCriticality criticality, @NotNull Duration timeout) {
        this.enabled = enabled;
        this.criticality = criticality;
        setTimeout(timeout);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @NotNull DependencyCriticality getCriticality() {
        return criticality;
    }

    public void setCriticality(@NotNull DependencyCriticality criticality) {
        this.criticality = criticality;
    }

    public @NotNull Duration getTimeout() {
        return timeout;
    }

    /**
     * 设置单项启动检查的最长等待时间。
     *
     * @throws IllegalArgumentException 超时时间不是正数时抛出
     */
    public void setTimeout(@NotNull Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("启动检查超时时间必须大于零");
        }
        this.timeout = timeout;
    }

    /**
     * 创建关键依赖的默认策略。
     *
     * @return 已启用、三秒超时的关键依赖策略
     */
    public static @NotNull BootCheckPolicy required() {
        return new BootCheckPolicy();
    }
}
