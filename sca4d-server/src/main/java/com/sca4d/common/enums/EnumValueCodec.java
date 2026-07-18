package com.sca4d.common.enums;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 枚举 value 编解码入口。
 *
 * <p>Jackson、ORM 转换器和其他层都应优先调用该类，不各自实现枚举字符串解析规则。
 */
public final class EnumValueCodec {

    private EnumValueCodec() {
    }

    /**
     * 把枚举编码为固定字符串值。
     *
     * @param value 枚举值
     * @return BaseEnum 返回 value；普通枚举返回 name；入参为 null 时返回 null
     */
    public static @Nullable String encode(@Nullable Enum<?> value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BaseEnum baseEnum) {
            return baseEnum.value();
        }
        return value.name();
    }

    /**
     * 把 HTTP 参数或数据库字段中的字符串解码为枚举。
     *
     * @param enumType 枚举类型
     * @param value HTTP 参数或数据库字段中的字符串
     * @param <E> 枚举类型
     * @return 匹配的枚举；value 为 null 时返回 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E extends Enum<E>> @Nullable E decode(@NotNull Class<E> enumType, @Nullable String value) {
        if (value == null) {
            return null;
        }
        // BaseEnum 始终按 value 解析，使 HTTP 参数和数据库字段使用同一个字符串值。
        if (BaseEnum.class.isAssignableFrom(enumType)) {
            return (E) BaseEnum.fromValue((Class) enumType, value);
        }
        // 普通枚举仍按转换后的 name 解析，兼容少量非业务枚举。
        return Enum.valueOf(enumType, BaseEnum.normalize(value));
    }
}
