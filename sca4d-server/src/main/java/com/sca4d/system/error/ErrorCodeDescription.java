package com.sca4d.system.error;

import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/** 当前请求语言下的一个错误码目录说明。 */
public class ErrorCodeDescription {

    private final int code;
    private final String messageKey;
    private final String message;
    private final ResponseCode category;
    private final int httpStatus;
    private final String source;

    /**
     * 创建错误码目录说明。
     *
     * @param code 固定业务错误码
     * @param messageKey 用于按请求语言读取错误文本的资源键
     * @param message 当前请求语言的错误消息
     * @param category 通用响应分类
     * @param httpStatus HTTP 状态码
     * @param source 声明该错误码的枚举类型
     */
    public ErrorCodeDescription(
            int code,
            @NotNull String messageKey,
            @NotNull String message,
            @NotNull ResponseCode category,
            int httpStatus,
            @NotNull String source) {
        this.code = code;
        this.messageKey = messageKey;
        this.message = message;
        this.category = category;
        this.httpStatus = httpStatus;
        this.source = source;
    }

    /** @return 固定业务错误码 */
    public int getCode() {
        return code;
    }

    /** @return 用于按请求语言读取错误文本的资源键 */
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    /** @return 当前请求语言的错误消息 */
    public @NotNull String getMessage() {
        return message;
    }

    /** @return 通用响应分类 */
    public @NotNull ResponseCode getCategory() {
        return category;
    }

    /** @return HTTP 状态码 */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** @return 声明该错误码的枚举类型 */
    public @NotNull String getSource() {
        return source;
    }
}
