package com.sca4d.app.server.iot;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.iot.api.change.DeviceChange;
import com.sca4d.iot.api.change.DeviceChangePublisher;
import com.sca4d.web.realtime.stomp.WebRealtimeProtocol;
import com.sca4d.web.realtime.stomp.WebRealtimeSubscriptionPolicy;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 把 IoT 模块失效通知发布到 App Web Realtime，并授权 Device Topic 订阅。 */
@Component
public class AppIotRealtime implements DeviceChangePublisher, WebRealtimeSubscriptionPolicy {

    private static final String TOPIC_PREFIX = "iot.device.";

    private static final String DESTINATION_PREFIX = WebRealtimeProtocol.TOPIC_DESTINATION_PREFIX + "/" + TOPIC_PREFIX;

    private static final EventType<IotDeviceInvalidation> DEVICE_CHANGED = new EventType<>(
            "IOT/DEVICE/CHANGED",
            1,
            IotDeviceInvalidation.class);

    private static final Permission DEVICES_READ = new Permission(
            new Operation(IotOperations.DEVICES_READ),
            Action.INVOKE);

    private final @NotNull WebRealtimePublisher realtime;

    private final @NotNull PermissionAssessment permissions;

    /** 创建使用 App 权限和 Topic namespace 的 IoT Realtime 适配器。 */
    public AppIotRealtime(
            @NotNull WebRealtimePublisher realtime,
            @NotNull PermissionAssessment permissions) {
        this.realtime = Objects.requireNonNull(realtime, "Web Realtime 发布器不能为空");
        this.permissions = Objects.requireNonNull(permissions, "权限评估器不能为空");
    }

    /** 发布已经提交且允许丢失的 Device 数据失效事件。 */
    @Override
    public void publish(@NotNull DeviceChange change) {
        DeviceChange checked = Objects.requireNonNull(change, "Device 变化不能为空");
        realtime.publish(
                WebRealtimeTarget.topic(topic(checked.deviceId())),
                new Event<>(
                        DEVICE_CHANGED,
                        Long.toString(checked.deviceId()),
                        checked.occurredAt(),
                        new IotDeviceInvalidation(checked)),
                DispatchPolicy.immediate());
    }

    /** 只处理 {@code /topic/iot.device.<deviceId>} 格式的 STOMP destination。 */
    @Override
    public boolean supports(@NotNull String destination) {
        return Objects.requireNonNull(destination, "STOMP destination 不能为空")
                .startsWith(DESTINATION_PREFIX);
    }

    /** 检查 `iot:devices:read` 后登记不包含 Spring broker 前缀的共享 Topic。 */
    @Override
    @CheckReturnValue
    public @NotNull WebRealtimeTarget authorize(
            @NotNull Call call,
            @NotNull String destination) {
        if (!permissions.assess(
                Objects.requireNonNull(call, "Realtime 调用不能为空"),
                DEVICES_READ)
                .isAllowed()) {
            throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
        }
        String value = Objects.requireNonNull(destination, "STOMP destination 不能为空")
                .substring(DESTINATION_PREFIX.length());
        try {
            long deviceId = Long.parseLong(value);
            if (deviceId <= 0 || !value.equals(Long.toString(deviceId))) {
                throw new NumberFormatException("Device 标识不是规范正整数");
            }
            return WebRealtimeTarget.topic(topic(deviceId));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("IoT Realtime destination 不合法", exception);
        }
    }

    /** 返回 App 共享索引使用的 Device Topic。 */
    private static @NotNull String topic(long deviceId) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Realtime Device 标识必须为正数");
        }
        return TOPIC_PREFIX + deviceId;
    }
}
