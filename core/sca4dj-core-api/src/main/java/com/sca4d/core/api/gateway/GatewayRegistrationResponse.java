package com.sca4d.core.api.gateway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Gateway 为 Server 实例注册分配的租约标识。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class GatewayRegistrationResponse {

    private final @NotNull String leaseId;

    /**
     * 创建实例注册响应。
     *
     * @param leaseId 续租和注销必须原样提交的随机租约标识
     */
    @JsonCreator
    public GatewayRegistrationResponse(@JsonProperty("leaseId") @NotNull String leaseId) {
        String checkedLeaseId = Objects.requireNonNull(leaseId, "Gateway 租约 ID 不能为空");
        if (checkedLeaseId.isBlank()) {
            throw new IllegalArgumentException("Gateway 租约 ID 不能为空白");
        }
        this.leaseId = checkedLeaseId;
    }

    /** @return Gateway 为本次注册分配的租约标识 */
    public @NotNull String getLeaseId() {
        return leaseId;
    }
}
