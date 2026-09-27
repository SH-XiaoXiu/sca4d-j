package com.sca4d.core.spring.web.view;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.sca4d.core.api.i18n.TextLocalizer;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 只包装 BaseView 子类已经创建好的 Jackson 默认 Bean 序列化器。 */
public class BaseViewSerializerModifier extends BeanSerializerModifier {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient TextLocalizer localizer;

    /**
     * 创建只替换 BaseView 子类序列化器的修改器。
     *
     * @param localizer 应用文本本地化入口
     */
    public BaseViewSerializerModifier(@NotNull TextLocalizer localizer) {
        this.localizer = Objects.requireNonNull(localizer, "TextLocalizer 不能为空");
    }

    /**
     * @param config 当前序列化配置
     * @param beanDescription Bean 元数据
     * @param serializer Jackson 默认序列化器
     * @return BaseView 包装序列化器，或原序列化器
     */
    @Override
    public @NotNull JsonSerializer<?> modifySerializer(
            @NotNull SerializationConfig config,
            @NotNull BeanDescription beanDescription,
            @NotNull JsonSerializer<?> serializer) {
        if (!BaseView.class.isAssignableFrom(beanDescription.getBeanClass())) {
            return serializer;
        }
        @SuppressWarnings("unchecked")
        JsonSerializer<Object> delegate = (JsonSerializer<Object>) serializer;
        Map<String, String> propertyNames = new LinkedHashMap<>();
        for (BeanPropertyDefinition property : beanDescription.findProperties()) {
            propertyNames.put(property.getInternalName(), property.getName());
        }
        return new BaseViewSerializer(
                beanDescription.getBeanClass(),
                delegate,
                localizer,
                propertyNames);
    }
}
