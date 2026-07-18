package com.sca4d.common.web;

import cn.hutool.core.util.StrUtil;
import com.sca4d.common.core.SortDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 排序查询入参。
 *
 * <p>该对象只表达排序请求，不负责校验字段是否允许排序。字段白名单应由业务查询层决定。
 */
public class SortQuery {

    private String field;
    private SortDirection direction;

    public SortQuery() {
    }

    public SortQuery(@Nullable String field, @Nullable SortDirection direction) {
        this.field = field;
        this.direction = direction;
    }

    /**
     * 转换后的排序字段。
     *
     * @return 去除首尾空白后的字段名，空白时返回空字符串
     */
    public @NotNull String field() {
        return StrUtil.trimToEmpty(field);
    }

    /**
     * 转换后的排序方向。
     *
     * @return 未指定时默认降序
     */
    public @NotNull SortDirection direction() {
        return direction == null ? SortDirection.DESC : direction;
    }

    public @Nullable String getField() {
        return field;
    }

    public void setField(@Nullable String field) {
        this.field = field;
    }

    public @Nullable SortDirection getDirection() {
        return direction;
    }

    public void setDirection(@Nullable SortDirection direction) {
        this.direction = direction;
    }
}
