package com.sca4d.iot.emqx.authentication;

import com.sca4d.iot.api.connection.DeviceConnectionRequest;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 把 EMQX 的 CONNECT 和 TLS 字段转换为具体应用安装的统一 Device 连接请求。 */
public interface EmqxDeviceConnectionRequestFactory {

    /**
     * 解释 EMQX 原始字段并创建一种互斥的连接认证输入。
     *
     * @param request EMQX 从 CONNECT 和 TLS 通道提取的字段
     * @return 协议身份固定为 MQTT Client ID 的连接准入请求
     * @throws RuntimeException 应用不接受当前字段组合、字段缺失或认证材料无法解析时抛出
     */
    @CheckReturnValue
    @NotNull
    DeviceConnectionRequest create(@NotNull EmqxAuthenticationRequest request);
}
