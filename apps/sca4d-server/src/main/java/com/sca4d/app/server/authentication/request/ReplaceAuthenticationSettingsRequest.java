package com.sca4d.app.server.authentication.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 完整替换当前账号的动作认证因素数量和认证方法开关。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class ReplaceAuthenticationSettingsRequest {

    @Min(1)
    @Max(3)
    private int loginFactorCount;

    @Min(0)
    @Max(3)
    private int sensitiveActionFactorCount;

    @Min(1)
    private long reusableAuthenticationMaximumAgeSeconds;

    @NotEmpty
    @Valid
    private List<AuthenticationMethodSettingsRequest> methods = new ArrayList<>();

    /** 创建供 JSON 反序列化使用的空请求。 */
    public ReplaceAuthenticationSettingsRequest() {
    }

    public int getLoginFactorCount() {
        return loginFactorCount;
    }

    public void setLoginFactorCount(int loginFactorCount) {
        this.loginFactorCount = loginFactorCount;
    }

    public int getSensitiveActionFactorCount() {
        return sensitiveActionFactorCount;
    }

    public void setSensitiveActionFactorCount(int sensitiveActionFactorCount) {
        this.sensitiveActionFactorCount = sensitiveActionFactorCount;
    }

    public long getReusableAuthenticationMaximumAgeSeconds() {
        return reusableAuthenticationMaximumAgeSeconds;
    }

    public void setReusableAuthenticationMaximumAgeSeconds(
            long reusableAuthenticationMaximumAgeSeconds) {
        this.reusableAuthenticationMaximumAgeSeconds = reusableAuthenticationMaximumAgeSeconds;
    }

    public @NotNull List<AuthenticationMethodSettingsRequest> getMethods() {
        return List.copyOf(methods);
    }

    /** 使用 JSON 中的完整方法设置替换当前请求值；JSON 为 {@code null} 时保留空集合供校验器拒绝。 */
    public void setMethods(List<AuthenticationMethodSettingsRequest> methods) {
        this.methods = methods == null ? new ArrayList<>() : new ArrayList<>(methods);
    }
}
