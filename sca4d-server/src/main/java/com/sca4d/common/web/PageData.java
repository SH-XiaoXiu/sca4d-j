package com.sca4d.common.web;

import com.mybatisflex.core.paginate.Page;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应数据。
 *
 * <p>用于屏蔽 ORM 分页对象，Controller 对外只暴露固定的分页结构。
 *
 * @param <T> 记录类型
 */
public class PageData<T> {

    private List<T> records;
    private long page;
    private long size;
    private long total;

    public PageData() {
    }

    public PageData(@NotNull List<T> records, long page, long size, long total) {
        this.records = List.copyOf(records);
        this.page = page;
        this.size = size;
        this.total = total;
    }

    /**
     * 创建空分页。
     *
     * @param page 当前页码
     * @param size 每页数量
     * @param <T> 记录类型
     * @return 空分页响应
     */
    public static <T> @NotNull PageData<T> empty(long page, long size) {
        return new PageData<>(List.of(), page, size, 0);
    }

    /**
     * 从 MyBatis-Flex Page 转换。
     *
     * @param page ORM 分页对象
     * @param <T> 记录类型
     * @return 分页响应；page 为 null 时返回空分页
     */
    public static <T> @NotNull PageData<T> from(@Nullable Page<T> page) {
        if (page == null) {
            return empty(1, 0);
        }
        return new PageData<>(page.getRecords(), page.getPageNumber(), page.getPageSize(), page.getTotalRow());
    }

    /**
     * 从 MyBatis-Flex Page 转换并映射记录类型。
     *
     * @param page ORM 分页对象
     * @param mapper 记录转换函数
     * @param <S> 原始记录类型
     * @param <T> 目标记录类型
     * @return 分页响应；page 为 null 时返回空分页
     */
    public static <S, T> @NotNull PageData<T> from(@Nullable Page<S> page, @NotNull Function<? super S, T> mapper) {
        if (page == null) {
            return empty(1, 0);
        }
        // 仅转换 records，分页元数据保持 ORM Page 的真实结果。
        List<T> records = page.getRecords().stream().map(mapper).toList();
        // 返回对外固定结构，避免 Controller 暴露 ORM 类型。
        return new PageData<>(records, page.getPageNumber(), page.getPageSize(), page.getTotalRow());
    }

    /**
     * 映射当前分页的记录类型，分页元数据保持不变。
     *
     * @param mapper 记录转换函数
     * @param <R> 目标记录类型
     * @return 转换后的分页响应
     */
    public <R> @NotNull PageData<R> map(@NotNull Function<? super T, R> mapper) {
        return new PageData<>(getRecords().stream().map(mapper).toList(), page, size, total);
    }

    public @NotNull List<T> getRecords() {
        return records == null ? List.of() : records;
    }

    public void setRecords(@NotNull List<T> records) {
        this.records = List.copyOf(records);
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
