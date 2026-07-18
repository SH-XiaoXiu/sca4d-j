package com.sca4d.system.view;

import com.sca4d.common.exception.ResponseCode;
import com.sca4d.system.error.ErrorCodeDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * 错误码视图。
 */
@Getter
@AllArgsConstructor
@Schema(description = "错误码视图")
public class ErrorCodeView {

    /**
     * 不随请求语言变化的数值错误码。
     */
    @Schema(description = "数值错误码", example = "1000")
    private final int code;

    /** 用于按请求语言读取错误消息的资源键。 */
    @Schema(description = "错误消息资源键", example = "error.common.bad-request")
    private final String messageKey;

    /**
     * 当前请求语言的错误消息。
     */
    @Schema(description = "当前请求语言的错误消息", example = "请求参数错误")
    private final String message;

    /**
     * 响应分类。
     */
    @Schema(description = "响应分类", example = "BAD_REQUEST")
    private final ResponseCode category;

    /**
     * HTTP 状态码。
     */
    @Schema(description = "HTTP 状态码", example = "400")
    private final int httpStatus;

    /**
     * 错误码来源。
     */
    @Schema(description = "错误码来源", example = "CommonErrorCode")
    private final String source;

    /**
     * 从错误码目录项构造视图。
     *
     * @param item 已按当前请求语言解析的错误码说明
     * @return 错误码视图
     */
    public static @NotNull ErrorCodeView from(@NotNull ErrorCodeDescription item) {
        return new ErrorCodeView(
                item.getCode(),
                item.getMessageKey(),
                item.getMessage(),
                item.getCategory(),
                item.getHttpStatus(),
                item.getSource());
    }
}
