package com.sca4d.app.server.iot.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/** 接收 Product 可修改的接入方式和兼容协议。 */
@SuppressWarnings("unused")
public class IotProductChangeRequest {

    @Req.NotNull
    @Size(max = 16)
    private @Nullable Set<@NotBlank @Size(max = 128) String> accessMethods;

    @NotEmpty
    @Size(max = 16)
    private @Nullable Set<@NotBlank @Size(max = 128) String> protocolCodes;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotProductChangeRequest() {
    }

    /** @return 完整允许接入方式 */
    public @NotNull Set<String> getAccessMethods() {
        return Set.copyOf(Objects.requireNonNull(accessMethods));
    }

    /** @param accessMethods 完整允许接入方式 */
    public void setAccessMethods(@Nullable Set<String> accessMethods) {
        this.accessMethods = accessMethods == null ? null : Set.copyOf(accessMethods);
    }

    /** @return 完整兼容协议 code */
    public @NotNull Set<String> getProtocolCodes() {
        return Set.copyOf(Objects.requireNonNull(protocolCodes));
    }

    /** @param protocolCodes 完整兼容协议 code */
    public void setProtocolCodes(@Nullable Set<String> protocolCodes) {
        this.protocolCodes = protocolCodes == null ? null : Set.copyOf(protocolCodes);
    }
}
