package com.sca4d.iot.model;

import com.sca4d.iot.api.model.ThingArrayValue;
import com.sca4d.iot.api.model.ThingObjectValue;
import com.sca4d.iot.api.model.ThingValueKind;
import com.sca4d.iot.api.model.ThingValueType;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 把结构化输入转换成物模型声明的精确 Java 类型并递归检查约束。 */
public final class ThingValueValidator {

    /** 创建无状态物模型值校验器。 */
    public ThingValueValidator() {
    }

    /**
     * 校验并规范一个物模型值。
     *
     * @param type 目标物模型值类型
     * @param value JSON 或协议解码得到的非空值
     * @return 使用固定 Java 类型和不可变容器的规范值
     */
    public @NotNull Object validate(@NotNull ThingValueType type, @NotNull Object value) {
        ThingValueType checkedType = Objects.requireNonNull(type, "物模型值类型不能为空");
        Object checkedValue = Objects.requireNonNull(value, "物模型值不能为空");
        return switch (checkedType.kind()) {
            case BOOLEAN -> requireType(checkedValue, Boolean.class);
            case INTEGER -> integer(checkedType, checkedValue);
            case DECIMAL -> decimal(checkedType, checkedValue);
            case STRING -> string(checkedType, checkedValue, false);
            case NUM -> enumeration(checkedType, checkedValue);
            case TIMESTAMP -> timestamp(checkedValue);
            case OBJECT -> object(checkedType, checkedValue);
            case ARRAY -> array(checkedType, checkedValue);
        };
    }

    private static Long integer(ThingValueType type, Object value) {
        Long result;
        if (value instanceof Long longValue) {
            result = longValue;
        } else if (value instanceof Integer integerValue) {
            result = integerValue.longValue();
        } else {
            throw typeFailure(ThingValueKind.INTEGER, value);
        }
        range(type, BigDecimal.valueOf(result));
        return result;
    }

    private static BigDecimal decimal(ThingValueType type, Object value) {
        BigDecimal result;
        if (value instanceof BigDecimal decimal) {
            result = decimal;
        } else if (value instanceof Number number) {
            result = new BigDecimal(number.toString());
        } else if (value instanceof String text) {
            try {
                result = new BigDecimal(text);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("decimal 值格式不正确", exception);
            }
        } else {
            throw typeFailure(ThingValueKind.DECIMAL, value);
        }
        range(type, result);
        type.scale().ifPresent(scale -> {
            if (result.scale() > scale) {
                throw new IllegalArgumentException("decimal 小数位超过物模型 scale");
            }
        });
        return result;
    }

    private static String string(ThingValueType type, Object value, boolean enumeration) {
        String result = requireType(value, String.class);
        type.maximumLength().ifPresent(maximum -> {
            if (result.length() > maximum) {
                throw new IllegalArgumentException("string 长度超过物模型 maximumLength");
            }
        });
        if (!type.matchesPattern(result)) {
            throw new IllegalArgumentException("string 值不匹配物模型 pattern");
        }
        if (!enumeration && result.isEmpty()) {
            return result;
        }
        return result;
    }

    private static String enumeration(ThingValueType type, Object value) {
        String result = string(type, value, true);
        if (!type.enumValues().contains(result)) {
            throw new IllegalArgumentException("enum 值不在物模型允许集合中: " + result);
        }
        return result;
    }

    private static Instant timestamp(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("timestamp 必须是 ISO-8601 UTC 时间", exception);
            }
        }
        throw typeFailure(ThingValueKind.TIMESTAMP, value);
    }

    private ThingObjectValue object(ThingValueType type, Object value) {
        Map<?, ?> source;
        if (value instanceof ThingObjectValue objectValue) {
            source = objectValue.fields();
        } else if (value instanceof Map<?, ?> map) {
            source = map;
        } else {
            throw typeFailure(ThingValueKind.OBJECT, value);
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        type.requiredFields().forEach((code, fieldType) -> {
            if (!source.containsKey(code)) {
                throw new IllegalArgumentException("object 缺少必需字段: " + code);
            }
            result.put(code, validate(fieldType, Objects.requireNonNull(source.get(code), "object 字段值不能为空")));
        });
        type.optionalFields().forEach((code, fieldType) -> {
            if (source.containsKey(code)) {
                result.put(code, validate(fieldType, Objects.requireNonNull(source.get(code), "object 字段值不能为空")));
            }
        });
        for (Object key : source.keySet()) {
            if (!(key instanceof String code)
                    || (!type.requiredFields().containsKey(code) && !type.optionalFields().containsKey(code))) {
                throw new IllegalArgumentException("object 包含未知字段: " + key);
            }
        }
        return new ThingObjectValue(result);
    }

    private ThingArrayValue array(ThingValueType type, Object value) {
        List<?> source;
        if (value instanceof ThingArrayValue arrayValue) {
            source = arrayValue.elements();
        } else if (value instanceof List<?> list) {
            source = list;
        } else {
            throw typeFailure(ThingValueKind.ARRAY, value);
        }
        type.maximumItems().ifPresent(maximum -> {
            if (source.size() > maximum) {
                throw new IllegalArgumentException("array 元素数超过物模型 maximumItems");
            }
        });
        ThingValueType elementType = type.elementType().orElseThrow();
        return new ThingArrayValue(
                source.stream()
                        .map(element -> validate(elementType, Objects.requireNonNull(element, "array 元素不能为空")))
                        .toList());
    }

    private static void range(ThingValueType type, BigDecimal value) {
        type.minimum().ifPresent(minimum -> {
            if (value.compareTo(new BigDecimal(minimum)) < 0) {
                throw new IllegalArgumentException("数值小于物模型 minimum");
            }
        });
        type.maximum().ifPresent(maximum -> {
            if (value.compareTo(new BigDecimal(maximum)) > 0) {
                throw new IllegalArgumentException("数值大于物模型 maximum");
            }
        });
    }

    private static <T> T requireType(Object value, Class<T> type) {
        if (!type.equals(value.getClass())) {
            throw new IllegalArgumentException(
                    "物模型值需要 " + type.getSimpleName() + "，实际为 " + value.getClass().getSimpleName());
        }
        return type.cast(value);
    }

    private static IllegalArgumentException typeFailure(ThingValueKind kind, Object value) {
        return new IllegalArgumentException("物模型 " + kind + " 值类型不正确: " + value.getClass().getSimpleName());
    }
}
