package com.sca4d.notification.email.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.notification.NotificationCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Email 模板 Mapper 读取的数据库状态。 */
public final class EmailTemplateState {
    private final long templateId;

    private final @NotNull NotificationCode code;

    private final @NotNull String localesJson;

    private final boolean enabled;

    private final @NotNull Version version;

    /** 创建当前对象。 */
    public EmailTemplateState(
            long templateId,
            @NotNull NotificationCode code,
            @NotNull String localesJson,
            boolean enabled,
            @NotNull Version version) {
        this.templateId = templateId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.localesJson = Objects.requireNonNull(localesJson, "Email 模板 Locale JSON 不能为空");
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "Email 模板版本不能为空");
    }

    /** 执行 templateId 方法表示的业务操作。 */
    public long templateId() {
        return templateId;
    }

    /** 返回 code 方法提供的结果。 */
    public @NotNull NotificationCode code() {
        return code;
    }

    /** 执行 localesJson 方法表示的业务操作。 */
    public @NotNull String localesJson() {
        return localesJson;
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
