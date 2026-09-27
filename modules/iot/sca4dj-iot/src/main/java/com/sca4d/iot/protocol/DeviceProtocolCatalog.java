package com.sca4d.iot.protocol;

import com.sca4d.iot.api.protocol.DeviceProtocol;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按稳定 code 索引一个 App 安装的设备协议。 */
public class DeviceProtocolCatalog {

    private final @NotNull Map<String, DeviceProtocol> protocols;

    /** @param protocols App 安装的全部设备协议 */
    public DeviceProtocolCatalog(@NotNull Collection<DeviceProtocol> protocols) {
        try {
            this.protocols = Objects.requireNonNull(protocols, "设备协议集合不能为空")
                    .stream()
                    .map(protocol -> Objects.requireNonNull(protocol, "设备协议不能为空"))
                    .collect(Collectors.toUnmodifiableMap(DeviceProtocol::code, Function.identity()));
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("设备协议 code 重复", exception);
        }
    }

    /** @param code 协议 code @return 匹配协议 @throws IllegalArgumentException 没有登记时抛出 */
    public @NotNull DeviceProtocol loadByCode(@NotNull String code) {
        DeviceProtocol protocol = protocols.get(Objects.requireNonNull(code, "设备协议 code 不能为空"));
        if (protocol == null)
            throw new IllegalArgumentException("设备协议未登记: " + code);
        return protocol;
    }

    /** @return 按稳定 code 排列的全部已安装协议 */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<DeviceProtocol> findAll() {
        return protocols.values()
                .stream()
                .sorted(Comparator.comparing(DeviceProtocol::code))
                .toList();
    }
}
