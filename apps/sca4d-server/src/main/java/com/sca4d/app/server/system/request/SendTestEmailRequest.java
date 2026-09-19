package com.sca4d.app.server.system.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 接收已保存模板测试发送使用的 Locale、变量和单个收件地址。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class SendTestEmailRequest {

    @NotBlank
    @Size(max = 35)
    private @Nullable String languageTag;

    @Valid
    @Req.NotNull
    @Size(max = 100)
    private @Nullable Map<@NotBlank String, @Req.NotNull @Size(max = 4096) String> variables;

    @NotBlank
    @Email
    @Size(max = 320)
    private @Nullable String recipient;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public SendTestEmailRequest() {
    }

    /** @return 测试渲染使用的规范 Locale */
    public @NotNull Locale locale() {
        Locale locale = Locale.forLanguageTag(Objects.requireNonNull(languageTag, "Locale 标签不能为空"));
        if (locale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Locale 标签必须包含语言");
        }
        return locale;
    }

    /** @param languageTag 测试渲染使用的 BCP 47 Locale 标签 */
    public void setLanguageTag(@Nullable String languageTag) {
        this.languageTag = languageTag;
    }

    /** @return 模板变量的不可修改副本 */
    public @NotNull Map<@NotNull String, @NotNull String> variables() {
        return Map.copyOf(Objects.requireNonNull(variables, "模板变量不能为空"));
    }

    /** @param variables 模板引用的变量 */
    public void setVariables(@Nullable Map<String, String> variables) {
        this.variables = variables == null ? null : Map.copyOf(variables);
    }

    /** @return 测试邮件收件地址 */
    public @NotNull String getRecipient() {
        return Objects.requireNonNull(recipient, "测试邮件收件地址不能为空");
    }

    /** @param recipient 测试邮件收件地址 */
    public void setRecipient(@Nullable String recipient) {
        this.recipient = recipient;
    }

    @Override
    public @NotNull String toString() {
        return "SendTestEmailRequest[languageTag=" + languageTag + ", recipient=protected]";
    }
}
