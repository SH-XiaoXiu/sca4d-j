package com.sca4d.notification.sms.mapper;

import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 SMS Provider Mapper 返回的数据库状态。 */
public final class SmsProviderState {
    private final long providerId;

    private final @NotNull String code;

    private final @NotNull String type;

    private final @NotNull String credentialRef;

    private final int priority;

    private final boolean enabled;

    private final @NotNull Version version;

    /**
     * @param providerId Provider 数据库标识
     * @param code Provider 配置编码
     * @param type Provider 类型
     * @param credentialRef 凭据引用
     * @param priority 路由优先级
     * @param enabled 是否允许投递
     * @param version Provider 配置版本
     */
    public SmsProviderState(
            long providerId,
            @NotNull String code,
            @NotNull String type,
            @NotNull String credentialRef,
            int priority,
            boolean enabled,
            @NotNull Version version) {
        this.providerId = providerId;
        this.code = Objects.requireNonNull(code, "Provider 编码不能为空");
        this.type = Objects.requireNonNull(type, "Provider 类型不能为空");
        this.credentialRef = Objects.requireNonNull(credentialRef, "凭据引用不能为空");
        this.priority = priority;
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "Provider 版本不能为空");
    }

    /** @return Provider 数据库标识 */
    public long providerId() {
        return providerId;
    }

    /** @return Provider 编码 */
    public @NotNull String code() {
        return code;
    }

    /** @return Provider 类型 */
    public @NotNull String type() {
        return type;
    }

    /** @return 凭据引用 */
    public @NotNull String credentialRef() {
        return credentialRef;
    }

    /** @return 路由优先级 */
    public int priority() {
        return priority;
    }

    /** @return 是否允许投递 */
    public boolean enabled() {
        return enabled;
    }

    /** @return Provider 配置版本 */
    public @NotNull Version version() {
        return version;
    }
}
