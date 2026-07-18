package com.sca4d.role;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/** 角色模块错误码。 */
@CodeRange(start = 3500, end = 3599)
public enum RoleErrorCode implements BizErrorCode {
    /** 角色不属于目标租户。 */
    ROLE_NOT_IN_TENANT(3500, "error.role.not-in-tenant", ResponseCode.BAD_REQUEST),
    /** 角色不属于平台范围。 */
    ROLE_NOT_PLATFORM(3501, "error.role.not-platform", ResponseCode.BAD_REQUEST),
    /** 角色编码由内置角色保留。 */
    ROLE_BUILT_IN_CODE_RESERVED(3502, "error.role.built-in-code-reserved", ResponseCode.BAD_REQUEST),
    /** 内置角色禁止删除。 */
    ROLE_BUILT_IN_DELETE_FORBIDDEN(3503, "error.role.built-in-delete-forbidden", ResponseCode.BAD_REQUEST),
    /** 内置角色权限禁止手动修改。 */
    ROLE_BUILT_IN_PERMISSIONS_READONLY(3504, "error.role.built-in-permissions-readonly", ResponseCode.BAD_REQUEST),
    /** 当前范围已存在相同角色编码。 */
    ROLE_CODE_EXISTS(3505, "error.role.code-exists", ResponseCode.CONFLICT),
    /** 角色编码格式无效。 */
    ROLE_CODE_INVALID(3506, "error.role.code-invalid", ResponseCode.BAD_REQUEST),
    /** 角色仍被 Principal 使用。 */
    ROLE_IN_USE(3507, "error.role.in-use", ResponseCode.CONFLICT),
    /** 角色名称无效。 */
    ROLE_NAME_INVALID(3508, "error.role.name-invalid", ResponseCode.BAD_REQUEST),
    /** 角色不存在或不在当前管理范围。 */
    ROLE_NOT_FOUND(3509, "error.role.not-found", ResponseCode.NOT_FOUND),
    /** 权限不在角色的可分配范围内。 */
    ROLE_PERMISSION_OUT_OF_SCOPE(3510, "error.role.permission-out-of-scope", ResponseCode.BAD_REQUEST),
    /** 租户成员的角色分配已被其他请求修改。 */
    TENANT_MEMBER_ROLES_CHANGED(3511, "error.role.tenant-member-roles-changed", ResponseCode.CONFLICT),
    /** 平台 Principal 的角色分配已被其他请求修改。 */
    PLATFORM_PRINCIPAL_ROLES_CHANGED(3512, "error.role.platform-principal-roles-changed", ResponseCode.CONFLICT),
    /** 角色已被其他请求修改。 */
    ROLE_CHANGED(3513, "error.role.changed", ResponseCode.CONFLICT);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    RoleErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
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
