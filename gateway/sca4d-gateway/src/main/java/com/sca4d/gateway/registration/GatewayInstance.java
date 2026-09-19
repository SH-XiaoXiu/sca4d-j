package com.sca4d.gateway.registration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 保存一个 Server 实例当前租约、地址、回调声明和 readiness 状态。 */
public final class GatewayInstance {

    private final @NotNull String instanceId;

    private final @NotNull String leaseId;

    private final @NotNull URI baseUrl;

    private final @NotNull String readinessPath;

    private final @NotNull Set<@NotNull String> callbackCodes;

    private volatile @NotNull Instant leaseExpiresAt;

    private volatile boolean ready;

    /** 创建一个尚未通过 readiness 检查的新租约实例。 */
    GatewayInstance(
            @NotNull String instanceId,
            @NotNull String leaseId,
            @NotNull URI baseUrl,
            @NotNull String readinessPath,
            @NotNull Set<@NotNull String> callbackCodes,
            @NotNull Instant leaseExpiresAt) {
        this.instanceId = instanceId;
        this.leaseId = leaseId;
        this.baseUrl = baseUrl;
        this.readinessPath = readinessPath;
        this.callbackCodes = Set.copyOf(callbackCodes);
        this.leaseExpiresAt = leaseExpiresAt;
    }

    /** @return Server 实例唯一标识 */
    public @NotNull String instanceId() {
        return instanceId;
    }

    /** @return 当前注册的随机租约 ID */
    public @NotNull String leaseId() {
        return leaseId;
    }

    /** @return Gateway 可以访问的 Server HTTP(S) Origin */
    public @NotNull URI baseUrl() {
        return baseUrl;
    }

    /** @return Gateway 主动检查的 readiness 路径 */
    public @NotNull String readinessPath() {
        return readinessPath;
    }

    /** @return 当前实例声明的不可修改回调编码集合 */
    public @NotNull @Unmodifiable Set<@NotNull String> callbackCodes() {
        return callbackCodes;
    }

    /** @return 最后一次成功注册或续租计算出的过期时间 */
    public @NotNull Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }

    /** @return 最近一次主动 readiness 检查是否成功 */
    public boolean ready() {
        return ready;
    }

    /** @param expiresAt 续租成功后的新过期时间 */
    void renew(@NotNull Instant expiresAt) {
        leaseExpiresAt = expiresAt;
    }

    /** @param value 最近一次 readiness 检查结果 */
    void ready(boolean value) {
        ready = value;
    }

    /** @return 租约未过期且最近一次 readiness 检查成功时为 true */
    @Contract(pure = true)
    boolean routable(@NotNull Instant now) {
        return ready && leaseExpiresAt.isAfter(now);
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof GatewayInstance instance
                        && instanceId.equals(instance.instanceId)
                        && leaseId.equals(instance.leaseId);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(instanceId, leaseId);
    }
}
