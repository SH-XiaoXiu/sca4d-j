package com.sca4d.core.api.gateway;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/** 表示 Gateway 可以发现并转发到应用实例的稳定回调编码。 */
public final class GatewayCallbackCode {

    private static final Pattern VALUE = Pattern.compile("[a-z0-9][a-z0-9.-]{0,127}");

    private final @NotNull String value;

    /**
     * 创建回调编码。
     *
     * @param value 由小写字母、数字、点和连字符组成的稳定编码
     * @throws IllegalArgumentException 编码格式不合法时抛出
     */
    public GatewayCallbackCode(@NotNull String value) {
        String checked = TextUtils.requireText(value, "Gateway 回调编码");
        if (!VALUE.matcher(checked).matches()) {
            throw new IllegalArgumentException("Gateway 回调编码只能包含小写字母、数字、点和连字符");
        }
        this.value = checked;
    }

    /** @return 跨实例注册和路由使用的稳定编码 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof GatewayCallbackCode code && value.equals(code.value);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
