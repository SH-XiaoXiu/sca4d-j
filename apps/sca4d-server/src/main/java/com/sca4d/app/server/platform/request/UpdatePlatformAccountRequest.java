package com.sca4d.app.server.platform.request;

import com.sca4d.core.spring.validation.Req;
import com.sca4d.iam.identity.IdentityStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 Platform Account 可以修改的状态字段。 */
public final class UpdatePlatformAccountRequest {

    @Req.NotNull
    private @Nullable IdentityStatus status;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public UpdatePlatformAccountRequest() {
    }

    /**
     * 返回新账号状态。
     *
     * @return 新账号状态
     */
    public @NotNull IdentityStatus getStatus() {
        return Objects.requireNonNull(status, "账号状态不能为空");
    }

    /**
     * 设置新账号状态。
     *
     * @param status 新账号状态
     */
    public void setStatus(@Nullable IdentityStatus status) {
        this.status = status;
    }
}
