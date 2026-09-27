package com.sca4d.app.server.platform.request;

import com.sca4d.auth.federation.FederatedProviderStatus;
import com.sca4d.core.spring.validation.Req;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 接收联邦 Provider 状态修改请求。 */
public class ProviderStatusRequest {
    @Req.NotNull
    private FederatedProviderStatus status;

    /** 创建空请求供 JSON 绑定。 */
    public ProviderStatusRequest() {
    }

    /** @return 目标状态 */
    public @NotNull FederatedProviderStatus getStatus() {
        return Objects.requireNonNull(status, "Provider 状态不能为空");
    }

    /** @param value 目标状态 */
    public void setStatus(FederatedProviderStatus value) {
        status = value;
    }
}
