package com.sca4d.core.spring.web.list;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.api.query.SortDir;
import com.sca4d.core.api.query.SortOrder;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 声明一个 HTTP 列表接口允许的排序字段、默认排序和可选页大小上限。
 *
 * <p>解析只生成 {@link Sort} 和 {@link SortDir}，不生成 SQL、数据库列名或筛选条件。
 */
public class ListDefinition {

    private final @Nullable Integer maxSize;

    private final @NotNull @Unmodifiable List<@NotNull SortOrder> defaultSort;

    private final @NotNull @Unmodifiable Map<@NotNull String, @NotNull Sort> fields;

    /** 使用构造器已经收集的字段、默认排序和页大小限制创建不可修改声明。 */
    private ListDefinition(@NotNull Builder builder) {
        if (builder.fields.isEmpty()) {
            throw new IllegalArgumentException("列表必须声明至少一个排序字段");
        }
        if (builder.defaultSort.isEmpty()) {
            throw new IllegalArgumentException("列表必须声明默认排序");
        }
        this.fields = Map.copyOf(builder.fields);
        this.defaultSort = List.copyOf(builder.defaultSort);
        Set<Sort> defaultFields = new HashSet<>();
        for (SortOrder order : defaultSort) {
            if (!fields.containsValue(order.getField()) || !defaultFields.add(order.getField())) {
                throw new IllegalArgumentException("列表默认排序包含未声明或重复的字段");
            }
        }
        this.maxSize = builder.maxSize;
    }

    /** @return 尚未声明字段和默认排序的构造器 */
    @CheckReturnValue
    @Contract(value = "-> new", pure = true)
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * 把 Controller 收到的查询参数解析成不含外部字符串的列表要求。
     *
     * @param page 已通过全局限制的分页要求
     * @param values 按 HTTP 参数出现顺序保存的 sort 值
     * @param maxSortFields 全局允许的最大排序字段数量
     * @return 包含默认或请求排序条件的列表要求
     * @throws BusinessException 页大小或任一 sort 参数无效时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    @NotNull
    ListRequest resolve(
            @NotNull PageRequest page,
            @NotNull List<@NotNull String> values,
            int maxSortFields) {
        PageRequest checkedPage = Objects.requireNonNull(page, "列表分页要求不能为空");
        List<String> checkedValues = List.copyOf(Objects.requireNonNull(values, "列表排序参数不能为空"));
        if (maxSize != null && checkedPage.size() > maxSize) {
            throw validationFailure(null);
        }
        if (checkedValues.isEmpty()) {
            if (defaultSort.size() > maxSortFields) {
                throw new IllegalStateException("列表默认排序字段数量超过 sca4dj.core.sort-max-fields");
            }
            return new ListRequest(checkedPage, defaultSort);
        }
        if (checkedValues.size() > maxSortFields) {
            throw validationFailure(null);
        }
        try {
            List<SortOrder> orders = new ArrayList<>(checkedValues.size());
            for (String value : checkedValues) {
                int separator = value.indexOf(',');
                if (separator < 1
                        || separator != value.lastIndexOf(',')
                        || separator == value.length() - 1) {
                    throw new IllegalArgumentException("sort 参数必须使用 FIELD,DIRECTION");
                }
                Sort field = fields.get(value.substring(0, separator));
                if (field == null) {
                    throw new IllegalArgumentException("sort 参数包含未知字段");
                }
                SortDir direction = SortDir.valueOf(value.substring(separator + 1));
                orders.add(SortOrder.of(field, direction));
            }
            return new ListRequest(checkedPage, orders);
        } catch (IllegalArgumentException exception) {
            throw validationFailure(exception);
        }
    }

    /** 把列表协议错误转换为统一 HTTP 输入错误。 */
    private static @NotNull BusinessException validationFailure(@Nullable RuntimeException cause) {
        return cause == null
                ? new BusinessException(CoreErrorCode.VALIDATION_FAILED)
                : new BusinessException(CoreErrorCode.VALIDATION_FAILED, cause);
    }

    /** 按声明顺序收集列表允许字段、默认排序和页大小限制。 */
    public static class Builder {

        private final @NotNull Map<@NotNull String, @NotNull Sort> fields = new LinkedHashMap<>();

        private final @NotNull List<@NotNull SortOrder> defaultSort = new ArrayList<>();

        private @Nullable Integer maxSize;

        private Builder() {
        }

        /**
         * 登记当前列表允许使用的排序字段。
         *
         * @param values 公共或业务自定义排序字段
         * @return 当前构造器
         * @throws IllegalArgumentException 同名字段重复声明时抛出
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder fields(Sort @NotNull... values) {
            for (Sort field : List.of(values)) {
                Sort previous = fields.putIfAbsent(field.name(), field);
                if (previous != null) {
                    throw new IllegalArgumentException("列表排序字段不能重复声明: " + field.name());
                }
            }
            return this;
        }

        /**
         * 声明请求没有 sort 参数时使用的有序条件。
         *
         * @param orders 默认排序条件
         * @return 当前构造器
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder defaultSort(SortOrder @NotNull... orders) {
            defaultSort.addAll(List.of(orders));
            return this;
        }

        /**
         * 声明当前接口比全局配置更小的页大小限制。
         *
         * @param value 正数页大小上限
         * @return 当前构造器
         */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder maxSize(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("列表最大每页数量必须为正数");
            }
            maxSize = value;
            return this;
        }

        /** @return 校验字段和默认排序后的不可修改列表声明 */
        @CheckReturnValue
        @Contract(value = "-> new", pure = true)
        public @NotNull ListDefinition build() {
            return new ListDefinition(this);
        }
    }
}
