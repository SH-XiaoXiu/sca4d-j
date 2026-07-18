package com.sca4d.common.exception;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

/**
 * 通用响应分类。
 *
 * <p>该枚举用于表达 HTTP 响应分类、固定消息键和状态码。错误码目录共用使用 {@link BizErrorCode}，
 * common 层通过 {@link CommonErrorCode} 接入，业务模块通过自己的枚举实现 {@link BizErrorCode}。
 */
public enum ResponseCode implements BaseEnum {
    /** 成功。 */
    SUCCESS(0, "response.success", HttpStatus.OK),

    /** 请求参数错误。 */
    BAD_REQUEST(1000, "response.bad-request", HttpStatus.BAD_REQUEST),
    /** 未登录或认证失效。 */
    UNAUTHORIZED(1001, "response.unauthorized", HttpStatus.UNAUTHORIZED),
    /** 无权限。 */
    FORBIDDEN(1002, "response.forbidden", HttpStatus.FORBIDDEN),
    /** 资源不存在。 */
    NOT_FOUND(1003, "response.not-found", HttpStatus.NOT_FOUND),
    /** 请求方法不允许。 */
    METHOD_NOT_ALLOWED(1004, "response.method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED),
    /** 资源冲突。 */
    CONFLICT(1005, "response.conflict", HttpStatus.CONFLICT),
    /** 请求体过大。 */
    PAYLOAD_TOO_LARGE(1006, "response.payload-too-large", HttpStatus.PAYLOAD_TOO_LARGE),
    /** 媒体类型不支持。 */
    UNSUPPORTED_MEDIA_TYPE(1007, "response.unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** 请求过多。 */
    TOO_MANY_REQUESTS(1008, "response.too-many-requests", HttpStatus.TOO_MANY_REQUESTS),
    /** If-Match 等请求前置条件不成立。 */
    PRECONDITION_FAILED(1009, "response.precondition-failed", HttpStatus.PRECONDITION_FAILED),
    /** 请求缺少服务端要求的 If-Match 等前置条件。 */
    PRECONDITION_REQUIRED(1010, "response.precondition-required", HttpStatus.PRECONDITION_REQUIRED),
    /** 外部服务异常。 */
    EXTERNAL_SERVICE_ERROR(1097, "response.external-service-error", HttpStatus.INTERNAL_SERVER_ERROR),
    /** 内部服务异常。 */
    INTERNAL_SERVICE_ERROR(1098, "response.internal-service-error", HttpStatus.INTERNAL_SERVER_ERROR),
    /** 未知服务异常。 */
    SERVER_ERROR(1099, "response.server-error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String messageKey;
    private final HttpStatus httpStatus;

    ResponseCode(int code, @NotNull String messageKey, @NotNull HttpStatus httpStatus) {
        this.code = code;
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }

    /**
     * 按数值响应码查找枚举。
     *
     * @param code 数值响应码
     * @return 匹配的响应码；未匹配时返回 {@link #SERVER_ERROR}
     */
    public static @NotNull ResponseCode fromCode(int code) {
        for (ResponseCode responseCode : values()) {
            if (responseCode.code == code) {
                return responseCode;
            }
        }
        return SERVER_ERROR;
    }

    /**
     * 获取响应分类的固定数值编码。
     *
     * @return 与共用响应体和错误码目录一致的数值编码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取响应分类的固定消息键。
     *
     * @return 在全部支持语言资源中存在的固定消息键
     */
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    /**
     * 获取响应分类对应的 Spring HTTP 状态。
     *
     * @return HTTP 响应使用的状态码
     */
    public @NotNull HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 获取响应分类对应的 HTTP 数值状态码。
     *
     * @return HTTP 状态数值
     */
    public int getHttpCode() {
        return httpStatus.value();
    }

    /**
     * 获取字典和 JSON 序列化使用的固定分类值。
     *
     * @return 转换后的枚举名称
     */
    @Override
    public @NotNull String value() {
        return BaseEnum.normalize(name());
    }

    /**
     * 获取代码字典扫描使用的标签消息键。
     *
     * @return 与当前响应分类展示含义一致的固定消息键
     */
    @Override
    public @NotNull String description() {
        return messageKey;
    }

    /**
     * 判断当前分类是否表示成功。
     *
     * @return 仅 {@link #SUCCESS} 返回 {@code true}
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 判断当前分类是否表示失败。
     *
     * @return 非 {@link #SUCCESS} 返回 {@code true}
     */
    public boolean isError() {
        return !isSuccess();
    }

    /**
     * 判断当前分类是否使用 HTTP 5xx 状态。
     *
     * @return 服务端错误分类返回 {@code true}
     */
    public boolean isServerError() {
        return httpStatus.is5xxServerError();
    }
}
