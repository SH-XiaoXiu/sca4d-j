package com.sca4d.iam.rbac;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 表示通用 RBAC 操作的固定失败类型。 */
@ErrorCodeRange(start = 2100, end = 2199)
public enum RbacErrorCode implements ErrorCode {
    /** 角色不存在于当前 Repository。 */
    ROLE_NOT_FOUND("2100", ErrorCategory.NOT_FOUND, "error.rbac.role-not-found"),
    /** 当前 Repository 已经存在同编码角色。 */
    ROLE_CODE_EXISTS("2101", ErrorCategory.CONFLICT, "error.rbac.role-code-exists"),
    /** 角色编码、动态国际化字段或状态不符合约束。 */
    ROLE_INVALID("2102", ErrorCategory.VALIDATION, "error.rbac.role-invalid"),
    /** 自定义角色仍被 Principal 使用。 */
    ROLE_IN_USE("2103", ErrorCategory.CONFLICT, "error.rbac.role-in-use"),
    /** 请求试图修改内置角色受保护的状态或关系。 */
    BUILT_IN_ROLE_PROTECTED("2104", ErrorCategory.CONFLICT, "error.rbac.built-in-role-protected"),
    /** Operation 未声明、重复、不是 ACTION 或为内部选择符。 */
    OPERATION_INVALID("2105", ErrorCategory.VALIDATION, "error.rbac.operation-invalid"),
    /** Principal 角色重复或包含其他 Repository 的角色。 */
    PRINCIPAL_ROLE_INVALID("2106", ErrorCategory.VALIDATION, "error.rbac.principal-role-invalid");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    RbacErrorCode(@NotNull String code, @NotNull ErrorCategory category, @NotNull String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    /**
     * 返回全局错误码。
     *
     * @return 全局错误码
     */
    @Override
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    /**
     * 返回 HTTP 映射使用的错误分类。
     *
     * @return 错误分类
     */
    @Override
    @Contract(pure = true)
    public @NotNull ErrorCategory category() {
        return category;
    }

    /**
     * 返回错误消息键。
     *
     * @return 国际化消息键
     */
    @Override
    @Contract(pure = true)
    public @NotNull String messageKey() {
        return messageKey;
    }
}
