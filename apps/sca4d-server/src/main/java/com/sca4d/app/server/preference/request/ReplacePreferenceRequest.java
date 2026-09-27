package com.sca4d.app.server.preference.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.spring.validation.Req;
import org.jetbrains.annotations.NotNull;

/** 接收前端提交的完整个人偏好 JSON 值。 */
public class ReplacePreferenceRequest {

    @Req.NotNull
    private JsonNode value;

    /** 创建等待 Jackson 写入偏好值的请求。 */
    public ReplacePreferenceRequest() {
    }

    /** @return 偏好 Codec 可以解析的完整 JSON 值 */
    public @NotNull JsonNode getValue() {
        return value;
    }

    /** @param value 偏好 Codec 可以解析的完整 JSON 值 */
    public void setValue(@NotNull JsonNode value) {
        this.value = value;
    }
}
