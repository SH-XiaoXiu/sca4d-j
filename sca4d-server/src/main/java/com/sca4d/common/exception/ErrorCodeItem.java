package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;

/**
 * 错误码目录项。
 *
 * <p>该对象用于把 {@link BizErrorCode} 整理成共用目录，方便后续管理端展示和排查。
 */
public final class ErrorCodeItem {

    private final int code;
    private final String messageKey;
    private final ResponseCode category;
    private final int httpStatus;
    private final String source;

    /**
     * 创建错误码目录项。
     *
     * @param code 数值错误码
     * @param messageKey 用于按请求语言读取消息的资源键
     * @param category 响应分类名称
     * @param httpStatus HTTP 状态码
     * @param source 错误码来源
     */
    public ErrorCodeItem(
            int code,
            @NotNull String messageKey,
            @NotNull ResponseCode category,
            int httpStatus,
            @NotNull String source) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
        this.httpStatus = httpStatus;
        this.source = source;
    }

    /**
     * 从业务错误码创建目录项。
     *
     * @param errorCode 业务错误码
     * @return 错误码目录项
     */
    public static @NotNull ErrorCodeItem from(@NotNull BizErrorCode errorCode) {
        ResponseCode category = errorCode.getCategory();
        return new ErrorCodeItem(
                errorCode.getCode(),
                errorCode.getMessageKey(),
                category,
                category.getHttpCode(),
                errorCode.getClass().getSimpleName());
    }

    /**
     * 获取数值错误码。
     *
     * @return 错误码数值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取错误消息资源键。
     *
     * @return Controller 按请求语言解析的消息键
     */
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    /**
     * 获取响应分类名称。
     *
     * @return 响应分类名称
     */
    public @NotNull ResponseCode getCategory() {
        return category;
    }

    /**
     * 获取 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 获取错误码来源。
     *
     * @return 错误码来源
     */
    public @NotNull String getSource() {
        return source;
    }
}
