package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.common.auth.Authentication;
import com.sca4d.common.request.web.CurrentRequestFilter;
import com.sca4d.common.web.ClientStateFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/** 验证 MVC 参数解析器、拦截器、过滤器和消息转换配置。 */
class WebMvcConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(Authentication.class, () -> mock(Authentication.class))
            .withUserConfiguration(WebMvcConfig.class);

    @Test
    void shouldRegisterCurrentRequestFilterAsSpringBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CurrentRequestFilter.class);
            assertThat(context).hasBean("sca4dCurrentRequestFilter");
            assertThat(context).hasSingleBean(ClientStateFilter.class);
            assertThat(context).hasBean("sca4dCurrentRequestFilterRegistration");
            assertThat(context).hasBean("clientStateFilterRegistration");
        });
    }

    @Test
    void shouldUseCurrentRequestFilterBeanInRegistration() {
        contextRunner.run(context -> {
            CurrentRequestFilter requestContextFilter = context.getBean(CurrentRequestFilter.class);
            var registration = context.getBean(
                    "sca4dCurrentRequestFilterRegistration",
                    FilterRegistrationBean.class);

            assertThat(registration.getFilter()).isSameAs(requestContextFilter);

            ClientStateFilter clientStateFilter = context.getBean(ClientStateFilter.class);
            var clientStateRegistration = context.getBean(
                    "clientStateFilterRegistration",
                    FilterRegistrationBean.class);

            assertThat(clientStateRegistration.getFilter()).isSameAs(clientStateFilter);
        });
    }
}
