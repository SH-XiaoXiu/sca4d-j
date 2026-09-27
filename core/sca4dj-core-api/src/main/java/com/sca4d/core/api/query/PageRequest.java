package com.sca4d.core.api.query;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 表示一次从一开始计数的分页查询要求。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PageRequest {

    private final int page;

    private final int size;

    /**
     * 创建分页查询要求。
     *
     * @param page 从一开始的页码
     * @param size 当前页最多返回的记录数量
     * @throws IllegalArgumentException page 或 size 不是正数时抛出
     */
    public PageRequest(int page, int size) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("页码和每页数量必须为正数");
        }
        this.page = page;
        this.size = size;
    }

    /** @return 从一开始的页码 */
    @Contract(pure = true)
    public int page() {
        return page;
    }

    /** @return 当前页最多返回的记录数量 */
    @Contract(pure = true)
    public int size() {
        return size;
    }

    /** @return 数据库查询使用的零基偏移量 */
    @CheckReturnValue
    @Contract(pure = true)
    public long offset() {
        return (long) (page - 1) * size;
    }

    /** @return MyBatis 等 JavaBean 属性读取器使用的页码 */
    @Contract(pure = true)
    public int getPage() {
        return page;
    }

    /** @return MyBatis 等 JavaBean 属性读取器使用的页大小 */
    @Contract(pure = true)
    public int getSize() {
        return size;
    }

    /** @return MyBatis 等 JavaBean 属性读取器使用的数据库偏移量 */
    @Contract(pure = true)
    public long getOffset() {
        return offset();
    }

    /**
     * 检查当前每页数量没有超过调用方的限制。
     *
     * @param maxSize 调用方允许的最大每页数量
     * @return 当前分页要求
     * @throws IllegalArgumentException maxSize 不是正数或当前 size 超过限制时抛出
     */
    @CheckReturnValue
    @Contract(value = "_ -> this", pure = true)
    public @NotNull PageRequest verifyMaxSize(int maxSize) {
        if (maxSize < 1 || size > maxSize) {
            throw new IllegalArgumentException("每页数量超过允许的最大值");
        }
        return this;
    }
}
