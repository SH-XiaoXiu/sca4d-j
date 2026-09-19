package com.sca4d.core.api.job;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示持久化 Job run 是否排队、运行或已经结束。 */
public enum JobRunStatus implements BaseEnum {
    /** 已保存并等待工作实例领取首个节点。 */
    QUEUED("等待执行"),
    /** 至少一个节点已经开始执行。 */
    RUNNING("执行中"),
    /** Job 最终输出已经保存。 */
    SUCCEEDED("执行成功"),
    /** 必需节点重试耗尽或定义无法恢复。 */
    FAILED("执行失败");

    private final @NotNull String description;

    JobRunStatus(@NotNull String description) {
        this.description = description;
    }

    /** @return 数据库和 JSON 使用的状态编码 */
    @Override
    public @NotNull String value() {
        return name();
    }

    /** @return 状态的中文说明 */
    @Override
    public @NotNull String description() {
        return description;
    }
}
