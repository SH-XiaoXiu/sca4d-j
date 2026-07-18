package com.sca4d.schedule;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 系统 Job 最近一次执行的有限结果。 */
public enum SystemJobResult implements BaseEnum {
    /** 系统 Job 正常完成。 */
    SUCCESS,
    /** 系统 Job 抛出异常。 */
    FAILED;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
