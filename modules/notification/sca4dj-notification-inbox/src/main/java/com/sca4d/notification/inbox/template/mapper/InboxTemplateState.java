package com.sca4d.notification.inbox.template.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.notification.NotificationCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Inbox Template Mapper 从数据库读取的 JSON 文本状态。 */
public final class InboxTemplateState {

    private final long templateId;

    private final @NotNull NotificationCode code;

    private final @NotNull String contentJson;

    private final boolean enabled;

    private final @NotNull Version version;

    /** 创建模板数据库状态。 */
    public InboxTemplateState(
            long templateId,
            @NotNull NotificationCode code,
            @NotNull String contentJson,
            boolean enabled,
            @NotNull Version version) {
        this.templateId = templateId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.contentJson = Objects.requireNonNull(contentJson, "站内信模板 JSON 不能为空");
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "站内信模板版本不能为空");
    }

    /** 执行 templateId 方法表示的业务操作。 */
    public long templateId() {
        return templateId;
    }

    /** 返回 code 方法提供的结果。 */
    public @NotNull NotificationCode code() {
        return code;
    }

    /** 执行 contentJson 方法表示的业务操作。 */
    public @NotNull String contentJson() {
        return contentJson;
    }

    /** 执行 enabled 方法表示的业务操作。 */
    public boolean enabled() {
        return enabled;
    }

    /** 返回 version 方法提供的结果。 */
    public @NotNull Version version() {
        return version;
    }
}
