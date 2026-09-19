package com.sca4d.job;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示 Job 节点的依赖等待、执行和终态。 */
public enum JobNodeStatus implements BaseEnum {
    /** 等待前置节点完成。 */
    WAITING,
    /** 已具备输入并等待领取。 */
    READY,
    /** 当前尝试已由工作实例领取。 */
    RUNNING,
    /** 节点输出已保存。 */
    SUCCEEDED,
    /** 节点已耗尽重试。 */
    FAILED,
    /** 前置节点失败，无法继续执行。 */
    CANCELLED;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return switch (this) {
            case WAITING -> "等待依赖";
            case READY -> "等待领取";
            case RUNNING -> "执行中";
            case SUCCEEDED -> "执行成功";
            case FAILED -> "执行失败";
            case CANCELLED -> "已取消";
        };
    }
}
