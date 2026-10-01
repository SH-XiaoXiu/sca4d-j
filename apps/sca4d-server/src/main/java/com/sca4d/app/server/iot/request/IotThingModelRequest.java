package com.sca4d.app.server.iot.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.AssertTrue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 接收一次 ThingModel 技术定义和初始完整多语言字段发布请求。 */
public class IotThingModelRequest {

    /** `model` 和 `i18n` 紧凑 JSON 允许占用的最大 UTF-8 字节数。 */
    private static final int MAXIMUM_CONTENT_LENGTH = 1_048_576;

    @Req.NotNull
    private @Nullable JsonNode model;

    @Req.NotNull
    private @Nullable LocalizedFields i18n;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotThingModelRequest() {
    }

    /** @return ThingModel 格式 2 技术定义 */
    public @NotNull JsonNode getModel() {
        return Objects.requireNonNull(model, "ThingModel 技术定义不能为空");
    }

    /** @param model ThingModel 格式 2 技术定义 */
    public void setModel(@Nullable JsonNode model) {
        this.model = model;
    }

    /** @return 与技术定义一起首次发布的完整多语言字段 */
    public @NotNull LocalizedFields getI18n() {
        return Objects.requireNonNull(i18n, "ThingModel 多语言字段不能为空");
    }

    /** @param i18n 与技术定义一起首次发布的完整多语言字段 */
    public void setI18n(@Nullable LocalizedFields i18n) {
        this.i18n = i18n;
    }

    /** @return `model` 和 `i18n` 的紧凑 JSON 是否不超过 1 MiB */
    @JsonIgnore
    @AssertTrue(message = "ThingModel 发布请求不能超过 1 MiB")
    public boolean isContentLengthValid() {
        if (model == null || i18n == null) {
            return true;
        }
        ObjectNode i18nNode = JsonNodeFactory.instance.objectNode();
        i18n.toJson().forEach((locale, fields) -> {
            ObjectNode fieldsNode = i18nNode.putObject(locale);
            fields.forEach(fieldsNode::put);
        });
        ObjectNode requestNode = JsonNodeFactory.instance.objectNode();
        requestNode.set("model", model);
        requestNode.set("i18n", i18nNode);
        return requestNode.toString().getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_CONTENT_LENGTH;
    }
}
