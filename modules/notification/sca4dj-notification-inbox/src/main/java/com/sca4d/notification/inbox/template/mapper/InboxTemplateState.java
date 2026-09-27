package com.sca4d.notification.inbox.template.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存 Inbox Template Mapper 从数据库读取的 JSON 文本状态。
 */
public class InboxTemplateState {

    private final long templateId;

    private final @NotNull TreeCode code;

    private final @NotNull String contentJson;

    private final boolean enabled;

    private final @NotNull Version version;

    /**
     * @param templateId 模板数据库标识
     * @param code 通知编码
     * @param contentJson 模板内容 JSON
     * @param enabled 是否允许投递
     * @param version 模板版本
     */
    public InboxTemplateState(
            long templateId,
            @NotNull TreeCode code,
            @NotNull String contentJson,
            boolean enabled,
            @NotNull Version version) {
        this.templateId = templateId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.contentJson = Objects.requireNonNull(contentJson, "站内信模板 JSON 不能为空");
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "站内信模板版本不能为空");
    }

    /** @return 模板数据库标识 */
    public long templateId() {
        return templateId;
    }

    /** @return 站内信模板编码 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** @return 模板内容 JSON */
    public @NotNull String contentJson() {
        return contentJson;
    }

    /** @return 是否允许投递 */
    public boolean enabled() {
        return enabled;
    }

    /** @return 模板版本 */
    public @NotNull Version version() {
        return version;
    }
}
