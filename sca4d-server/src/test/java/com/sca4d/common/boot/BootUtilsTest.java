package com.sca4d.common.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

/** 验证启动检查的并发执行、超时、失败汇总和日志输出。 */
class BootUtilsTest {

    @Test
    void shouldBlockRequiredCheckFailureInProductionLikeEnvironment() {
        MockEnvironment environment = environment("prod");
        BootCheck check = check("storage", DependencyCriticality.REQUIRED, () -> BootCheckResult.unavailable("不可用"));
        BootUtils bootUtils = new BootUtils(environment, List.of(check));

        assertThat(BootUtils.getEnv()).isEqualTo(BootEnvironment.PROD);
        assertThat(BootUtils.isProductionLike()).isTrue();
        assertThatThrownBy(() -> bootUtils.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("关键启动检查失败: storage");
    }

    @Test
    void shouldAllowRequiredCheckFailureInLocalEnvironment() throws Exception {
        MockEnvironment environment = environment("local");
        BootCheck check = check("storage", DependencyCriticality.REQUIRED, () -> BootCheckResult.unavailable("不可用"));
        BootUtils bootUtils = new BootUtils(environment, List.of(check));

        bootUtils.run(new DefaultApplicationArguments());

        assertThat(BootUtils.latestResults()).containsEntry("storage", BootCheckResult.unavailable("不可用"));
    }

    @Test
    void shouldReportDegradedFailureAsDownAndOptionalFailureAsDetailOnly() {
        MockEnvironment environment = environment("staging");
        BootCheck degraded = check("minio", DependencyCriticality.DEGRADED, () -> BootCheckResult.unavailable("不可用"));
        BootCheck optional = check("maps", DependencyCriticality.OPTIONAL, () -> BootCheckResult.unavailable("不可用"));
        BootUtils bootUtils = new BootUtils(environment, List.of(degraded, optional));

        assertThat(new BootCheckHealthIndicator(bootUtils, List.of(degraded, optional)).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void shouldKeepOptionalFailureOutOfOverallHealthStatus() {
        MockEnvironment environment = environment("staging");
        BootCheck optional = check("maps", DependencyCriticality.OPTIONAL, () -> BootCheckResult.unavailable("不可用"));
        BootUtils bootUtils = new BootUtils(environment, List.of(optional));

        assertThat(new BootCheckHealthIndicator(bootUtils, List.of(optional)).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void shouldReportTimedOutCheckWithoutBlockingTheRunnerThread() {
        MockEnvironment environment = environment("local");
        BootCheck check = check("slow", DependencyCriticality.OPTIONAL, () -> {
            Thread.sleep(Duration.ofSeconds(1));
            return BootCheckResult.available("完成");
        }, Duration.ofMillis(10));
        BootUtils bootUtils = new BootUtils(environment, List.of(check));

        bootUtils.run(new DefaultApplicationArguments());

        assertThat(BootUtils.latestResults().get("slow")).isEqualTo(BootCheckResult.unavailable("检查超时"));
    }

    /**
     * 创建只启用指定 profile 的测试环境。
     *
     * @param profile 待启用的 Spring profile
     * @return 不读取系统环境变量的可变测试环境
     */
    private static @NotNull MockEnvironment environment(@NotNull String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    /**
     * 创建使用一秒默认超时的启动检查夹具。
     *
     * @param name 检查名称
     * @param criticality 依赖关键等级
     * @param probe 测试探针
     * @return 已启用的启动检查
     */
    private static @NotNull BootCheck check(
            @NotNull String name, @NotNull DependencyCriticality criticality, @NotNull CheckedProbe probe) {
        return check(name, criticality, probe, Duration.ofSeconds(1));
    }

    /**
     * 创建使用指定超时和探针行为的启动检查夹具。
     *
     * @param name 检查名称
     * @param criticality 依赖关键等级
     * @param probe 测试探针
     * @param timeout 检查超时上限
     * @return 已启用的启动检查
     */
    private static @NotNull BootCheck check(
            @NotNull String name,
            @NotNull DependencyCriticality criticality,
            @NotNull CheckedProbe probe,
            @NotNull Duration timeout) {
        return new BootCheck() {
            @Override
            public @NotNull String name() {
                return name;
            }

            @Override
            public @NotNull BootCheckPolicy policy() {
                return new BootCheckPolicy(true, criticality, timeout);
            }

            @Override
            public @NotNull BootCheckResult check(@NotNull BootEnvironment environment) {
                try {
                    return probe.check();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return BootCheckResult.unavailable("检查被中断");
                }
            }
        };
    }

    /** 允许测试探针显式抛出 InterruptedException 的函数接口。 */
    @FunctionalInterface
    private interface CheckedProbe {

        /**
         * 执行一次允许被中断的测试探针。
         *
         * @return 启动检查结果
         * @throws InterruptedException 测试需要模拟探针被中断时抛出
         */
        @NotNull
        BootCheckResult check() throws InterruptedException;
    }
}
