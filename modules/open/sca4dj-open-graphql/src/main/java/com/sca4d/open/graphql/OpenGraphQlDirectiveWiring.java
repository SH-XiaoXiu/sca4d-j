package com.sca4d.open.graphql;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.open.client.OpenClientOperationChecker;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 将 Schema 字段上的 {@code @openOperation} 转换为 Client Operation 检查。 */
public class OpenGraphQlDirectiveWiring implements SchemaDirectiveWiring {

    private final @NotNull OpenClientOperationChecker checker;

    private final @NotNull OperationCatalog operations;

    /** 创建 GraphQL 开放 Operation 指令接线器。 */
    public OpenGraphQlDirectiveWiring(
            @NotNull OperationCatalog operations,
            @NotNull OpenClientOperationChecker checker) {
        this.operations = Objects.requireNonNull(operations, "Operation 目录不能为空");
        this.checker = Objects.requireNonNull(checker, "Open Client Operation 检查器不能为空");
    }

    /** 为字段包装开放 Client Operation 检查，并在启动时验证 Operation 编码。 */
    @Override
    public @NotNull GraphQLFieldDefinition onField(
            @NotNull SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        String operation = operation(environment);
        TreeCode code = operations.actionByCode(operation).operation().code();
        DataFetcher<?> delegate = environment.getFieldDataFetcher();
        environment.setFieldDataFetcher(data -> fetch(delegate, data, code));
        return environment.getElement();
    }

    private Object fetch(
            DataFetcher<?> delegate,
            DataFetchingEnvironment environment,
            TreeCode operation) throws Exception {
        Call call = CallHolder.currentOrNull();
        if (call == null) {
            throw new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED);
        }
        checker.verifyAllowed(call, operation);
        return CallHolder.run(call, () -> delegate.get(environment));
    }

    private static String operation(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        Object value = environment.getDirective().getArgument("operation").getArgumentValue().getValue();
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("@openOperation 的 operation 必须是非空字符串");
        }
        return text;
    }
}
