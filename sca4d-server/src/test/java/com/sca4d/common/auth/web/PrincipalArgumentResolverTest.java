package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

/** 验证 Controller 的 Principal 参数从 CurrentRequest 解析。 */
class PrincipalArgumentResolverTest {

    private final PrincipalArgumentResolver resolver = new PrincipalArgumentResolver();

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldResolvePrincipalContext() throws Exception {
        Principal principal = Principal.of(10L, PrincipalType.DEFAULT, 1L, 100L);
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(principal);
        CurrentRequestHolder.set(request);

        Object value = resolver.resolveArgument(parameter("principal"), null, null, null);

        assertThat(value).isSameAs(principal);
    }

    @Test
    void shouldResolvePrincipalId() throws Exception {
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(Principal.of(11L, PrincipalType.DEFAULT, 1L, 100L));
        CurrentRequestHolder.set(request);

        Object value = resolver.resolveArgument(parameter("principalId"), null, null, null);

        assertThat(value).isEqualTo(11L);
    }

    @Test
    void shouldResolvePrincipalType() throws Exception {
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(Principal.of(12L, PrincipalType.DEFAULT, 1L, 100L));
        CurrentRequestHolder.set(request);

        Object value = resolver.resolveArgument(parameter("principalType"), null, null, null);

        assertThat(value).isEqualTo(PrincipalType.DEFAULT);
    }

    @Test
    void shouldDeferPrincipalInjectionUntilAccessAspectCompletesSelection() throws Exception {
        CurrentRequestHolder.set(new CurrentRequest());

        Object value = resolver.resolveArgument(parameter("principal"), null, null, null);

        assertThat(value).isNull();
        assertThat(CurrentRequestHolder.current().getPrincipal()).isNull();
    }

    @Test
    void shouldReturnNullWhenNullable() throws Exception {
        CurrentRequestHolder.set(new CurrentRequest());
        Object value = resolver.resolveArgument(parameter("nullablePrincipal"), null, null, null);

        assertThat(value).isNull();
    }

    @SuppressWarnings("unused")
    void endpoint(
            @AuthPrincipal Principal principal,
            @AuthPrincipal Long principalId,
            @AuthPrincipal PrincipalType principalType,
            @AuthPrincipal(nullable = true) Principal nullablePrincipal) {
    }

    /** @return endpoint 方法中指定名称对应的 Principal 参数 */
    private MethodParameter parameter(String name) throws Exception {
        var method = getClass()
                .getDeclaredMethod(
                        "endpoint", Principal.class, Long.class, PrincipalType.class, Principal.class);
        String[] names = {"principal", "principalId", "principalType", "nullablePrincipal"};
        for (int index = 0; index < names.length; index++) {
            if (names[index].equals(name)) {
                return new MethodParameter(method, index);
            }
        }
        throw new IllegalArgumentException(name);
    }
}
