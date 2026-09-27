package com.sca4d.iot.emqx.protocol;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 按 desired property 或 command code 选择下行 encoder 和 Topic 模板。 */
public class MqttDownlinkRoute {

    private final @NotNull DeviceDownlinkKind kind;

    private final @NotNull String targetCode;

    private final @NotNull String topicTemplate;

    private MqttDownlinkRoute(
            @NotNull DeviceDownlinkKind kind,
            @NotNull String targetCode,
            @NotNull String topicTemplate) {
        this.kind = kind;
        this.targetCode = TextUtils.requireText(targetCode, "MQTT 下行目标 code");
        this.topicTemplate = TextUtils.requireText(topicTemplate, "MQTT 下行 Topic 模板");
        if (!topicTemplate.contains("{identity}") || topicTemplate.contains("#") || topicTemplate.contains("+")) {
            throw new IllegalArgumentException("下行 Topic 模板必须包含 {identity} 且不能包含通配符");
        }
    }

    /** 使用默认的 desired Topic 模板。 */
    @CheckReturnValue
    public static @NotNull MqttDownlinkRoute desired(@NotNull String propertyCode) {
        String code = TextUtils.requireText(propertyCode, "desired property code");
        return desired(code, "sca4dj/devices/{identity}/desired/" + code);
    }

    /** @return 使用明确 Topic 模板的 desired 路由 */
    @CheckReturnValue
    public static @NotNull MqttDownlinkRoute desired(
            @NotNull String propertyCode,
            @NotNull String topicTemplate) {
        return new MqttDownlinkRoute(DeviceDownlinkKind.DESIRED, propertyCode, topicTemplate);
    }

    /** 使用默认的 command Topic 模板。 */
    @CheckReturnValue
    public static @NotNull MqttDownlinkRoute command(@NotNull String commandCode) {
        String code = TextUtils.requireText(commandCode, "command code");
        return command(code, "sca4dj/devices/{identity}/commands/" + code);
    }

    /** @return 使用明确 Topic 模板的 command 路由 */
    @CheckReturnValue
    public static @NotNull MqttDownlinkRoute command(
            @NotNull String commandCode,
            @NotNull String topicTemplate) {
        return new MqttDownlinkRoute(DeviceDownlinkKind.COMMAND, commandCode, topicTemplate);
    }

    /** @return desired 或 command */
    public @NotNull DeviceDownlinkKind kind() {
        return kind;
    }

    /** @return 属性或命令 code */
    public @NotNull String targetCode() {
        return targetCode;
    }

    /** @return 包含 `{identity}` 的 Topic 模板 */
    public @NotNull String topicTemplate() {
        return topicTemplate;
    }
}
