package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.web.AuthInterceptor;
import com.sca4d.common.auth.web.PrincipalArgumentResolver;
import com.sca4d.common.auth.web.PrincipalInterceptor;
import com.sca4d.common.auth.web.PrincipalSelectionInputResolver;
import com.sca4d.common.auth.web.PrincipalSelectionInvoker;
import com.sca4d.common.auth.web.SubjectArgumentResolver;
import com.sca4d.common.permission.AccessAspect;
import com.sca4d.common.permission.PermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 验证认证、Principal 选择和 HTTP 拦截器的 Spring 装配。 */
class AuthConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(AuthConfig.class);

    @Test
    void shouldRegisterUnifiedSecurityRuntime() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Authentication.class);
            assertThat(context).hasSingleBean(PrincipalSelection.class);
            assertThat(context).hasSingleBean(PermissionChecker.class);
            assertThat(context).hasSingleBean(ResourceAccess.class);
            assertThat(context).hasSingleBean(SubjectArgumentResolver.class);
            assertThat(context).hasSingleBean(PrincipalArgumentResolver.class);
            assertThat(context).hasSingleBean(PrincipalSelectionInputResolver.class);
            assertThat(context).hasSingleBean(PrincipalSelectionInvoker.class);
            assertThat(context).hasSingleBean(AuthInterceptor.class);
            assertThat(context).hasSingleBean(PrincipalInterceptor.class);
            assertThat(context).hasSingleBean(AccessAspect.class);
            assertThat(context).hasSingleBean(WebMvcConfigurer.class);
        });
    }

    @Test
    void shouldKeepBusinessSecurityImplementations() {
        Authentication authentication = mock(Authentication.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        PrincipalSelection<?> principalSelection = mock(PrincipalSelection.class);

        contextRunner
                .withBean(Authentication.class, () -> authentication)
                .withBean(PermissionChecker.class, () -> permissionChecker)
                .withBean("defaultPrincipalSelection", PrincipalSelection.class, () -> principalSelection)
                .run(context -> {
                    assertThat(context.getBean(Authentication.class)).isSameAs(authentication);
                    assertThat(context.getBean(PermissionChecker.class)).isSameAs(permissionChecker);
                    assertThat(context.getBean("defaultPrincipalSelection")).isSameAs(principalSelection);
                });
    }

    @Test
    void shouldDeclareOpenApiRootDocumentsAsPublicMvcResources() {
        assertThat(AuthConfig.OPEN_API_PUBLIC_PATHS)
                .contains("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**")
                .contains("/swagger-ui/**", "/swagger-ui.html", "/webjars/**");
    }

    @Test
    void shouldRejectEnabledEmailCodeWithoutSender() {
        contextRunner
                .withPropertyValues("sca4dj.authentication.email-code.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldAcceptCompleteEmailCodeConfiguration() {
        contextRunner
                .withPropertyValues(
                        "sca4dj.authentication.email-code.enabled=true",
                        "sca4dj.authentication.email-code.sender-id=7")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
