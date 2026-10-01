package com.sca4d.iot.api.change;

/** 表示 Device 发生变化后需要重新读取的稳定数据区域。 */
public enum DeviceChangeArea {
    /** Device 基础资料、绑定、接入方式或具体应用合并展示的 Device Profile。 */
    DEVICE,
    /** 当前设备连接信息。 */
    CONNECTION,
    /** 当前 reported state。 */
    REPORTED,
    /** 当前 desired state。 */
    DESIRED,
    /** 下行消息及其业务回执。 */
    DOWNLINK,
    /** 离散设备事件。 */
    EVENT,
    /** 设备定时规则及执行结果。 */
    SCHEDULE
}
