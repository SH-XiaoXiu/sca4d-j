package com.sca4d.core.api.region;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 表示具体应用定义并用于业务策略判断的稳定区域。 */
public final class Region {

    private final @NotNull String code;

    /**
     * @param code 具体应用内稳定且唯一的区域编码，最长 128 个字符
     */
    public Region(@NotNull String code) {
        String checked = TextUtils.requireText(code, "区域编码");
        if (checked.length() > 128) {
            throw new IllegalArgumentException("区域编码不能超过 128 个字符");
        }
        this.code = checked;
    }

    /** @return 具体应用定义的稳定区域编码 */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof Region region && code.equals(region.code);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return code;
    }
}
