package com.sca4d.system.view;

import com.sca4d.common.exception.CodeRangeItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * 错误码号段视图。
 */
@Getter
@AllArgsConstructor
@Schema(description = "错误码号段视图")
public class CodeRangeView {

    /** 号段来源。 */
    @Schema(description = "号段来源", example = "CommonErrorCode")
    private final String source;

    /** 起始错误码。 */
    @Schema(description = "起始错误码", example = "0")
    private final int start;

    /** 结束错误码。 */
    @Schema(description = "结束错误码", example = "1999")
    private final int end;

    /** 号段内实际错误码数量。 */
    @Schema(description = "号段内实际错误码数量", example = "13")
    private final int size;

    /**
     * 从错误码号段目录项构造视图。
     *
     * @param item 错误码号段目录项
     * @return 错误码号段视图
     */
    public static @NotNull CodeRangeView from(@NotNull CodeRangeItem item) {
        return new CodeRangeView(item.getSource(), item.getStart(), item.getEnd(), item.getSize());
    }
}
