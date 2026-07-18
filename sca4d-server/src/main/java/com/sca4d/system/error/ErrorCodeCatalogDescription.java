package com.sca4d.system.error;

import com.sca4d.common.exception.CodeRangeItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 当前请求语言下的错误码与号段目录。 */
public class ErrorCodeCatalogDescription {

    private final List<ErrorCodeDescription> items;
    private final List<CodeRangeItem> ranges;

    /**
     * 创建已经本地化的错误码目录。
     *
     * @param items 按错误码升序排列的目录说明
     * @param ranges 按起始错误码升序排列的号段数据
     */
    public ErrorCodeCatalogDescription(
            @NotNull List<ErrorCodeDescription> items, @NotNull List<CodeRangeItem> ranges) {
        this.items = List.copyOf(items);
        this.ranges = List.copyOf(ranges);
    }

    /** @return 按错误码升序排列的目录说明 */
    public @NotNull List<ErrorCodeDescription> getItems() {
        return items;
    }

    /** @return 按起始错误码升序排列的号段数据 */
    public @NotNull List<CodeRangeItem> getRanges() {
        return ranges;
    }
}
