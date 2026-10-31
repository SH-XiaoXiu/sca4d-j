package com.sca4d.open.graphql;

import com.sca4d.core.api.error.exception.BusinessException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** 将平台业务异常转换为不暴露内部细节的 GraphQL 错误。 */
public class OpenGraphQlExceptionResolver implements DataFetcherExceptionResolver {

    /** 将认证、权限和业务错误码写入 GraphQL extensions。 */
    @Override
    public @NotNull Mono<List<GraphQLError>> resolveException(
            @NotNull Throwable exception,
            @NotNull DataFetchingEnvironment environment) {
        Throwable cause = exception;
        while (cause.getCause() != null
                && !(cause instanceof BusinessException)) {
            cause = cause.getCause();
        }
        if (!(cause instanceof BusinessException business)) {
            return Mono.empty();
        }
        GraphQLError error = GraphqlErrorBuilder.newError()
                .message("请求无法执行")
                .extensions(
                        Map.of(
                                "code",
                                business.errorCode().code(),
                                "messageKey",
                                business.errorCode().messageKey()))
                .build();
        return Mono.just(List.of(error));
    }
}
