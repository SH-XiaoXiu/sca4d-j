package com.sca4d.tenant;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/** 租户模块错误码。 */
@CodeRange(start = 3600, end = 3699)
public enum TenantErrorCode implements BizErrorCode {
    /** 租户不存在。 */
    TENANT_NOT_FOUND(3600, "error.tenant.not-found", ResponseCode.NOT_FOUND),
    /** 租户编码已存在。 */
    TENANT_CODE_EXISTS(3601, "error.tenant.code-exists", ResponseCode.CONFLICT),
    /** 租户当前不可用。 */
    TENANT_UNAVAILABLE(3602, "error.tenant.unavailable", ResponseCode.FORBIDDEN),
    /** Account 所属 Subject 已经加入目标租户。 */
    TENANT_MEMBER_EXISTS(3603, "error.tenant.member-exists", ResponseCode.CONFLICT),
    /** 租户成员不存在。 */
    TENANT_MEMBER_NOT_FOUND(3604, "error.tenant.member-not-found", ResponseCode.NOT_FOUND),
    /** TENANT Principal 不属于当前 Subject。 */
    FOREIGN_TENANT_PRINCIPAL(3605, "error.tenant.foreign-principal", ResponseCode.FORBIDDEN),
    /** 选择 TENANT Principal 时未提供租户。 */
    TENANT_ID_REQUIRED(3606, "error.tenant.id-required", ResponseCode.BAD_REQUEST),
    /** 当前 Subject 没有可用的目标租户成员关系。 */
    TENANT_MEMBERSHIP_UNAVAILABLE(3607, "error.tenant.membership-unavailable", ResponseCode.FORBIDDEN),
    /** 当前接口禁止使用 TENANT Principal。 */
    TENANT_PRINCIPAL_NOT_ALLOWED(3608, "error.tenant.principal-not-allowed", ResponseCode.FORBIDDEN),
    /** 租户访问规则包含无效或重复租户。 */
    TENANT_ACCESS_INVALID_RULE(3609, "error.tenant.access-invalid-rule", ResponseCode.BAD_REQUEST),
    /** 租户编码格式无效。 */
    TENANT_CODE_INVALID(3610, "error.tenant.code-invalid", ResponseCode.BAD_REQUEST),
    /** 租户编码未填写。 */
    TENANT_CODE_REQUIRED(3611, "error.tenant.code-required", ResponseCode.BAD_REQUEST),
    /** 租户名称未填写。 */
    TENANT_NAME_REQUIRED(3612, "error.tenant.name-required", ResponseCode.BAD_REQUEST),
    /** 租户访问规则已被其他请求修改。 */
    TENANT_ACCESS_CHANGED(3613, "error.tenant.access-changed", ResponseCode.CONFLICT),
    /** 租户成员状态已被其他请求修改。 */
    TENANT_MEMBER_STATUS_CHANGED(3614, "error.tenant.member-status-changed", ResponseCode.CONFLICT),
    /** 租户已被其他请求修改。 */
    TENANT_CHANGED(3615, "error.tenant.changed", ResponseCode.CONFLICT);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    TenantErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
