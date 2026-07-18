package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错误码目录。
 *
 * <p>该目录是纯内存对象，用于收集和校验错误码定义。注册入口按枚举类注册，
 * 以确保每个错误码枚举都经过号段声明和冲突校验。
 */
public final class ErrorCodeCatalog {

    private final Map<Integer, ErrorCodeItem> items;
    private final List<CodeRangeItem> ranges;

    /**
     * 固化已经完成号段和编码冲突校验的目录快照。
     *
     * @param items 按业务错误码索引的目录项
     * @param ranges 已注册错误码枚举的号段项
     */
    private ErrorCodeCatalog(@NotNull Map<Integer, ErrorCodeItem> items, @NotNull List<CodeRangeItem> ranges) {
        this.items = Map.copyOf(items);
        this.ranges = List.copyOf(ranges);
    }

    /**
     * 创建包含 common 基础错误码的目录。
     *
     * @return 错误码目录
     */
    public static @NotNull ErrorCodeCatalog common() {
        return builder().register(CommonErrorCode.class).build();
    }

    /**
     * 创建错误码目录构建器。
     *
     * @return 构建器
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * 获取排序后的错误码项。
     *
     * @return 按 code 升序排列的错误码项
     */
    public @NotNull List<ErrorCodeItem> items() {
        return items.values().stream()
                .sorted(Comparator.comparingInt(ErrorCodeItem::getCode))
                .toList();
    }

    /**
     * 获取排序后的号段项。
     *
     * @return 按 start 升序排列的号段项
     */
    public @NotNull List<CodeRangeItem> ranges() {
        return ranges.stream()
                .sorted(Comparator.comparingInt(CodeRangeItem::getStart))
                .toList();
    }

    /**
     * 错误码目录构建器。
     *
     * <p>构建阶段会检测号段声明、号段冲突、错误码越界和重复 code。
     */
    public static final class Builder {

        private final Map<Integer, ErrorCodeItem> items = new LinkedHashMap<>();
        private final List<CodeRangeItem> ranges = new ArrayList<>();

        /** 创建尚未注册任何错误码枚举的目录构建器。 */
        private Builder() {
        }

        /**
         * 注册错误码枚举类。
         *
         * <p>每个枚举的错误码常量只执行一次内存遍历，用于校验号段并写入目录，不产生数据库或外部调用。
         *
         * @param enumType 错误码枚举类
         * @return 当前构建器
         */
        public @NotNull Builder register(@NotNull Class<? extends Enum<?>> enumType) {
            if (!BizErrorCode.class.isAssignableFrom(enumType)) {
                throw new IllegalArgumentException("错误码枚举必须实现 BizErrorCode: " + enumType.getName());
            }
            CodeRange range = enumType.getAnnotation(CodeRange.class);
            if (range == null) {
                throw new IllegalArgumentException("错误码枚举必须声明 @CodeRange: " + enumType.getName());
            }
            if (range.start() > range.end()) {
                throw new IllegalArgumentException("错误码号段起始值不能大于结束值: " + enumType.getName());
            }
            validateRangeConflict(enumType, range);
            BizErrorCode[] errorCodes = errorCodes(enumType);
            for (BizErrorCode errorCode : errorCodes) {
                validateCodeInRange(enumType, range, errorCode);
                put(ErrorCodeItem.from(errorCode));
            }
            ranges.add(new CodeRangeItem(enumType.getSimpleName(), range.start(), range.end(), errorCodes.length));
            return this;
        }

        /**
         * 构建错误码目录。
         *
         * @return 错误码目录
         */
        public @NotNull ErrorCodeCatalog build() {
            return new ErrorCodeCatalog(items, ranges);
        }

        /**
         * 读取已经证明实现 BizErrorCode 的枚举常量。
         *
         * @param enumType 待注册错误码枚举类型
         * @return 保持枚举声明顺序的错误码数组
         */
        @SuppressWarnings("unchecked")
        private @NotNull BizErrorCode[] errorCodes(@NotNull Class<? extends Enum<?>> enumType) {
            return (BizErrorCode[]) enumType.getEnumConstants();
        }

        /**
         * 将新号段与全部已注册号段逐一比较并拒绝任意闭区间重叠。
         *
         * @param enumType 新号段所属错误码枚举
         * @param range 新枚举声明的闭区间号段
         */
        private void validateRangeConflict(@NotNull Class<? extends Enum<?>> enumType, @NotNull CodeRange range) {
            for (CodeRangeItem existing : ranges) {
                if (range.start() <= existing.getEnd() && existing.getStart() <= range.end()) {
                    throw new IllegalArgumentException("错误码号段冲突: " + enumType.getSimpleName() + "["
                            + range.start() + "," + range.end() + "] 与 " + existing.getSource() + "["
                            + existing.getStart() + "," + existing.getEnd() + "]");
                }
            }
        }

        /**
         * 校验单个业务错误码落在所属枚举声明的闭区间内。
         *
         * @param enumType 错误码所属枚举
         * @param range 当前枚举声明的号段
         * @param errorCode 待注册业务错误码
         */
        private void validateCodeInRange(
                @NotNull Class<? extends Enum<?>> enumType, @NotNull CodeRange range, @NotNull BizErrorCode errorCode) {
            int code = errorCode.getCode();
            if (code < range.start() || code > range.end()) {
                throw new IllegalArgumentException("错误码超出声明号段: " + enumType.getSimpleName() + "." + code + " 不在 ["
                        + range.start() + "," + range.end() + "]");
            }
        }

        /**
         * 按 code 注册唯一错误码项，并在冲突时保留双方来源用于启动诊断。
         *
         * @param item 待加入目录的错误码项
         */
        private void put(@NotNull ErrorCodeItem item) {
            ErrorCodeItem existing = items.putIfAbsent(item.getCode(), item);
            if (existing == null) {
                return;
            }
            // 重复错误码会让调用方无法判断真实错误来源，构建阶段直接失败。
            throw new IllegalArgumentException(
                    "重复错误码: " + item.getCode() + ", " + existing.getSource() + ", " + item.getSource());
        }
    }

    /**
     * 获取内部错误码项快照。
     *
     * @return 错误码项列表副本
     */
    @NotNull
    List<ErrorCodeItem> itemSnapshot() {
        return new ArrayList<>(items.values());
    }

    /**
     * 获取内部号段项快照。
     *
     * @return 号段项列表副本
     */
    @NotNull
    List<CodeRangeItem> rangeSnapshot() {
        return new ArrayList<>(ranges);
    }
}
