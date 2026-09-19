package com.sca4d.app.server.system.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.spring.validation.Req;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 接收未保存 Email 模板、目标 Locale 和预览变量。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class PreviewEmailTemplateRequest {

    @Req.NotNull
    private @Nullable JsonNode content;

    @NotBlank
    @Size(max = 35)
    private @Nullable String languageTag;

    @Valid
    @Req.NotNull
    @Size(max = 100)
    private @Nullable Map<@NotBlank String, @Req.NotNull @Size(max = 4096) String> variables;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public PreviewEmailTemplateRequest() {
    }

    /** @return 未保存的完整多语言模板字段 */
    public @NotNull JsonNode getContent() {
        return Objects.requireNonNull(content, "统一通知模板内容不能为空").deepCopy();
    }

    /** @param content 未保存的完整多语言模板字段 */
    public void setContent(@Nullable JsonNode content) {
        this.content = content == null ? null : content.deepCopy();
    }

    /** @return 预览使用的规范 Locale */
    public @NotNull Locale locale() {
        Locale locale = Locale.forLanguageTag(Objects.requireNonNull(languageTag, "Locale 标签不能为空"));
        if (locale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Locale 标签必须包含语言");
        }
        return locale;
    }

    /** @param languageTag 预览使用的 BCP 47 Locale 标签 */
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
}
