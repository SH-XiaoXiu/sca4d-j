package com.sca4d.job;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示 Job 节点一次尝试的执行结果，与节点是否继续重试分别保存。 */
public enum JobAttemptStatus implements BaseEnum {
    /** 工作实例仍在执行本次尝试。 */
    RUNNING,
    /** 本次尝试完成。 */
    SUCCEEDED,
    /** 本次尝试失败。 */
    FAILED,
    /** 租约失效后已由其他尝试接管。 */
    ABANDONED;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return switch (this) {
            case RUNNING -> "执行中";
            case SUCCEEDED -> "执行成功";
            case FAILED -> "执行失败";
            case ABANDONED -> "租约已失效";
        };
    }
}
