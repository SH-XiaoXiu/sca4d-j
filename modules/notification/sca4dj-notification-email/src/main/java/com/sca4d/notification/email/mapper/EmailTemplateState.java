package com.sca4d.notification.email.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Email 模板 Mapper 读取的数据库状态。
 */
public final class EmailTemplateState {
    private final long templateId;

    private final @NotNull TreeCode code;

    private final @NotNull String localesJson;

    private final boolean enabled;

    private final @NotNull Version version;

    public EmailTemplateState(
            long templateId,
            @NotNull TreeCode code,
            @NotNull String localesJson,
            boolean enabled,
            @NotNull Version version) {
        this.templateId = templateId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.localesJson = Objects.requireNonNull(localesJson, "Email 模板 Locale JSON 不能为空");
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "Email 模板版本不能为空");
    }

    public long templateId() {
        return templateId;
    }

    /**
     * 返回 code 方法提供的结果。
     */
    public @NotNull TreeCode code() {
        return code;
    }

    public @NotNull String localesJson() {
        return localesJson;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * 返回 version 方法提供的结果。
     */
    public @NotNull Version version() {
        return version;
    }
}
