package com.sca4d.app.server.iot.request;

import com.sca4d.app.server.iot.IotProfileStatus;
import com.sca4d.core.spring.validation.Req;

/** 接收 Product 或 Device App Profile 的目标启停状态。 */
@SuppressWarnings("unused")
public class IotStatusRequest {

    @Req.NotNull
    private IotProfileStatus status;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotStatusRequest() {
    }

    /** @return `ENABLED` 或 `DISABLED` */
    public IotProfileStatus getStatus() {
        return status;
    }

    /** @param status `ENABLED` 或 `DISABLED` */
    public void setStatus(IotProfileStatus status) {
        this.status = status;
    }
}
