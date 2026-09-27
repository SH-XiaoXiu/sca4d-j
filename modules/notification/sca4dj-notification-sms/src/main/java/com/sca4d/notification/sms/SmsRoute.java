package com.sca4d.notification.sms;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/** 标识具体应用声明的一条 SMS Provider 路由，不预设国家、地区或分区规则。 */
public class SmsRoute {

    private static final Pattern VALUE = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    private final @NotNull String value;

    /**
     * @param value 具体应用内稳定的小写路由编码，例如 `china-mainland` 或 `international`
     */
    public SmsRoute(@NotNull String value) {
        String checked = TextUtils.requireText(value, "SMS 路由编码");
        if (!VALUE.matcher(checked).matches()) {
            throw new IllegalArgumentException("SMS 路由编码只能包含小写字母、数字和连字符");
        }
        this.value = checked;
    }

    /** @return 具体应用声明的稳定路由编码 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof SmsRoute route && value.equals(route.value);
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
