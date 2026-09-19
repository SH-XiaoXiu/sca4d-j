package com.sca4d.iot.api.connection;

import org.jetbrains.annotations.NotNull;

/** 通过一种设备接入实现幂等终止它持有或代理的连接。 */
public interface DeviceConnectionTerminator {

    /** @return 当前实现能够终止的连接协议 */
    @NotNull
    DeviceConnectionProtocol protocol();

    /**
     * @param connectionId 待终止的明确连接代次
     * @param reason 不包含凭据和隐私数据的稳定终止原因
     * @throws RuntimeException 接入实现暂时不可用且调用方应保留任务重试时抛出
     */
    void disconnect(@NotNull DeviceConnectionId connectionId, @NotNull String reason);
}
