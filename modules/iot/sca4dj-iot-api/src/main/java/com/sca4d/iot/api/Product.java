package com.sca4d.iot.api;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** 定义一类设备允许采用的接入方式和 Java 协议实现。 */
public class Product {

    private final @NotNull LocalizedFields i18n;

    private final @NotNull Set<DeviceAccessMethod> accessMethods;

    private final @NotNull String code;

    private final long id;

    private final @NotNull Set<String> protocolCodes;

    /**
     * 创建完整 Product 快照。
     *
     * @param id Product 正数标识
     * @param code Product 稳定 code
     * @param i18n Product 按 Locale 保存的完整展示字段
     * @param accessMethods 允许的认证接入方式；不直接连接的设备类型可以为空
     * @param protocolCodes 非空兼容协议 code
     */
    public Product(
            long id,
            @NotNull String code,
            @NotNull LocalizedFields i18n,
            @NotNull Collection<DeviceAccessMethod> accessMethods,
            @NotNull Collection<String> protocolCodes) {
        if (id <= 0) {
            throw new IllegalArgumentException("Product 标识必须为正数");
        }
        this.id = id;
        this.code = TextUtils.requireText(code, "Product code");
        this.i18n = Objects.requireNonNull(i18n, "Product 多语言字段不能为空");
        this.accessMethods = Set.copyOf(Objects.requireNonNull(accessMethods, "Product 接入方式不能为空"));
        if (this.accessMethods.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Product 接入方式不能包含空值");
        }
        Collection<String> checkedProtocolCodes = Objects.requireNonNull(
                protocolCodes,
                "Product 协议不能为空");
        if (checkedProtocolCodes.isEmpty() || checkedProtocolCodes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Product 协议必须包含非空值");
        }
        LinkedHashSet<String> protocols = new LinkedHashSet<>();
        for (String protocol : checkedProtocolCodes) {
            protocols.add(TextUtils.requireText(protocol, "协议 code"));
        }
        this.protocolCodes = Set.copyOf(protocols);
    }

    /** @return Product 标识 */
    public long id() {
        return id;
    }

    /** @return Product 稳定 code */
    public @NotNull String code() {
        return code;
    }

    /** @return Product 按 Locale 保存的完整展示字段 */
    public @NotNull LocalizedFields i18n() {
        return i18n;
    }

    /** @return Product 允许 Device 选择的认证接入方式；可以为空 */
    public @NotNull @Unmodifiable Set<DeviceAccessMethod> accessMethods() {
        return accessMethods;
    }

    /** @return 不可修改的兼容协议 code */
    public @NotNull @Unmodifiable Set<String> protocolCodes() {
        return protocolCodes;
    }

}
