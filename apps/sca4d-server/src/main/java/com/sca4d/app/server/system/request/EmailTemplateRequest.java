package com.sca4d.app.server.system.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 接收通知编码和完整多语言 Email 源码模板。 */
public final class EmailTemplateRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String notificationCode;

    @Req.NotNull
    @Size(min = 1, max = 100)
    private @Nullable Map<@NotBlank @Size(max = 35) String, @Valid EmailTemplateSourceRequest> locales;

    public EmailTemplateRequest() {
    }

    public @NotNull String getNotificationCode() {
        return Objects.requireNonNull(notificationCode, "通知编码不能为空");
    }

    public void setNotificationCode(@Nullable String notificationCode) {
        this.notificationCode = notificationCode;
    }

    public @NotNull Map<String, EmailTemplateSourceRequest> getLocales() {
        return Map.copyOf(Objects.requireNonNull(locales, "Email 模板 Locale 不能为空"));
    }

    public void setLocales(@Nullable Map<String, EmailTemplateSourceRequest> locales) {
        this.locales = locales == null ? null : new LinkedHashMap<>(locales);
    }
}
