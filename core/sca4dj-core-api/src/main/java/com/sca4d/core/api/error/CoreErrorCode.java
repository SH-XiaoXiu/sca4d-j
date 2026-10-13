package com.sca4d.core.api.error;

/** 列出 Core 固定检查可能产生的错误码（Core Error Code）。 */
@ErrorCodeRange(start = 1000, end = 1099)
public enum CoreErrorCode implements ErrorCode {
    /** 输入或配置不满足 Core 不变量。 */
    VALIDATION_FAILED("1000", ErrorCategory.VALIDATION, "error.core.validation-failed"),
    /** 调用尚未完成认证。 */
    AUTHENTICATION_REQUIRED("1001", ErrorCategory.AUTHENTICATION, "error.core.authentication-required"),
    /** 当前业务主体没有目标权限。 */
    PERMISSION_DENIED("1002", ErrorCategory.PERMISSION, "error.core.permission-denied"),
    /** 目标对象不存在。 */
    NOT_FOUND("1003", ErrorCategory.NOT_FOUND, "error.core.not-found"),
    /** 当前状态或乐观版本发生冲突。 */
    CONFLICT("1004", ErrorCategory.CONFLICT, "error.core.conflict"),
    /** 调用超过允许频率。 */
    RATE_LIMITED("1005", ErrorCategory.RATE_LIMIT, "error.core.rate-limited"),
    /** 同一幂等请求仍在处理中。 */
    IDEMPOTENCY_IN_PROGRESS("1006", ErrorCategory.CONFLICT, "error.core.idempotency-in-progress"),
    /** 幂等键已经绑定不同请求。 */
    IDEMPOTENCY_KEY_CONFLICT("1007", ErrorCategory.CONFLICT, "error.core.idempotency-key-conflict"),
    /** 客户端版本声明格式错误。 */
    CLIENT_VERSION_INVALID("1008", ErrorCategory.VALIDATION, "error.core.client-version-invalid"),
    /** 客户端无法使用请求的接口契约。 */
    CLIENT_INCOMPATIBLE("1009", ErrorCategory.CONFLICT, "error.core.client-incompatible"),
    /** 外部服务暂时不可用。 */
    UNAVAILABLE("1098", ErrorCategory.UNAVAILABLE, "error.core.unavailable"),
    /** Core 无法归类的内部失败。 */
    INTERNAL("1099", ErrorCategory.INTERNAL, "error.core.internal");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    CoreErrorCode(String code, ErrorCategory category, String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    /**
     * 返回全局数值字符串。
     *
     * @return 全局数值字符串
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 返回错误分类。
     *
     * @return 错误分类
     */
    @Override
    public ErrorCategory category() {
        return category;
    }

    /**
     * 返回国际化消息键。
     *
     * @return 国际化消息键
     */
    @Override
    public String messageKey() {
        return messageKey;
    }
}
