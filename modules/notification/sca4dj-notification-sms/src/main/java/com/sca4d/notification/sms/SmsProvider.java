package com.sca4d.notification.sms;

import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一个由系统管理数据库维护的 SMS Provider 实例。 */
public final class SmsProvider {

    private final long providerId;

    private final @NotNull String code;

    private final @NotNull String type;

    private final @NotNull String credentialRef;

    private final int priority;

    private final boolean enabled;

    private final @NotNull Version version;

    /** 创建一个 SMS Provider 配置快照。 */
    public SmsProvider(
            long providerId,
            @NotNull String code,
            @NotNull String type,
            @NotNull String credentialRef,
            int priority,
            boolean enabled,
            @NotNull Version version) {
        if (providerId <= 0)
            throw new IllegalArgumentException("SMS Provider 标识必须为正数");
        this.providerId = providerId;
        this.code = required(code, "Provider 编码");
        this.type = required(type, "Provider 类型");
        this.credentialRef = required(credentialRef, "Provider 凭据引用");
        this.priority = priority;
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "Provider 版本不能为空");
    }

    /** @return Provider 全局标识 */
    @Contract(pure = true)
    public long providerId() {
        return providerId;
    }

    /** @return Provider 实例稳定编码 */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    /** @return 选择 `SmsTransport` 使用的 Provider 类型 */
    @Contract(pure = true)
    public @NotNull String type() {
        return type;
    }

    /** @return 部署配置中凭据集合的稳定引用 */
    @Contract(pure = true)
    public @NotNull String credentialRef() {
        return credentialRef;
    }

    /** @return 内置优先级选择器使用的升序值 */
    @Contract(pure = true)
    public int priority() {
        return priority;
    }

    /** @return Provider 是否参与新投递 */
    @Contract(pure = true)
    public boolean enabled() {
        return enabled;
    }

    /** @return Provider 当前乐观版本 */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }

    private static @NotNull String required(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").strip();
        if (checked.isEmpty() || checked.length() > 128)
            throw new IllegalArgumentException(name + "不合法");
        return checked;
    }
}
