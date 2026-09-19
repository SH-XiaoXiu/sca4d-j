package com.sca4d.notification.template.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.notification.NotificationCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Notification Template Mapper 从数据库读取的 JSON 文本状态。 */
public final class NotificationTemplateState {

    private final long templateId;

    private final @NotNull NotificationCode code;

    private final @NotNull String contentJson;

    private final boolean enabled;

    private final @NotNull Version version;

    /** 创建模板数据库状态。 */
    public NotificationTemplateState(
            long templateId,
            @NotNull NotificationCode code,
            @NotNull String contentJson,
            boolean enabled,
            @NotNull Version version) {
        this.templateId = templateId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.contentJson = Objects.requireNonNull(contentJson, "通知模板 JSON 不能为空");
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "通知模板版本不能为空");
    }

    public long templateId() {
        return templateId;
    }

    public @NotNull NotificationCode code() {
        return code;
    }

    public @NotNull String contentJson() {
        return contentJson;
    }

    public boolean enabled() {
        return enabled;
    }

    public @NotNull Version version() {
        return version;
    }
}
