package com.sca4d.common.web;

import cn.hutool.core.util.StrUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 游标分页入参。
 *
 * <p>适用于无限滚动或实时数据列表。游标含义由具体业务决定，common 不解析游标内容。
 */
public class CursorQuery {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 500;

    private String cursor;
    private Integer size;

    public CursorQuery() {
    }

    public CursorQuery(@Nullable String cursor, @Nullable Integer size) {
        this.cursor = cursor;
        this.size = size;
    }

    /**
     * 转换后的游标。
     *
     * @return 去除首尾空白后的游标；空白时返回空字符串
     */
    public @NotNull String cursor() {
        return StrUtil.trimToEmpty(cursor);
    }

    /**
     * 转换后的每页数量。
     *
     * @return 处于 1 到最大限制之间的每页数量
     */
    public int size() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public @Nullable String getCursor() {
        return cursor;
    }

    public void setCursor(@Nullable String cursor) {
        this.cursor = cursor;
    }

    public @Nullable Integer getSize() {
        return size;
    }

    public void setSize(@Nullable Integer size) {
        this.size = size;
    }
}
