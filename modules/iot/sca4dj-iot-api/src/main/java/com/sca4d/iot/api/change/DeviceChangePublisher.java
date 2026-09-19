package com.sca4d.iot.api.change;

import org.jetbrains.annotations.NotNull;

/** 把已提交的 Device 变化交给具体应用选择的通知通道。 */
public interface DeviceChangePublisher {

    /**
     * 发布允许丢失的失效通知；权威状态仍由 IoT 查询接口提供。
     *
     * @param change 已经完成数据库提交的 Device 变化
     */
    void publish(@NotNull DeviceChange change);
}
