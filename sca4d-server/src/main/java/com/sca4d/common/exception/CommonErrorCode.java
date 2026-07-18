package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;

/**
 * common 层错误码。
 *
 * <p>该枚举是 common 层对 {@link BizErrorCode} 的基础实现。框架内部可预期失败都应使用这里的错误码；
 * 业务模块需要定义自己的错误码时，同样实现 {@link BizErrorCode}，不要直接把 {@link ResponseCode} 当作业务错误码目录项。
 */
@CodeRange(start = 0, end = 1999)
public enum CommonErrorCode implements BizErrorCode {
    /** 操作成功。 */
    SUCCESS(0, "error.common.success", ResponseCode.SUCCESS),
    /** 请求参数错误。 */
    BAD_REQUEST(1000, "error.common.bad-request", ResponseCode.BAD_REQUEST),
    /** 未登录或认证失效。 */
    UNAUTHORIZED(1001, "error.common.unauthorized", ResponseCode.UNAUTHORIZED),
    /** 无权限。 */
    FORBIDDEN(1002, "error.common.forbidden", ResponseCode.FORBIDDEN),
    /** 资源不存在。 */
    NOT_FOUND(1003, "error.common.not-found", ResponseCode.NOT_FOUND),
    /** 请求方法不允许。 */
    METHOD_NOT_ALLOWED(1004, "error.common.method-not-allowed", ResponseCode.METHOD_NOT_ALLOWED),
    /** 资源冲突。 */
    CONFLICT(1005, "error.common.conflict", ResponseCode.CONFLICT),
    /** 请求体过大。 */
    PAYLOAD_TOO_LARGE(1006, "error.common.payload-too-large", ResponseCode.PAYLOAD_TOO_LARGE),
    /** 媒体类型不支持。 */
    UNSUPPORTED_MEDIA_TYPE(1007, "error.common.unsupported-media-type", ResponseCode.UNSUPPORTED_MEDIA_TYPE),
    /** 请求过多。 */
    TOO_MANY_REQUESTS(1008, "error.common.too-many-requests", ResponseCode.TOO_MANY_REQUESTS),
    /** 数据并发修改冲突。 */
    CONCURRENCY_CONFLICT(1009, "error.common.concurrency-conflict", ResponseCode.CONFLICT),
    /** 锁繁忙。 */
    LOCK_BUSY(1010, "error.common.lock-busy", ResponseCode.CONFLICT),
    /** 缺少幂等键。 */
    IDEMPOTENCY_KEY_REQUIRED(1011, "error.common.idempotency-key-required", ResponseCode.BAD_REQUEST),
    /** 幂等请求正在处理中。 */
    IDEMPOTENCY_IN_PROGRESS(1012, "error.common.idempotency-in-progress", ResponseCode.CONFLICT),
    /** 幂等键冲突。 */
    IDEMPOTENCY_KEY_CONFLICT(1013, "error.common.idempotency-key-conflict", ResponseCode.CONFLICT),
    /** 资源访问表达式没有解析出资源主键。 */
    ACCESS_RESOURCE_ID_REQUIRED(1014, "error.access.resource-id-required", ResponseCode.FORBIDDEN),
    /** 当前会话没有可用的 DEFAULT Principal。 */
    AUTHENTICATION_DEFAULT_PRINCIPAL_UNAVAILABLE(
            1015, "error.authentication.default-principal-unavailable", ResponseCode.FORBIDDEN),
    /** 当前部署没有认证实现。 */
    AUTHENTICATION_IMPLEMENTATION_UNAVAILABLE(
            1016, "error.authentication.implementation-unavailable", ResponseCode.UNAUTHORIZED),
    /** 当前会话没有指定 Principal 的授权。 */
    AUTHENTICATION_PRINCIPAL_AUTHORIZATION_MISSING(
            1017, "error.authentication.principal-authorization-missing", ResponseCode.FORBIDDEN),
    /** 等待分布式锁时线程被中断。 */
    LOCK_INTERRUPTED(1018, "error.lock.interrupted", ResponseCode.CONFLICT),
    /** 当前部署没有权限检查实现。 */
    PERMISSION_CHECKER_UNAVAILABLE(1019, "error.permission.checker-unavailable", ResponseCode.FORBIDDEN),
    /** 当前 Principal 缺少指定权限。 */
    PERMISSION_MISSING_PERMISSION(1020, "error.permission.missing-permission", ResponseCode.FORBIDDEN),
    /** 当前 Principal 缺少指定角色。 */
    PERMISSION_MISSING_ROLE(1021, "error.permission.missing-role", ResponseCode.FORBIDDEN),
    /** DEFAULT Principal 不接受外部选择参数。 */
    PRINCIPAL_SELECTION_DEFAULT_EXTERNAL_INPUT_FORBIDDEN(
            1022, "error.principal-selection.default-external-input-forbidden", ResponseCode.FORBIDDEN),
    /** Principal 选择表达式无法求值。 */
    PRINCIPAL_SELECTION_EXPRESSION_INVALID(
            1023, "error.principal-selection.expression-invalid", ResponseCode.FORBIDDEN),
    /** Principal 选择参数不是数字。 */
    PRINCIPAL_SELECTION_NUMBER_REQUIRED(
            1024, "error.principal-selection.number-required", ResponseCode.FORBIDDEN),
    /** 接口缺少对应的 PrincipalSelection。 */
    PRINCIPAL_SELECTION_REQUIRED(1025, "error.principal-selection.required", ResponseCode.FORBIDDEN),
    /** Principal 选择参数使用了集合或数组。 */
    PRINCIPAL_SELECTION_SCALAR_REQUIRED(
            1026, "error.principal-selection.scalar-required", ResponseCode.FORBIDDEN),
    /** 当前会话无权订阅指定实时目标。 */
    REALTIME_SUBSCRIPTION_FORBIDDEN(
            1027, "error.realtime.subscription-forbidden", ResponseCode.FORBIDDEN),
    /** 当前请求缺少入口应当完成装配的认证上下文。 */
    AUTHENTICATION_CONTEXT_INCOMPLETE(
            1028, "error.authentication.context-incomplete", ResponseCode.FORBIDDEN),
    /** 外部服务异常。 */
    EXTERNAL_SERVICE_ERROR(1097, "error.common.external-service", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 内部服务异常。 */
    INTERNAL_SERVICE_ERROR(1098, "error.common.internal-service", ResponseCode.INTERNAL_SERVICE_ERROR),
    /** 未知服务异常。 */
    SERVER_ERROR(1099, "error.common.server", ResponseCode.SERVER_ERROR);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    CommonErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    /**
     * 获取数值错误码。
     *
     * @return 错误码数值
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 获取错误消息资源键。
     *
     * @return HTTP 或消息响应按请求语言解析的消息资源键
     */
    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    /**
     * 获取响应分类。
     *
     * @return 响应分类
     */
    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
