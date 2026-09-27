package com.sca4d.app.server.platform.request;

import com.sca4d.core.spring.validation.Req;
import com.sca4d.iam.rbac.RoleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 Platform 角色状态修改请求。 */
@Schema(name = "UpdatePlatformRoleStatusRequest")
public class UpdateRoleStatusRequest {

    @Req.NotNull
    private @Nullable RoleStatus status;

    /** 创建供 JSON 反序列化填充的空请求。 */
    public UpdateRoleStatusRequest() {
    }

    /**
     * 返回新角色状态。
     * @return 新角色状态
     */
    public @NotNull RoleStatus getStatus() {
        return Objects.requireNonNull(status, "角色状态不能为空");
    }

    /**
     * 设置新角色状态。
     * @param status 新角色状态
     */
    public void setStatus(@Nullable RoleStatus status) {
        this.status = status;
    }
}
