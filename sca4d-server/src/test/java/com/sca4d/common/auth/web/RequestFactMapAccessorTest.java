package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.expression.AccessException;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/** 验证请求数据映射只暴露允许的 path、query、header 和 body 值。 */
class RequestFactMapAccessorTest {

    @Test
    void shouldReadOptionalRequestFact() throws AccessException {
        RequestFactMapAccessor accessor = new RequestFactMapAccessor();
        StandardEvaluationContext context = new StandardEvaluationContext();

        assertThat(accessor.read(context, Map.of("tenantId", 10L), "tenantId").getValue())
                .isEqualTo(10L);
        assertThat(accessor.read(context, Map.of(), "tenantId").getValue()).isNull();
    }

    @Test
    void shouldRejectTargetOutsideAccessorContract() {
        RequestFactMapAccessor accessor = new RequestFactMapAccessor();
        StandardEvaluationContext context = new StandardEvaluationContext();

        assertThatThrownBy(() -> accessor.read(context, null, "tenantId"))
                .isInstanceOf(AccessException.class)
                .hasMessage("请求事实目标必须是 Map");
    }
}
