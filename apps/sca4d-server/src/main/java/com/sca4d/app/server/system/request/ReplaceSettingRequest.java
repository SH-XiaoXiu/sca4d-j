package com.sca4d.app.server.system.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.spring.validation.Req;
import org.jetbrains.annotations.NotNull;

/** 接收管理端提交的一个完整 JSON 设置值。 */
public final class ReplaceSettingRequest {

    @Req.NotNull
    private JsonNode value;

    /** 创建等待 Jackson 写入设置值的请求。 */
    public ReplaceSettingRequest() {
    }

    /** @return 设置 Codec 可以解析的完整 JSON 值 */
    public @NotNull JsonNode getValue() {
        return value;
    }

    /** @param value 设置 Codec 可以解析的完整 JSON 值 */
    public void setValue(@NotNull JsonNode value) {
        this.value = value;
    }
}
