package com.sca4d.common.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 游标分页响应。
 *
 * @param <T> 记录类型
 */
public class CursorPageData<T> {

    private List<T> records;
    private String nextCursor;
    private boolean hasMore;

    public CursorPageData() {
    }

    public CursorPageData(@NotNull List<T> records, @Nullable String nextCursor, boolean hasMore) {
        this.records = List.copyOf(records);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    /**
     * 创建游标分页响应。
     *
     * @param records 当前页记录
     * @param nextCursor 下一页游标
     * @param hasMore 是否还有下一页
     * @param <T> 记录类型
     * @return 游标分页响应
     */
    public static <T> @NotNull CursorPageData<T> of(
            @NotNull List<T> records, @Nullable String nextCursor, boolean hasMore) {
        return new CursorPageData<>(records, nextCursor, hasMore);
    }

    public @NotNull List<T> getRecords() {
        return records == null ? List.of() : records;
    }

    public void setRecords(@NotNull List<T> records) {
        this.records = List.copyOf(records);
    }

    public @Nullable String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(@Nullable String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
