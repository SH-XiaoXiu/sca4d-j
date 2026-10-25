package com.sca4d.core.api.scheduling;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 指定同一计划已有活动 run 时怎样处理新的 fire。 */
public enum OverlapPolicy implements BaseEnum {
    /** 跳过新的 fire，不创建排队 run。 */
    SKIP_IF_RUNNING,
    /** 保存新的运行，等同一工作键下更早的运行结束后再执行。 */
    WAIT,
    /** 每个 fire 都创建独立 run。 */
    ALLOW;

    /** @return 用于传输和持久化的策略值，与枚举名一致 */
    @Override
    public @NotNull String value() {
        return name();
    }

    /** @return 策略的中文说明 */
    @Override
    public @NotNull String description() {
        return switch (this) {
            case SKIP_IF_RUNNING -> "已有运行时跳过";
            case WAIT -> "排队等待";
            case ALLOW -> "允许并行";
        };
    }
}
