package com.sca4d.iot.api.model;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** 标识物模型递归值类型的固定种类。 */
public enum ThingValueKind {
    /** 布尔值。 */
    BOOLEAN(Boolean.class),
    /** 64 位整数。 */
    INTEGER(Long.class),
    /** 任意精度十进制数。 */
    DECIMAL(BigDecimal.class),
    /** 字符串。 */
    STRING(String.class),
    /** 从固定 code 集合中选择的字符串。 */
    ENUM(String.class),
    /** UTC 时间点。 */
    TIMESTAMP(Instant.class),
    /** 具有固定字段定义的对象。 */
    OBJECT(ThingObjectValue.class),
    /** 元素采用同一种值类型的数组。 */
    ARRAY(ThingArrayValue.class);

    private final @NotNull Class<?> javaType;

    ThingValueKind(@NotNull Class<?> javaType) {
        this.javaType = javaType;
    }

    /** @return 物模型校验、类型化命令和协议映射共同使用的 Java 类型 */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull Class<?> javaType() {
        return javaType;
    }
}
