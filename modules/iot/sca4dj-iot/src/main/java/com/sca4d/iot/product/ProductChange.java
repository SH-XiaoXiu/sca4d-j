package com.sca4d.iot.product;

import com.sca4d.iot.api.DeviceAccessMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

/** 保存一次 Product 技术定义修改中必须共同校验的接入方式和协议集合。 */
public final class ProductChange {

    private final @NotNull Set<DeviceAccessMethod> accessMethods;

    private final @NotNull Set<String> protocolCodes;

    /** 创建完整替换 Product 技术定义的数据。 */
    public ProductChange(
            @NotNull Set<DeviceAccessMethod> accessMethods,
            @NotNull Set<String> protocolCodes) {
        this.accessMethods = Set.copyOf(Objects.requireNonNull(accessMethods, "Product 接入方式不能为空"));
        this.protocolCodes = Set.copyOf(Objects.requireNonNull(protocolCodes, "Product 协议不能为空"));
    }

    /** @return 完整允许接入方式 */
    public @NotNull Set<DeviceAccessMethod> accessMethods() {
        return accessMethods;
    }

    /** @return 完整兼容协议 code */
    public @NotNull Set<String> protocolCodes() {
        return protocolCodes;
    }
}
