package com.sca4d.iot.api;

import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.TranslatedText;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 定义数据库或代码 Product 共同使用的只读技术能力。 */
public final class ProductDefinition {

    private final @NotNull String code;

    private final @NotNull LocalizableText displayName;

    private final @NotNull Set<DeviceAccessMethod> accessMethods;

    private final @NotNull Set<String> protocolCodes;

    /** 创建来源无关的 Product 技术定义。 */
    public ProductDefinition(
            @NotNull String code,
            @NotNull LocalizableText displayName,
            @NotNull Collection<DeviceAccessMethod> accessMethods,
            @NotNull Collection<String> protocolCodes) {
        this.code = TextUtils.requireText(code, "ProductDefinition code");
        this.displayName = Objects.requireNonNull(displayName, "ProductDefinition 展示名称不能为空");
        this.accessMethods = Set.copyOf(
                Objects.requireNonNull(accessMethods, "ProductDefinition 接入方式不能为空"));
        if (this.accessMethods.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ProductDefinition 接入方式不能包含空值");
        }
        LinkedHashSet<String> protocols = new LinkedHashSet<>();
        for (String protocol : Objects.requireNonNull(protocolCodes, "ProductDefinition 协议不能为空")) {
            protocols.add(TextUtils.requireText(protocol, "ProductDefinition 协议"));
        }
        this.protocolCodes = Set.copyOf(protocols);
        if (this.protocolCodes.isEmpty()) {
            throw new IllegalArgumentException("ProductDefinition 协议不能为空");
        }
    }

    /** @return 从数据库 Product 创建相同技术定义 */
    @CheckReturnValue
    public static @NotNull ProductDefinition from(@NotNull Product product) {
        Product checked = Objects.requireNonNull(product, "Product 不能为空");
        Map<Locale, String> translations = new LinkedHashMap<>();
        checked.i18n()
                .locales()
                .forEach(
                        locale -> checked.i18n()
                                .find(locale, "displayName")
                                .ifPresent(value -> translations.put(locale, value)));
        return new ProductDefinition(
                checked.code(),
                TranslatedText.of(translations, checked.code()),
                checked.accessMethods(),
                checked.protocolCodes());
    }

    /** @return Product code */
    public @NotNull String code() {
        return code;
    }

    /** @return 展示名称 */
    public @NotNull LocalizableText displayName() {
        return displayName;
    }

    /** @return 允许的 Device 认证接入方式；可以为空 */
    public @NotNull @Unmodifiable Set<DeviceAccessMethod> accessMethods() {
        return accessMethods;
    }

    /** @return 兼容协议 code */
    public @NotNull @Unmodifiable Set<String> protocolCodes() {
        return protocolCodes;
    }
}
