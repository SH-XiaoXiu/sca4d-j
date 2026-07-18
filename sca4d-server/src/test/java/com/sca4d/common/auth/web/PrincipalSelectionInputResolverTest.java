package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** 验证 Principal 选择输入从 path、query、header 和 body 中解析。 */
class PrincipalSelectionInputResolverTest {

    private final PrincipalSelectionInputResolver resolver = new PrincipalSelectionInputResolver();

    @Test
    void shouldResolvePathQueryAndHeaderSelectionValues() throws Exception {
        Method method = SelectionEndpoint.class.getDeclaredMethod("fromRequestFacts");
        CurrentRequest request = new CurrentRequest();
        request.setPathVariables(Map.of("principalId", "9"));
        request.setQueryParameters(Map.of("tenantId", "10"));
        request.setHeaders(Map.of("X-Scope", "workspace"));

        PrincipalSelectionInput input = resolver.resolve(
                method.getAnnotation(AuthPrincipal.class), method, new Object[0], request);

        assertThat(input.getPrincipalId()).isEqualTo(9L);
        assertThat(input.getTenantId()).isEqualTo(10L);
        assertThat(input.getSelectionKey()).isEqualTo("workspace");
    }

    @Test
    void shouldRejectRepeatedQueryValueForSingleTenantSelection() throws Exception {
        Method method = SelectionEndpoint.class.getDeclaredMethod("fromRequestFacts");
        CurrentRequest request = new CurrentRequest();
        request.setPathVariables(Map.of("principalId", "9"));
        request.setQueryParameters(Map.of("tenantId", List.of("10", "11")));

        assertThatThrownBy(() -> resolver.resolve(
                method.getAnnotation(AuthPrincipal.class), method, new Object[0], request))
                .isInstanceOfSatisfying(ForbiddenException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.principal-selection.scalar-required"));
    }

    @Test
    void shouldResolveMissingOptionalRequestFactAsNull() throws Exception {
        Method method = SelectionEndpoint.class.getDeclaredMethod("fromRequestFacts");
        CurrentRequest request = new CurrentRequest();
        request.setPathVariables(Map.of("principalId", "9"));

        PrincipalSelectionInput input = resolver.resolve(
                method.getAnnotation(AuthPrincipal.class), method, new Object[0], request);

        assertThat(input.getTenantId()).isNull();
        assertThat(input.getSelectionKey()).isNull();
    }

    @Test
    void shouldResolveBoundHttpBodySelectionValue() throws Exception {
        Method method = SelectionEndpoint.class.getDeclaredMethod("fromBody", SelectionBody.class);
        SelectionBody body = new SelectionBody(12L);

        PrincipalSelectionInput input = resolver.resolve(
                method.getAnnotation(AuthPrincipal.class),
                method,
                new Object[] {body},
                new CurrentRequest());

        assertThat(input.getTenantId()).isEqualTo(12L);
    }

    /** 声明 path、query、header 和 body 选择输入的测试端点。 */
    private static final class SelectionEndpoint {

        /** 声明只依赖 path、query 和 header 请求数据的 Principal 选择规则。 */
        @AuthPrincipal(principalId = "#path.principalId", tenantId = "#query.tenantId", selectionKey = "#header['X-Scope']")
        void fromRequestFacts() {
        }

        /**
         * 声明从已绑定请求体读取租户主键的 Principal 选择规则。
         *
         * @param body MVC 已解析的测试请求体
         */
        @AuthPrincipal(tenantId = "#body.tenantId")
        void fromBody(@RequestBody @NotNull SelectionBody body) {
        }
    }

    /** 提供端点 body 中 tenantId 字段的测试请求。 */
    private static final class SelectionBody {

        private final Long tenantId;

        /**
         * 创建包含租户选择数据的测试请求体。
         *
         * @param tenantId 应被 SpEL 读取的租户主键
         */
        private SelectionBody(@NotNull Long tenantId) {
            this.tenantId = tenantId;
        }

        /** @return 供 SpEL 属性访问器读取的租户主键 */
        public @NotNull Long getTenantId() {
            return tenantId;
        }
    }
}
