package com.sca4d.app.server.system.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 接收一份未保存 Email 源码、目标 Locale 和预览变量。 */
public class PreviewEmailTemplateRequest {

    @Valid
    @Req.NotNull
    private @Nullable EmailTemplateSourceRequest source;

    @NotBlank
    @Size(max = 35)
    private @Nullable String languageTag;

    @Valid
    @Req.NotNull
    @Size(max = 100)
    private @Nullable Map<@NotBlank String, @Req.NotNull @Size(max = 4096) String> variables;

    /** 创建空的请求对象，供 JSON 绑定使用。 */
    public PreviewEmailTemplateRequest() {
    }

    /** @return Email 模板源码 */
    public @NotNull EmailTemplateSourceRequest getSource() {
        return Objects.requireNonNull(source, "Email 模板源码不能为空");
    }

    /** @param source Email 模板源码 */
    public void setSource(@Nullable EmailTemplateSourceRequest source) {
        this.source = source == null ? null : source.copy();
    }

    /** @return 校验后的目标 Locale */
    public @NotNull Locale locale() {
        Locale locale = Locale.forLanguageTag(Objects.requireNonNull(languageTag, "Locale 标签不能为空"));
        if (locale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Locale 标签必须包含语言");
        }
        return locale;
    }

    /** @param languageTag BCP 47 Locale 标签 */
    public void setLanguageTag(@Nullable String languageTag) {
        this.languageTag = languageTag;
    }

    /** @return 不可修改的渲染变量 */
    public @NotNull Map<String, String> variables() {
        return Map.copyOf(Objects.requireNonNull(variables, "模板变量不能为空"));
    }

    /** @param variables 渲染变量 */
    public void setVariables(@Nullable Map<String, String> variables) {
        this.variables = variables == null ? null : Map.copyOf(variables);
    }
}
