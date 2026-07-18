package com.sca4d.common.web.view;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.jetbrains.annotations.NotNull;

/**
 * BaseView 序列化扩展点。
 *
 * <p>该扩展点只拦截 {@link BaseView} 子类，并用 {@link BaseViewSerializer}
 * 包装 Jackson 已经创建好的默认 Bean 序列化器。
 */
public final class BaseViewSerializerModifier extends BeanSerializerModifier {

    @Override
    public JsonSerializer<?> modifySerializer(
            @NotNull SerializationConfig config,
            @NotNull BeanDescription beanDescription,
            @NotNull JsonSerializer<?> serializer) {
        Class<?> beanClass = beanDescription.getBeanClass();
        if (!BaseView.class.isAssignableFrom(beanClass)) {
            return serializer;
        }
        // 只包装 BaseView 子类，其他响应对象继续使用 Jackson 默认行为。
        @SuppressWarnings("unchecked")
        JsonSerializer<Object> delegate = (JsonSerializer<Object>) serializer;
        return new BaseViewSerializer(delegate);
    }
}
