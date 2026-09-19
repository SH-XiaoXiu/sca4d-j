package com.sca4d.iot.connection;

import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionTerminator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按 `DeviceConnectionProtocol` 选择唯一的设备连接终止实现。 */
public final class DeviceConnectionTerminatorRouter {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceConnectionTerminatorRouter.class);

    private final @NotNull Map<DeviceConnectionProtocol, DeviceConnectionTerminator> terminators;

    /** @param terminators 具体设备接入模块登记的全部连接终止实现 */
    public DeviceConnectionTerminatorRouter(
            @NotNull Collection<DeviceConnectionTerminator> terminators) {
        try {
            this.terminators = Objects.requireNonNull(terminators, "连接终止实现集合不能为空")
                    .stream()
                    .map(value -> Objects.requireNonNull(value, "连接终止实现不能为空"))
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    DeviceConnectionTerminator::protocol,
                                    Function.identity()));
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("同一 DeviceConnectionProtocol 登记了多个终止实现", exception);
        }
    }

    /**
     * @param protocol 待终止连接使用的协议
     * @param connectionId 待终止的明确连接代次
     * @param reason 不包含凭据和隐私数据的稳定终止原因
     * @throws IllegalStateException 没有该协议的终止实现
     */
    public void disconnect(
            @NotNull DeviceConnectionProtocol protocol,
            @NotNull DeviceConnectionId connectionId,
            @NotNull String reason) {
        DeviceConnectionTerminator terminator = terminators.get(
                Objects.requireNonNull(protocol, "DeviceConnectionProtocol 不能为空"));
        if (terminator == null) {
            throw new IllegalStateException("DeviceConnectionProtocol 没有终止实现: " + protocol.code());
        }
        LOG.trace(
                "选择 Device Connection 终止实现 | protocol={} connectionId={} reason={}",
                protocol.code(),
                connectionId.value(),
                reason);
        terminator.disconnect(
                Objects.requireNonNull(connectionId, "DeviceConnectionId 不能为空"),
                Objects.requireNonNull(reason, "连接终止原因不能为空"));
    }
}
