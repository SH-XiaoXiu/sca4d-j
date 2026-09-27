package com.sca4d.app.server.system.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.notification.sms.SmsProviderBinding;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/** 返回统一通知到 SMS Provider 模板的传输绑定。 */
@SuppressWarnings("unused")
public class SmsBindingView {

    private final long bindingId;

    private final long providerId;

    private final String notificationCode;

    private final String route;

    private final String languageTag;

    private final String signName;

    private final String providerTemplateCode;

    private final Map<String, String> parameterMapping;

    private final boolean enabled;

    private final Version revision;

    private SmsBindingView(SmsProviderBinding binding) {
        this.bindingId = binding.bindingId();
        this.providerId = binding.providerId();
        this.notificationCode = binding.code().format(SLASH);
        this.route = binding.route().value();
        this.languageTag = binding.locale().toLanguageTag();
        this.signName = binding.signName();
        this.providerTemplateCode = binding.providerTemplateCode();
        this.parameterMapping = binding.parameterMapping();
        this.enabled = binding.enabled();
        this.revision = binding.version();
    }

    /** @return SMS Provider 绑定的 HTTP 投影 */
    public static @NotNull SmsBindingView from(@NotNull SmsProviderBinding binding) {
        return new SmsBindingView(Objects.requireNonNull(binding, "SMS Provider 绑定不能为空"));
    }

    public long getBindingId() {
        return bindingId;
    }

    public long getProviderId() {
        return providerId;
    }

    public String getNotificationCode() {
        return notificationCode;
    }

    public String getRoute() {
        return route;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public String getSignName() {
        return signName;
    }

    public String getProviderTemplateCode() {
        return providerTemplateCode;
    }

    public @NotNull Map<String, String> getParameterMapping() {
        return Map.copyOf(parameterMapping);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getVersion() {
        return revision.value();
    }

    /** @return 生成当前完整绑定表示的版本 */
    @ETag
    public Version revision() {
        return revision;
    }
}
