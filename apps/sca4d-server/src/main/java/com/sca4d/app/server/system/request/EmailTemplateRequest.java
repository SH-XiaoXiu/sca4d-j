package com.sca4d.app.server.system.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收通知编码和平台无关的完整 Tiptap JSON 模板。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class EmailTemplateRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String notificationCode;

    @Req.NotNull
    private @Nullable JsonNode content;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public EmailTemplateRequest() {
    }

    /** @return 渠道无关通知编码 */
    public @NotNull String getNotificationCode() {
        return Objects.requireNonNull(notificationCode, "通知编码不能为空");
    }

    /** @param notificationCode 渠道无关通知编码 */
    public void setNotificationCode(@Nullable String notificationCode) {
        this.notificationCode = notificationCode;
    }

    /** @return Locale 到 title/body ProseMirror 文档的完整 JSON */
    public @NotNull JsonNode getContent() {
        return Objects.requireNonNull(content, "统一通知模板内容不能为空").deepCopy();
    }

    /** @param content Locale 到 title/body ProseMirror 文档的完整 JSON */
    public void setContent(@Nullable JsonNode content) {
        this.content = content == null ? null : content.deepCopy();
    }
}
