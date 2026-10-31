package com.sca4d.open.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 GraphQL 错误转换不会暴露异常内部信息。 */
class OpenGraphQlExceptionResolverTest {

    private final OpenGraphQlExceptionResolver resolver = new OpenGraphQlExceptionResolver();

    @Test
    @DisplayName("业务异常转换为错误码和消息键")
    void resolvesBusinessException() {
        var errors = resolver.resolveException(
                new BusinessException(CoreErrorCode.PERMISSION_DENIED),
                mock(DataFetchingEnvironment.class)).block();

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getMessage()).isEqualTo("请求无法执行");
        assertThat(errors.getFirst().getExtensions())
                .containsEntry("code", CoreErrorCode.PERMISSION_DENIED.code())
                .containsEntry("messageKey", CoreErrorCode.PERMISSION_DENIED.messageKey());
    }

    @Test
    @DisplayName("未知异常交给其他 GraphQL 处理器")
    void ignoresUnknownException() {
        assertThat(
                resolver.resolveException(
                        new IllegalStateException("internal"),
                        mock(DataFetchingEnvironment.class)).block())
                .isNull();
    }
}
