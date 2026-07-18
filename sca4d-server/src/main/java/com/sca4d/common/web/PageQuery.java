package com.sca4d.common.web;

import com.mybatisflex.core.paginate.Page;
import org.jetbrains.annotations.NotNull;

/**
 * 分页查询入参。
 *
 * <p>页码从 1 开始。过小的页码会改为 1，过大的每页数量会限制为系统允许的最大值。
 */
public class PageQuery {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 500L;

    private Long page;
    private Long size;

    public PageQuery() {
    }

    public PageQuery(Long page, Long size) {
        this.page = page;
        this.size = size;
    }

    /**
     * 转换后的页码。
     *
     * @return 最小为 1 的页码
     */
    public long page() {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    /**
     * 转换后的每页数量。
     *
     * @return 处于 1 到最大限制之间的每页数量
     */
    public long size() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 转换为 MyBatis-Flex Page。
     *
     * @param <T> 分页记录类型
     * @return MyBatis-Flex Page
     */
    public <T> @NotNull Page<T> toPage() {
        return new Page<>(page(), size());
    }

    public Long getPage() {
        return page;
    }

    public void setPage(Long page) {
        this.page = page;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
