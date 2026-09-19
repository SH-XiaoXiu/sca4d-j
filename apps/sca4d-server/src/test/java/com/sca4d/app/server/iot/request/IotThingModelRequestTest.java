package com.sca4d.app.server.iot.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.i18n.LocalizedFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/** 检查 ThingModel 结构化发布请求保留 1 MiB 内容限制。 */
class IotThingModelRequestTest {

    @Test
    @DisplayName("接受小于 1 MiB 的结构化发布请求")
    void acceptsRequestWithinContentLimit() {
        IotThingModelRequest request = request("climate");

        assertThat(request.isContentLengthValid()).isTrue();
    }

    @Test
    @DisplayName("拒绝超过 1 MiB 的结构化发布请求")
    void rejectsRequestOverContentLimit() {
        IotThingModelRequest request = request("x".repeat(1_048_576));

        assertThat(request.isContentLengthValid()).isFalse();
    }

    @Test
    @DisplayName("按 UTF-8 字节拒绝超过 1 MiB 的多字节请求")
    void rejectsMultibyteRequestOverUtf8ContentLimit() {
        IotThingModelRequest request = request("温".repeat(350_000));

        assertThat(request.isContentLengthValid()).isFalse();
    }

    /** 创建包含固定默认翻译和指定 Product code 的发布请求。 */
    private static IotThingModelRequest request(String productCode) {
        ObjectNode model = JsonNodeFactory.instance.objectNode();
        model.put("formatVersion", 2);
        model.put("productCode", productCode);
        model.put("version", 1);
        model.putArray("properties");
        model.putArray("events");
        model.putArray("commands");
        IotThingModelRequest request = new IotThingModelRequest();
        request.setModel(model);
        request.setI18n(
                LocalizedFields.fromJson(
                        Map.of(
                                "zh-CN",
                                Map.of("/displayName", "温控器"))));
        return request;
    }
}
