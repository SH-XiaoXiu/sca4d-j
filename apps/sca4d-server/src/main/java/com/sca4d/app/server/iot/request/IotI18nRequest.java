package com.sca4d.app.server.iot.request;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.validation.Req;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 Product 或 ThingModel 的完整多语言字段替换请求。 */
public class IotI18nRequest {

    @Req.NotNull
    private @Nullable LocalizedFields i18n;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotI18nRequest() {
    }

    /** @return 新的完整多语言字段 */
    public @NotNull LocalizedFields getI18n() {
        return Objects.requireNonNull(i18n, "IoT 多语言字段不能为空");
    }

    /** @param i18n 新的完整多语言字段 */
    public void setI18n(@Nullable LocalizedFields i18n) {
        this.i18n = i18n;
    }
}
