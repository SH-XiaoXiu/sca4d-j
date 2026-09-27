package com.sca4d.core.api.query;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 保存一次分页查询返回的当前页记录和完整命中数量。
 *
 * @param <T> 当前页记录类型
 */
public class Page<T> {

    private final @NotNull @Unmodifiable List<@NotNull T> items;

    private final int page;

    private final int size;

    private final long total;

    /**
     * 创建分页结果。
     *
     * @param items 当前页记录；数量不能超过 request 的 size
     * @param request 产生当前结果的分页要求
     * @param total 满足完整查询条件的非负记录数量
     * @throws NullPointerException items、其中的记录或 request 为空时抛出
     * @throws IllegalArgumentException items 数量超过 size 或 total 为负数时抛出
     */
    public Page(
            @NotNull List<@NotNull T> items,
            @NotNull PageRequest request,
            long total) {
        List<T> checkedItems = List.copyOf(Objects.requireNonNull(items, "分页记录不能为空"));
        PageRequest checkedRequest = Objects.requireNonNull(request, "分页要求不能为空");
        if (checkedItems.size() > checkedRequest.size() || total < 0) {
            throw new IllegalArgumentException("分页结果的记录数量或总数无效");
        }
        this.items = checkedItems;
        this.page = checkedRequest.page();
        this.size = checkedRequest.size();
        this.total = total;
    }

    /**
     * 创建没有命中记录的分页结果。
     *
     * @param request 原始分页要求
     * @param <T> 记录类型
     * @return 保留页码和每页数量的空分页结果
     */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static <T> @NotNull Page<T> empty(@NotNull PageRequest request) {
        return new Page<>(List.of(), request, 0);
    }

    /**
     * 转换当前页记录并保留分页元数据。
     *
     * @param mapper 记录转换函数；不能返回空记录
     * @param <R> 目标记录类型
     * @return 包含转换后记录的新分页结果
     * @throws NullPointerException mapper 或任一转换结果为空时抛出
     */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public <R> @NotNull Page<R> map(@NotNull Function<? super T, ? extends R> mapper) {
        Function<? super T, ? extends R> checked = Objects.requireNonNull(mapper, "分页记录转换函数不能为空");
        List<R> mapped = new ArrayList<>(items.size());
        for (T item : items) {
            mapped.add(checked.apply(item));
        }
        return new Page<>(mapped, new PageRequest(page, size), total);
    }

    /** @return 当前页不可修改的记录 */
    @Contract(pure = true)
    public @NotNull @Unmodifiable List<@NotNull T> getItems() {
        return items;
    }

    /** @return 从一开始的页码 */
    @Contract(pure = true)
    public int getPage() {
        return page;
    }

    /** @return 请求的每页数量 */
    @Contract(pure = true)
    public int getSize() {
        return size;
    }

    /** @return 满足完整查询条件的记录数量 */
    @Contract(pure = true)
    public long getTotal() {
        return total;
    }
}
