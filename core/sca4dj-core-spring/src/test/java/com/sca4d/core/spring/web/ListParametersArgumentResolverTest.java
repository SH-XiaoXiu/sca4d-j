package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.api.query.SortOrder;
import com.sca4d.core.spring.config.CoreSpringProperties;
import com.sca4d.core.spring.web.annotation.PageOptions;
import com.sca4d.core.spring.web.list.ListDefinition;
import com.sca4d.core.spring.web.list.ListParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class ListParametersArgumentResolverTest {

    @Test
    @DisplayName("Resolves Repeated Sort Parameters")
    void resolvesRepeatedSortParameters() throws Exception {
        CoreSpringProperties properties = new CoreSpringProperties();
        ListParametersArgumentResolver resolver = new ListParametersArgumentResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "2");
        request.addParameter("size", "10");
        request.addParameter("sort", "STATUS,ASC", "UPDATED_AT,DESC");

        ListParameters parameters = (ListParameters) resolver.resolveArgument(
                methodParameter("list"),
                null,
                new ServletWebRequest(request),
                null);
        Sort status = Sort.of("STATUS");
        ListRequest resolved = parameters.resolve(
                ListDefinition.builder()
                        .fields(Sort.UPDATED_AT, status)
                        .defaultSort(Sort.UPDATED_AT.desc())
                        .build());

        assertThat(resolved.getPage().page()).isEqualTo(2);
        assertThat(resolved.getPage().size()).isEqualTo(10);
        assertThat(resolved.getSort()).extracting(SortOrder::getField)
                .containsExactly(status, Sort.UPDATED_AT);
    }

    @Test
    @DisplayName("Rejects Legacy Sort Parameters")
    void rejectsLegacySortParameters() {
        ListParametersArgumentResolver resolver = new ListParametersArgumentResolver(new CoreSpringProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("sortBy", "STATUS");

        assertThatThrownBy(
                () -> resolver.resolveArgument(
                        methodParameter("list"),
                        null,
                        new ServletWebRequest(request),
                        null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Applies Parameter Page Size Limit")
    void appliesParameterPageSizeLimit() {
        PageRequestArgumentResolver resolver = new PageRequestArgumentResolver(new CoreSpringProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("size", "11");

        assertThatThrownBy(
                () -> resolver.resolveArgument(
                        methodParameter("page"),
                        null,
                        new ServletWebRequest(request),
                        null))
                .isInstanceOf(BusinessException.class);
    }

    /** @return ControllerFixtures 中指定方法的第一个参数 */
    private static MethodParameter methodParameter(String method) throws Exception {
        return new MethodParameter(
                ControllerFixtures.class.getDeclaredMethod(
                        method,
                        method.equals("list") ? ListParameters.class : PageRequest.class),
                0);
    }

    /** 保存参数解析测试使用的 Controller 方法声明。 */
    private static final class ControllerFixtures {

        /** 声明类型安全列表参数。 */
        @SuppressWarnings("unused")
        private void list(ListParameters parameters) {
        }

        /** 声明更小的分页上限。 */
        @SuppressWarnings("unused")
        private void page(@PageOptions(maxSize = 10) PageRequest page) {
        }
    }

}
