package com.sca4d.core.api.gateway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/** 保存 Server 实例向 Gateway 提交的地址、readiness 路径和回调编码。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class GatewayRegistrationRequest {

    private final @Nullable String instanceId;

    private final @Nullable URI baseUrl;

    private final @Nullable String readinessPath;

    private final @NotNull Set<@NotNull String> callbackCodes;

    /**
     * 创建 Server 实例注册请求。
     *
     * @param instanceId Server 进程在 Gateway 注册表中的唯一标识
     * @param baseUrl Gateway 可以直接访问的 Server HTTP(S) Origin
     * @param readinessPath 相对 Server Origin 的 readiness 路径
     * @param callbackCodes 当前实例可以执行的回调编码；null 按空集合处理
     */
    @JsonCreator
    public GatewayRegistrationRequest(
            @JsonProperty("instanceId") @Nullable String instanceId,
            @JsonProperty("baseUrl") @Nullable URI baseUrl,
            @JsonProperty("readinessPath") @Nullable String readinessPath,
            @JsonProperty("callbackCodes") @Nullable Set<String> callbackCodes) {
        this.instanceId = instanceId;
        this.baseUrl = baseUrl;
        this.readinessPath = readinessPath;
        this.callbackCodes = callbackCodes == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(callbackCodes));
    }

    /** @return Server 进程在 Gateway 注册表中的唯一标识 */
    public @Nullable String getInstanceId() {
        return instanceId;
    }

    /** @return Gateway 可以直接访问的 Server HTTP(S) Origin */
    public @Nullable URI getBaseUrl() {
        return baseUrl;
    }

    /** @return 相对 Server Origin 的 readiness 路径 */
    public @Nullable String getReadinessPath() {
        return readinessPath;
    }

    /** @return 当前实例可以执行的不可修改回调编码集合 */
    public @NotNull @Unmodifiable Set<@NotNull String> getCallbackCodes() {
        return callbackCodes;
    }
}
