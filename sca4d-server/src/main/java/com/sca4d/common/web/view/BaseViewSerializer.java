package com.sca4d.common.web.view;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * BaseView 序列化器。
 *
 * <p>该序列化器会先委托 Jackson 默认 Bean 序列化逻辑生成对象 JSON，再执行 {@link BaseView}
 * 注册的展示层 JSON 操作，保证普通 View 字段输出行为不被破坏。
 */
public final class BaseViewSerializer extends StdSerializer<BaseView> {

    private final JsonSerializer<Object> delegate;

    /**
     * 创建 BaseView 序列化器。
     *
     * @param delegate 默认 Bean 序列化器
     */
    public BaseViewSerializer(@NotNull JsonSerializer<Object> delegate) {
        super(BaseView.class);
        this.delegate = delegate;
    }

    @Override
    public void serialize(
            @NotNull BaseView value, @NotNull JsonGenerator generator, @NotNull SerializerProvider provider)
            throws IOException {
        ObjectMapper objectMapper = (ObjectMapper) generator.getCodec();
        // 委托默认 Bean 序列化器写入 TokenBuffer，保留 Jackson 原本的字段发现、注解和命名策略。
        TokenBuffer buffer = new TokenBuffer(generator.getCodec(), false);
        delegate.serialize(value, buffer, provider);
        JsonNode node = objectMapper.readTree(buffer.asParser());
        ObjectNode root = node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        // 把普通 Bean JSON 包装为 ViewJson，并应用视图声明的最终 JSON 修改。
        value.apply(new ViewJson(objectMapper, root));
        // 输出修改后的最终 JSON。
        generator.writeTree(root);
    }
}
