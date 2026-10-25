package com.sca4d.job;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示根运行或节点执行的 Job 类型，写入 `job_run.job_kind` 与 `job_run_node.node_kind`。 */
public enum JobKind implements BaseEnum {
    /** 单个 DirectJob 直接执行的根运行或节点。 */
    DIRECT,
    /** PipelineJob 展开的根运行或节点，节点之间按依赖顺序执行。 */
    PIPELINE;

    /** @return 用于持久化的类型值，与枚举名一致 */
    @Override
    public @NotNull String value() {
        return name();
    }

    /** @return Job 类型的中文说明 */
    @Override
    public @NotNull String description() {
        return switch (this) {
            case DIRECT -> "直接执行";
            case PIPELINE -> "流水线";
        };
    }
}
