package com.sca4d.app.server.platform.request;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.validation.Req;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 Platform 角色完整多语言字段替换请求。 */
@Schema(name = "ReplacePlatformRoleI18nRequest")
public final class ReplaceRoleI18nRequest {

    @Req.NotNull
    private @Nullable LocalizedFields i18n;

    /** 创建供 JSON 反序列化填充的空请求。 */
    public ReplaceRoleI18nRequest() {
    }

    /**
     * 返回新的完整多语言字段。
     * @return 完整多语言字段
     */
    public @NotNull LocalizedFields getI18n() {
        return Objects.requireNonNull(i18n, "角色多语言字段不能为空");
    }

    /**
     * 设置新的完整多语言字段。
     * @param i18n 完整多语言字段
     */
    public void setI18n(@Nullable LocalizedFields i18n) {
        this.i18n = i18n;
    }
}
