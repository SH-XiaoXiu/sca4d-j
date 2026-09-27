package com.sca4d.notification.inbox.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存一种站内信的完整多语言 Tiptap JSON 模板、状态和版本。
 */
public final class InboxTemplate {

    private final long templateId;

    private final @NotNull TreeCode code;

    private final @NotNull JsonNode content;

    private final boolean enabled;

    private final @NotNull Version version;

    /**
     * 创建一项已从数据库恢复的站内信模板。
     */
    public InboxTemplate(
            long templateId,
            @NotNull TreeCode code,
            @NotNull JsonNode content,
            boolean enabled,
            @NotNull Version version) {
        if (templateId <= 0) {
            throw new IllegalArgumentException("站内信模板标识必须为正数");
        }
        this.templateId = templateId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.content = Objects.requireNonNull(content, "站内信模板内容不能为空").deepCopy();
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "站内信模板版本不能为空");
    }

    /**
     * @return 模板全局标识
     */
    @Contract(pure = true)
    public long templateId() {
        return templateId;
    }

    /**
     * @return 对应的稳定通知编码
     */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * @return Locale 到 title/body Tiptap 文档的独立 JSON 副本
     */
    @Contract(pure = true)
    public @NotNull JsonNode content() {
        return content.deepCopy();
    }

    /**
     * @return 是否参与实际渠道渲染
     */
    @Contract(pure = true)
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return 当前乐观版本
     */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
