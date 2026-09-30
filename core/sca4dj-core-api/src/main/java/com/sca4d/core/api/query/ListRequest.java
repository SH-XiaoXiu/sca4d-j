package com.sca4d.core.api.query;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 保存一个列表查询已经校验的分页要求和有序排序条件。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class ListRequest {

    private final @NotNull @Unmodifiable List<@NotNull SortOrder> sort;

    private final @NotNull PageRequest page;

    /**
     * 创建列表查询要求。
     *
     * @param page 分页要求
     * @param sort 按优先级排列的排序条件；可以为空列表
     * @throws NullPointerException page、sort 或其中的排序条件为空时抛出
     * @throws IllegalArgumentException 同一个字段出现多次时抛出
     */
    public ListRequest(
            @NotNull PageRequest page,
            @NotNull List<@NotNull SortOrder> sort) {
        this.page = Objects.requireNonNull(page, "列表分页要求不能为空");
        this.sort = List.copyOf(Objects.requireNonNull(sort, "列表排序条件不能为空"));
        Set<Sort> fields = new HashSet<>();
        for (SortOrder order : this.sort) {
            if (!fields.add(order.getField())) {
                throw new IllegalArgumentException("同一个列表排序字段不能重复出现");
            }
        }
    }

    /** @return 当前列表的分页要求 */
    @Contract(pure = true)
    public @NotNull PageRequest getPage() {
        return page;
    }

    /** @return 按优先级排列且不可修改的排序条件 */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull @Unmodifiable List<@NotNull SortOrder> getSort() {
        return sort;
    }
}
