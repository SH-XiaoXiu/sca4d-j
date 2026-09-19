package com.sca4d.core.spring.web;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.springframework.http.HttpStatus;

/** Spring HTTP 请求处理产生的固定错误码。 */
@ErrorCodeRange(start = 1100, end = 1199)
public enum HttpErrorCode implements ErrorCode {
    /** 请求参数、请求体或约束不正确。 */
    BAD_REQUEST("1100", ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST, "error.web.bad-request"),
    /** 当前路由不支持使用的 HTTP 方法。 */
    METHOD_NOT_ALLOWED(
            "1101", ErrorCategory.VALIDATION, HttpStatus.METHOD_NOT_ALLOWED, "error.web.method-not-allowed"),
    /** 服务端无法生成请求接受的响应媒体类型。 */
    NOT_ACCEPTABLE("1102", ErrorCategory.VALIDATION, HttpStatus.NOT_ACCEPTABLE, "error.web.not-acceptable"),
    /** 服务端不支持请求使用的媒体类型。 */
    UNSUPPORTED_MEDIA_TYPE(
            "1103",
            ErrorCategory.VALIDATION,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "error.web.unsupported-media-type"),
    /** 请求体或上传文件超过服务端允许的大小。 */
    PAYLOAD_TOO_LARGE(
            "1104", ErrorCategory.VALIDATION, HttpStatus.PAYLOAD_TOO_LARGE, "error.web.payload-too-large"),
    /** 修改资源时没有提供当前版本。 */
    PRECONDITION_REQUIRED(
            "1105",
            ErrorCategory.CONFLICT,
            HttpStatus.PRECONDITION_REQUIRED,
            "error.web.precondition-required"),
    /** 请求提供的资源版本不能用于当前修改。 */
    PRECONDITION_FAILED(
            "1106",
            ErrorCategory.CONFLICT,
            HttpStatus.PRECONDITION_FAILED,
            "error.web.precondition-failed");

    private final String code;

    private final ErrorCategory category;

    private final HttpStatus httpStatus;

    private final String messageKey;

    HttpErrorCode(String code, ErrorCategory category, HttpStatus httpStatus, String messageKey) {
        this.code = code;
        this.category = category;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    /** @return 全局固定错误码 */
    @Override
    public String code() {
        return code;
    }

    /** @return 输入错误分类 */
    @Override
    public ErrorCategory category() {
        return category;
    }

    /** @return 默认国际化消息键 */
    @Override
    public String messageKey() {
        return messageKey;
    }

    /**
     * 返回当前失败使用的 HTTP 状态。
     *
     * @return 当前 HTTP 失败对应的响应状态
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
