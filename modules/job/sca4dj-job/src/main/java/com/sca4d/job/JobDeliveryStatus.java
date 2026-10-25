package com.sca4d.job;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示无记录工作份额在 `job_delivery.status` 中的协调状态。 */
public enum JobDeliveryStatus implements BaseEnum {
    /** 份额已创建，等待目标实例代次领取。 */
    READY,
    /** 份额已被目标实例代次领取并持有租约。 */
    RUNNING,
    /** 工作已成功完成。 */
    SUCCEEDED,
    /** 工作失败，或目标实例等待期限已到。 */
    FAILED;

    /** @return 用于持久化的状态值，与枚举名一致 */
    @Override
    public @NotNull String value() {
        return name();
    }

    /** @return 份额状态的中文说明 */
    @Override
    public @NotNull String description() {
        return switch (this) {
            case READY -> "等待领取";
            case RUNNING -> "执行中";
            case SUCCEEDED -> "执行成功";
            case FAILED -> "执行失败";
        };
    }
}
