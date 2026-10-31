package com.sca4d.open.graphql;

import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.open.client.OpenClientOperationChecker;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.util.Objects;

/** 校验开放 GraphQL 根字段并注册 {@code @openOperation} 指令。 */
public class OpenGraphQlRuntimeWiringConfigurer implements RuntimeWiringConfigurer {

    private final @NotNull OpenClientOperationChecker checker;

    private final @NotNull OperationCatalog operations;

    private TypeDefinitionRegistry registry;

    /** 创建开放 GraphQL Schema 接线器。 */
    public OpenGraphQlRuntimeWiringConfigurer(
            @NotNull OperationCatalog operations,
            @NotNull OpenClientOperationChecker checker) {
        this.operations = Objects.requireNonNull(operations, "Operation 目录不能为空");
        this.checker = Objects.requireNonNull(checker, "Open Client Operation 检查器不能为空");
    }

    /** 保存完整 Schema 定义，供根字段校验使用。 */
    @Override
    public void setTypeDefinitionRegistry(@NotNull TypeDefinitionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "GraphQL Schema 目录不能为空");
    }

    /** 校验 Query/Mutation 根字段并注册指令接线器。 */
    @Override
    public void configure(@NotNull RuntimeWiring.Builder wiring) {
        if (registry == null) {
            throw new IllegalStateException("GraphQL Schema 目录尚未提供");
        }
        requireOperations("Query");
        requireOperations("Mutation");
        wiring.directiveWiring(new OpenGraphQlDirectiveWiring(operations, checker));
    }

    private void requireOperations(String typeName) {
        registry.getType(typeName, ObjectTypeDefinition.class)
                .ifPresent(type -> type.getFieldDefinitions().forEach(this::requireOperation));
    }

    private void requireOperation(FieldDefinition field) {
        if (field.getDirectives().stream().noneMatch(item -> "openOperation".equals(item.getName()))) {
            throw new IllegalStateException(
                    "开放 GraphQL 根字段必须声明 @openOperation: " + field.getName());
        }
    }
}
