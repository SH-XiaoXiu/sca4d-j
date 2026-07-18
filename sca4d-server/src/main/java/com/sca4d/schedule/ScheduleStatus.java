package com.sca4d.schedule;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Schedule 当前状态；CANCELLED 仅在数据库中表示已经删除。 */
public enum ScheduleStatus implements BaseEnum {
    /** Schedule 可以继续产生新的执行记录。 */
    ACTIVE,
    /** Schedule 暂停产生新的执行记录。 */
    PAUSED,
    /** 一次性 Schedule 已成功执行。 */
    COMPLETED,
    /** 一次性 Schedule 执行失败且不再自动运行。 */
    FAILED,
    /** Schedule 已删除，仅保留数据库记录。 */
    CANCELLED;

    /** @return 当前状态是否禁止任何恢复或修改 */
    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
