package com.sca4d.core.api.error;

import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 合并并校验多个错误码枚举的不可变目录（Error Code Catalog）。
 *
 * <p>目录拒绝非枚举实现、缺少号段、编码非整数、编码越界、编码重复和号段重叠。
 */
public class ErrorCodeCatalog {

    private final Map<String, ErrorCode> errorCodes;

    /**
     * 创建错误码目录。
     *
     * @param enumTypes 实现 {@link ErrorCode} 的错误码枚举类型
     */
    public ErrorCodeCatalog(List<Class<? extends Enum<?>>> enumTypes) {
        Objects.requireNonNull(enumTypes, "错误码枚举类型不能为空");
        Map<String, ErrorCode> indexed = new HashMap<>();
        Map<Class<? extends Enum<?>>, ErrorCodeRange> ranges = new HashMap<>();
        for (Class<? extends Enum<?>> enumType : enumTypes) {
            if (!ErrorCode.class.isAssignableFrom(enumType)) {
                throw new IllegalArgumentException("错误码类型必须是实现 ErrorCode 的枚举: " + enumType.getName());
            }
            ErrorCodeRange range = enumType.getAnnotation(ErrorCodeRange.class);
            if (range == null || range.start() > range.end()) {
                throw new IllegalArgumentException("错误码枚举必须声明有效号段: " + enumType.getName());
            }
            for (Map.Entry<Class<? extends Enum<?>>, ErrorCodeRange> entry : ranges.entrySet()) {
                if (range.start() <= entry.getValue().end() && entry.getValue().start() <= range.end()) {
                    throw new IllegalArgumentException(
                            "错误码号段重叠: " + enumType.getName() + " 与 " + entry.getKey().getName());
                }
            }
            ranges.put(enumType, range);
            for (Enum<?> constant : enumType.getEnumConstants()) {
                ErrorCode errorCode = (ErrorCode) constant;
                int numericCode;
                try {
                    numericCode = Integer.parseInt(errorCode.code());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("错误码必须使用整数文本: " + errorCode.code(), exception);
                }
                if (numericCode < range.start() || numericCode > range.end()) {
                    throw new IllegalArgumentException("错误码超出声明号段: " + errorCode.code());
                }
                if (indexed.putIfAbsent(errorCode.code(), errorCode) != null) {
                    throw new IllegalArgumentException("错误码重复: " + errorCode.code());
                }
            }
        }
        this.errorCodes = Map.copyOf(indexed);
    }

    /**
     * 按编码取得错误码。
     *
     * @param code 错误编码
     * @return 对应枚举常量
     */
    public ErrorCode loadByCode(String code) {
        ErrorCode errorCode = errorCodes.get(code);
        if (errorCode == null) {
            throw new IllegalArgumentException("错误码不存在: " + code);
        }
        return errorCode;
    }

    /**
     * 返回按编码索引的不可变错误码映射。
     *
     * @return 按编码索引的不可变错误码映射
     */
    public @Unmodifiable Map<String, ErrorCode> entries() {
        return errorCodes;
    }
}
