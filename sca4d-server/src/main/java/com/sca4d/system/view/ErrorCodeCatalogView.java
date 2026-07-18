package com.sca4d.system.view;

import com.sca4d.system.error.ErrorCodeCatalogDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 错误码目录视图。
 */
@Data
@Accessors(chain = true)
@Schema(description = "错误码目录视图")
public class ErrorCodeCatalogView {

    /**
     * 错误码列表。
     */
    @Schema(description = "错误码列表")
    @NotNull
    private final List<ErrorCodeView> items;

    /**
     * 号段列表。
     */
    @Schema(description = "号段列表")
    @NotNull
    private final List<CodeRangeView> ranges;

    /**
     * 错误码总数。
     */
    @Schema(description = "错误码总数", example = "13")
    private final int total;

    /**
     * 从错误码目录构造视图。
     *
     * @param catalog 已按当前请求语言解析的错误码目录
     * @return 错误码目录视图
     */
    public static @NotNull ErrorCodeCatalogView from(@NotNull ErrorCodeCatalogDescription catalog) {
        List<ErrorCodeView> items = catalog.getItems().stream().map(ErrorCodeView::from).toList();
        List<CodeRangeView> ranges = catalog.getRanges().stream().map(CodeRangeView::from).toList();
        return new ErrorCodeCatalogView(items, ranges, items.size());
    }
}
