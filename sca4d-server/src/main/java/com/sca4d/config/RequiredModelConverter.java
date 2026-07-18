package com.sca4d.config;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.sca4d.common.validation.Required;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Iterator;

/** 将运行时 {@link Required} 约束同步为 OpenAPI 必填字段。 */
public class RequiredModelConverter implements ModelConverter {

    @Override
    public @Nullable Schema<?> resolve(
            @NotNull AnnotatedType type,
            @NotNull ModelConverterContext context,
            @NotNull Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> schema = chain.next().resolve(type, context, chain);
        if (schema == null) {
            return schema;
        }
        Schema<?> targetSchema = schema;
        String reference = schema.get$ref();
        // springdoc 可能返回组件引用；必填字段必须写入实际组件，不能只修改当前引用对象。
        if (targetSchema.getProperties() == null
                && reference != null
                && reference.startsWith("#/components/schemas/")) {
            targetSchema = context.getDefinedModels().get(reference.substring("#/components/schemas/".length()));
        }
        if (targetSchema == null || targetSchema.getProperties() == null) {
            return schema;
        }
        Class<?> modelType = TypeFactory.defaultInstance()
                .constructType(type.getType())
                .getRawClass();
        // Request 可以继承公共字段，逐级读取声明字段才能与运行时 Bean Validation 保持一致。
        for (Class<?> current = modelType; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Required.class)
                        && targetSchema.getProperties().containsKey(field.getName())
                        && (targetSchema.getRequired() == null
                                || !targetSchema.getRequired().contains(field.getName()))) {
                    targetSchema.addRequiredItem(field.getName());
                }
            }
        }
        return schema;
    }
}
