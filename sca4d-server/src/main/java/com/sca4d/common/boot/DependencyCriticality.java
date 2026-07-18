package com.sca4d.common.boot;

/** 外部依赖不可用时的启动策略。 */
public enum DependencyCriticality {
    /** 预发布和生产环境中不可用时阻断启动。 */
    REQUIRED,
    /** 允许启动，但 readiness 保持不就绪。 */
    DEGRADED,
    /** 允许启动，仅在健康详情与日志中记录。 */
    OPTIONAL
}
