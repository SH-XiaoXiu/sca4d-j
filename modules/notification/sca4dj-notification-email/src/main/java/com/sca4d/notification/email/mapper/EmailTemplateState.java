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

    /**
     * @param templateId 模板数据库标识
     * @param code 通知编码
     * @param localesJson 各 Locale 的模板 JSON
     * @param enabled 是否允许投递
     * @param version 模板版本
     */
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

    /** @return 模板数据库标识 */
    public long templateId() {
        return templateId;
    }

    /** @return 邮件模板编码 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** @return 各 Locale 的模板 JSON */
    public @NotNull String localesJson() {
        return localesJson;
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
