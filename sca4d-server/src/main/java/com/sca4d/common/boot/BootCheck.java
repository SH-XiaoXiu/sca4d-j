package com.sca4d.common.boot;

import org.jetbrains.annotations.NotNull;

/** 应用启动时执行的单项外部依赖检查。 */
public interface BootCheck {

    /**
     * 获取固定的检查名称。
     *
     * @return 检查名称
     */
    @NotNull
    String name();

    /**
     * 获取当前检查的启用状态、严重等级和超时策略。
     *
     * @return 检查策略
     */
    @NotNull
    BootCheckPolicy policy();

    /**
     * 执行一次依赖可用性检查。
     *
     * @param environment 当前运行环境
     * @return 检查结果
     */
    @NotNull
    BootCheckResult check(@NotNull BootEnvironment environment);
}
