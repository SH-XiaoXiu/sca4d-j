package com.sca4d.core.api.enums;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/** 在业务枚举与外部字符串之间转换。 */
public final class EnumValueCodec {

    private EnumValueCodec() {
    }

    /**
     * 编码枚举值。
     *
     * @param value 可空枚举值
     * @return {@link BaseEnum#value()} 或普通枚举名称；输入为空时返回空
     */
    @Contract(value = "null -> null; !null -> !null", pure = true)
    public static @Nullable String encode(@Nullable Enum<?> value) {
        if (value == null) {
            return null;
        }
        return value instanceof BaseEnum baseEnum ? baseEnum.value() : value.name();
    }

    /**
     * 解码枚举值。
     *
     * @param enumType 枚举类型
     * @param value 可空外部值
     * @param <E> 枚举类型
     * @return 匹配常量；输入为空时返回空
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Contract(value = "_, null -> null; _, !null -> !null", pure = true)
    public static <E extends Enum<E>> @Nullable E decode(
            Class<E> enumType,
            @Nullable String value) {
        if (value == null) {
            return null;
        }
        if (BaseEnum.class.isAssignableFrom(enumType)) {
            return (E) BaseEnum.fromValue((Class) enumType, value);
        }
        return Enum.valueOf(enumType, BaseEnum.normalize(value));
    }
}
