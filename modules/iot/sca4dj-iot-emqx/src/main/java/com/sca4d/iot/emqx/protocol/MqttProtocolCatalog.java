package com.sca4d.iot.emqx.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按 code 索引具体应用安装的 MQTT 协议并汇总共享订阅 Topic。 */
public class MqttProtocolCatalog {

    private final @NotNull Map<String, MqttDeviceProtocol> protocols;

    /** @param protocols 具体应用安装的全部 MQTT 设备协议 */
    public MqttProtocolCatalog(@NotNull Collection<MqttDeviceProtocol> protocols) {
        try {
            this.protocols = Objects.requireNonNull(protocols, "MQTT 协议集合不能为空")
                    .stream()
                    .map(protocol -> Objects.requireNonNull(protocol, "MQTT 协议不能为空"))
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    MqttDeviceProtocol::code,
                                    Function.identity()));
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("MQTT 协议 code 重复", exception);
        }
    }

    /** @param code 协议 code @return 匹配 MQTT 协议 @throws IllegalArgumentException 未登记时抛出 */
    public @NotNull MqttDeviceProtocol loadByCode(@NotNull String code) {
        MqttDeviceProtocol protocol = protocols.get(Objects.requireNonNull(code, "MQTT 协议 code 不能为空"));
        if (protocol == null)
            throw new IllegalArgumentException("MQTT 协议未登记: " + code);
        return protocol;
    }

    /** @return 去重后的全部 MQTT 上行 Topic Filter */
    public @NotNull List<String> uplinkTopicFilters() {
        return protocols.values()
                .stream()
                .flatMap(protocol -> protocol.uplinkTopicFilters().stream())
                .distinct()
                .sorted()
                .toList();
    }

    /** @return 当前应用安装的全部 MQTT 设备协议 */
    public @NotNull @Unmodifiable List<MqttDeviceProtocol> protocols() {
        return List.copyOf(protocols.values());
    }
}
