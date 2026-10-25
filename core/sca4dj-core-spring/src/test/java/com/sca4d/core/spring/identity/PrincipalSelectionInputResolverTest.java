package com.sca4d.core.spring.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** 验证 PrincipalSelectionInputResolver 把路径变量、查询参数、Header、请求体和方法参数绑定到 PrincipalSelection 声明的参数类型。 */
class PrincipalSelectionInputResolverTest {

    @Test
    @DisplayName("Converts Path Query Header Body And Method Arguments To Declared Type")
    void convertsPathQueryHeaderBodyAndMethodArgumentsToDeclaredType() throws Exception {
        Method method = getClass().getDeclaredMethod("endpoint", Long.class, RequestBodyValue.class);
        RequestBodyValue body = new RequestBodyValue("device-name");
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/devices/42");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("deviceId", "42"));
        request.addParameter("scope", "owned", "shared");
        request.addHeader("X-Platform", "admin");
        PrincipalSelectionInput input = new PrincipalSelectionInputResolver()
                .resolve(method, new Object[] {42L, body}, request);
        CapturingSelection selection = new CapturingSelection();

        new PrincipalSelectionInvoker().select(selection, null, null, null, input);

        assertThat(selection.parameters).isNotNull();
        assertThat(selection.parameters.getPath()).containsEntry("deviceId", "42");
        assertThat(selection.parameters.getQuery().get("scope"))
                .isEqualTo(List.of("owned", "shared"));
        assertThat(selection.parameters.getHeader()).containsEntry("x-platform", "admin");
        assertThat(selection.parameters.getBody().getName()).isEqualTo("device-name");
        assertThat(selection.parameters.getDeviceId()).isEqualTo(42L);
    }

    /** 仅提供参数类型给 Method 反射使用，方法体为空，不会被实际调用。 */
    @SuppressWarnings("unused")
    private void endpoint(Long deviceId, @RequestBody RequestBodyValue body) {
    }

    /** 记录解析器传入的 SelectionParameters，供测试断言绑定结果。 */
    private static class CapturingSelection implements PrincipalSelection<SelectionParameters> {

        private SelectionParameters parameters;

        @Override
        public @NotNull Class<SelectionParameters> parameterType() {
            return SelectionParameters.class;
        }

        @Override
        public @Nullable Principal select(
                @Nullable Session session,
                @Nullable Subject subject,
                @Nullable Account account,
                @NotNull SelectionParameters parameters) {
            this.parameters = parameters;
            return null;
        }
    }

    /** 保存 DataBinder 绑定后的类型化选择参数。 */
    @SuppressWarnings("unused")
    public static class SelectionParameters {

        private Map<String, Object> path;

        private Map<String, Object> query;

        private Map<String, Object> header;

        private RequestBodyValue body;

        private Long deviceId;

        public SelectionParameters() {
        }

        public Map<String, Object> getPath() {
            return path;
        }

        public void setPath(Map<String, Object> path) {
            this.path = path;
        }

        public Map<String, Object> getQuery() {
            return query;
        }

        public void setQuery(Map<String, Object> query) {
            this.query = query;
        }

        public Map<String, Object> getHeader() {
            return header;
        }

        public void setHeader(Map<String, Object> header) {
            this.header = header;
        }

        public RequestBodyValue getBody() {
            return body;
        }

        public void setBody(RequestBodyValue body) {
            this.body = body;
        }

        public Long getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(Long deviceId) {
            this.deviceId = deviceId;
        }
    }

    /** 保存 MVC 已经解析的测试请求体。 */
    @SuppressWarnings("unused")
    public static class RequestBodyValue {

        private String name;

        public RequestBodyValue() {
        }

        RequestBodyValue(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
