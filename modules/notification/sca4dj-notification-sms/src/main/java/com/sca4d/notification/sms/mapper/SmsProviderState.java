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

    /** 创建 Provider 数据库状态。 */
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

    public long providerId() {
        return providerId;
    }

    /** 返回 code 方法提供的结果。 */
    public @NotNull String code() {
        return code;
    }

    /** 返回 type 方法提供的结果。 */
    public @NotNull String type() {
        return type;
    }

    public @NotNull String credentialRef() {
        return credentialRef;
    }

    public int priority() {
        return priority;
    }

    public boolean enabled() {
        return enabled;
    }

    /** 返回 version 方法提供的结果。 */
    public @NotNull Version version() {
        return version;
    }
}
