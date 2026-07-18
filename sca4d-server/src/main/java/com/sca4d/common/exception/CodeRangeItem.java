package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;

/**
 * 错误码号段目录项。
 *
 * <p>该对象用于管理端展示当前系统中各错误码枚举占用的号段。
 */
public final class CodeRangeItem {

    private final String source;
    private final int start;
    private final int end;
    private final int size;

    /**
     * 创建错误码号段目录项。
     *
     * @param source 号段来源
     * @param start 起始错误码
     * @param end 结束错误码
     * @param size 号段内实际错误码数量
     */
    public CodeRangeItem(@NotNull String source, int start, int end, int size) {
        this.source = source;
        this.start = start;
        this.end = end;
        this.size = size;
    }

    /**
     * 获取号段来源。
     *
     * @return 号段来源
     */
    public @NotNull String getSource() {
        return source;
    }

    /**
     * 获取起始错误码。
     *
     * @return 起始错误码
     */
    public int getStart() {
        return start;
    }

    /**
     * 获取结束错误码。
     *
     * @return 结束错误码
     */
    public int getEnd() {
        return end;
    }

    /**
     * 获取号段内实际错误码数量。
     *
     * @return 实际错误码数量
     */
    public int getSize() {
        return size;
    }
}
