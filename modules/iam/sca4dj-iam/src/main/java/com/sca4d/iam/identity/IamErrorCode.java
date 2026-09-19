package com.sca4d.iam.identity;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 表示默认 IAM 平台身份操作产生的固定错误码。 */
@ErrorCodeRange(start = 2000, end = 2099)
public enum IamErrorCode implements ErrorCode {
    /** 登录标识格式非法。 */
    IDENTIFIER_INVALID("2000", ErrorCategory.VALIDATION, "error.iam.identifier-invalid"),
    /** 同一认证域已经存在该登录标识。 */
    IDENTIFIER_EXISTS("2001", ErrorCategory.CONFLICT, "error.iam.identifier-exists"),
    /** 指定登录身份不存在或已经停用。 */
    SUBJECT_UNAVAILABLE("2002", ErrorCategory.NOT_FOUND, "error.iam.subject-unavailable"),
    /** 账号没有可用的平台业务主体。 */
    PRINCIPAL_UNAVAILABLE("2003", ErrorCategory.NOT_FOUND, "error.iam.principal-unavailable"),
    /** 身份事实已被其他调用方修改。 */
    CONCURRENT_CONFLICT("2004", ErrorCategory.CONFLICT, "error.iam.concurrent-conflict"),
    /** 登录标识不存在。 */
    IDENTIFIER_NOT_FOUND("2005", ErrorCategory.NOT_FOUND, "error.iam.identifier-not-found"),
    /** 应用没有安装该登录标识类型的规范化器。 */
    IDENTIFIER_TYPE_UNSUPPORTED("2006", ErrorCategory.VALIDATION, "error.iam.identifier-type-unsupported"),
    /** 账号不存在或已经停用。 */
    ACCOUNT_UNAVAILABLE("2007", ErrorCategory.NOT_FOUND, "error.iam.account-unavailable"),
    /** Principal 类型没有注册 Session Codec。 */
    PRINCIPAL_TYPE_UNREGISTERED("2008", ErrorCategory.VALIDATION, "error.iam.principal-type-unregistered"),
    /** 指定账号与业务主体关系不存在。 */
    RELATION_NOT_FOUND("2009", ErrorCategory.NOT_FOUND, "error.iam.relation-not-found"),
    /** 受保护账号不允许停用。 */
    PROTECTED_ACCOUNT("2010", ErrorCategory.CONFLICT, "error.iam.protected-account"),
    /** 当前登录账号不能停用自己。 */
    CURRENT_ACCOUNT("2011", ErrorCategory.CONFLICT, "error.iam.current-account");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    IamErrorCode(@NotNull String code, @NotNull ErrorCategory category, @NotNull String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    @Override
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    @Override
    @Contract(pure = true)
    public @NotNull ErrorCategory category() {
        return category;
    }

    @Override
    @Contract(pure = true)
    public @NotNull String messageKey() {
        return messageKey;
    }
}
