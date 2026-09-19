package com.sca4d.iot.api.connection;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 检查 Device、Account 和一种连接认证输入后决定是否允许建立协议连接。 */
public interface DeviceConnectionAdmission {

    /**
     * 验证请求中的公开 Device 身份、接入方式和对应 Account 认证或 Session。
     *
     * @param request 协议边界已经转换完成的连接准入请求
     * @return 允许接入的 Device、实际接入方式、可选 Session 和最晚有效时间
     * @throws RuntimeException Device、身份、Account、Credential 或 Session 不可用时抛出
     */
    @CheckReturnValue
    @NotNull
    DeviceConnectionAccess admit(@NotNull DeviceConnectionRequest request);
}
