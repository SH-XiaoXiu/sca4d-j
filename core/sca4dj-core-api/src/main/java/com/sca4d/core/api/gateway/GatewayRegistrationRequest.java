package com.sca4d.core.api.gateway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 保存 Server 实例向 Gateway 提交的地址、readiness 路径和回调编码。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class GatewayRegistrationRequest {

    private final @NotNull Map<String, Set<String>> apiRequirements;

    private final @NotNull Set<@NotNull String> callbackCodes;

    private final @NotNull Set<String> capabilities;

    private final @Nullable String instanceId;

    private final @Nullable String protocolFingerprint;

    private final @Nullable String readinessPath;

    private final @Nullable URI baseUrl;

    /**
     * 创建 Server 实例注册请求。
     *
     * @param instanceId Server 进程在 Gateway 注册表中的唯一标识
     * @param baseUrl Gateway 可以直接访问的 Server HTTP(S) Origin
     * @param readinessPath 相对 Server Origin 的 readiness 路径
     * @param protocolFingerprint Server 启动时确定的 Gateway 协议名称摘要
     * @param callbackCodes 当前实例可以执行的回调编码；null 按空集合处理
     */
    @JsonCreator
    public GatewayRegistrationRequest(
            @JsonProperty("instanceId") @Nullable String instanceId,
            @JsonProperty("baseUrl") @Nullable URI baseUrl,
            @JsonProperty("readinessPath") @Nullable String readinessPath,
            @JsonProperty("protocolFingerprint") @Nullable String protocolFingerprint,
            @JsonProperty("callbackCodes") @Nullable Set<String> callbackCodes,
            @JsonProperty("capabilities") @Nullable Set<String> capabilities,
            @JsonProperty("apiRequirements") @Nullable Map<String, Set<String>> apiRequirements) {
        this.instanceId = instanceId;
        this.baseUrl = baseUrl;
        this.readinessPath = readinessPath;
        this.protocolFingerprint = protocolFingerprint;
        this.callbackCodes = callbackCodes == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(callbackCodes));
        this.capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        this.apiRequirements = apiRequirements == null
                ? Map.of()
                : apiRequirements.entrySet()
                        .stream()
                        .collect(
                                Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
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

    /** @return Server 启动时确定的 Gateway 协议名称摘要 */
    public @Nullable String getProtocolFingerprint() {
        return protocolFingerprint;
    }

    /** @return 当前实例可以执行的不可修改回调编码集合 */
    public @NotNull @Unmodifiable Set<@NotNull String> getCallbackCodes() {
        return callbackCodes;
    }

    /**
     * 当前实例实际启用的能力，不代表业务权限。
     *
     * @return 当前实例实际启用的能力，不代表业务权限
     */
    public @NotNull @Unmodifiable Set<String> getCapabilities() {
        return capabilities;
    }

    /**
     * 全部已声明的路径能力要求，包含当前实例停用的能力。
     *
     * @return 全部已声明的路径能力要求，包含当前实例停用的能力
     */
    public @NotNull @Unmodifiable Map<String, Set<String>> getApiRequirements() {
        return apiRequirements;
    }
}
