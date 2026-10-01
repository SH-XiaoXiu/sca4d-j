package com.sca4d.iot.product;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iot.api.DeviceAccessMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** 保存创建 Product 时总是一起校验和写入的数据。 */
public class ProductRegistration {

    private final @NotNull LocalizedFields i18n;

    private final @NotNull Set<DeviceAccessMethod> accessMethods;

    private final @NotNull Set<String> protocolCodes;

    private final @NotNull String code;

    /** @param code Product code @param i18n 完整多语言展示字段 @param accessMethods 允许接入方式 @param protocolCodes 兼容协议 */
    public ProductRegistration(
            @NotNull String code,
            @NotNull LocalizedFields i18n,
            @NotNull Set<DeviceAccessMethod> accessMethods,
            @NotNull Set<String> protocolCodes) {
        this.code = code;
        this.i18n = i18n;
        this.accessMethods = Set.copyOf(accessMethods);
        this.protocolCodes = Set.copyOf(protocolCodes);
    }

    /** @return Product code */
    public @NotNull String code() {
        return code;
    }

    /** @return 完整多语言展示字段 */
    public @NotNull LocalizedFields i18n() {
        return i18n;
    }

    /** @return 允许接入方式 */
    public @NotNull Set<DeviceAccessMethod> accessMethods() {
        return accessMethods;
    }

    /** @return 兼容协议 */
    public @NotNull Set<String> protocolCodes() {
        return protocolCodes;
    }
}
