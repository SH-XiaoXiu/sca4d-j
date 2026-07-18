package com.sca4d.system;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 管理端可观测的 Quartz Trigger 有限状态。 */
public enum SystemJobTriggerState implements BaseEnum {
    /** Quartz 中没有找到对应 Trigger。 */
    NONE,
    /** Trigger 正常等待下一次触发。 */
    NORMAL,
    /** Trigger 已暂停。 */
    PAUSED,
    /** Trigger 已完成且不会再次触发。 */
    COMPLETE,
    /** Trigger 因调度错误停止。 */
    ERROR,
    /** Trigger 对应 Job 正在运行且禁止并发。 */
    BLOCKED;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
