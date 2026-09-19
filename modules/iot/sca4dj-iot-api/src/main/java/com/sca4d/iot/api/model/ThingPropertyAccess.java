package com.sca4d.iot.api.model;

/** 表示设备属性允许的平台读写方向。 */
public enum ThingPropertyAccess {
    /** 设备只上报，平台不能设置 desired。 */
    READ_ONLY,
    /** 设备上报且平台可以持续设置 desired。 */
    READ_WRITE
}
