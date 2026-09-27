package com.sca4d.app.server.iot.request;

import com.sca4d.core.spring.validation.Req;
import com.sca4d.iot.api.schedule.DeviceScheduleStatus;

/** 接收按乐观版本暂停或恢复 Device 定时规则的请求。 */
public class IotDeviceScheduleStatusRequest {

    private long version;

    private @Req.NotNull DeviceScheduleStatus status;

    /** @return 调用方读取到的当前版本 */
    public long getVersion() {
        return version;
    }

    /** @param version 调用方读取到的当前版本 */
    public void setVersion(long version) {
        this.version = version;
    }

    /** @return ACTIVE 或 PAUSED */
    public DeviceScheduleStatus getStatus() {
        return status;
    }

    /** @param status ACTIVE 或 PAUSED */
    public void setStatus(DeviceScheduleStatus status) {
        this.status = status;
    }
}
