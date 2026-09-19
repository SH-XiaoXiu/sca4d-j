package com.sca4d.iot.api.state;

/** 决定 reported 字段组缺失成员是清除还是保留。 */
public enum ReportedMergeMode {
    /** 本次缺少但属于组覆盖范围的属性从 latest 状态清除。 */
    SNAPSHOT,
    /** 本次缺少的属性继续保留原 latest 值。 */
    PATCH
}
