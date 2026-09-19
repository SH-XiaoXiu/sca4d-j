package com.sca4d.app.server.system.view;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.notification.sms.SmsProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回不包含 AccessKey 的 SMS Provider 配置。 */
@SuppressWarnings("unused")
public final class SmsProviderView {

    private final long providerId;

    private final String code;

    private final String type;

    private final String credentialRef;

    private final int priority;

    private final boolean enabled;

    private final Version revision;

    private SmsProviderView(SmsProvider provider) {
        this.providerId = provider.providerId();
        this.code = provider.code();
        this.type = provider.type();
        this.credentialRef = provider.credentialRef();
        this.priority = provider.priority();
        this.enabled = provider.enabled();
        this.revision = provider.version();
    }

    /** @return SMS Provider 的安全 HTTP 投影 */
    public static @NotNull SmsProviderView from(@NotNull SmsProvider provider) {
        return new SmsProviderView(Objects.requireNonNull(provider, "SMS Provider 不能为空"));
    }

    public long getProviderId() {
        return providerId;
    }

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getVersion() {
        return revision.value();
    }

    /** @return 生成当前完整 Provider 表示的版本 */
    @ETag
    public Version revision() {
        return revision;
    }
}
