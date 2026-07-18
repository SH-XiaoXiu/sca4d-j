package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.controllers.mg.system.SystemLdapProviderController;
import com.sca4d.controllers.mg.system.SystemOAuth2ProviderController;
import com.sca4d.controllers.mg.system.SystemOidcProviderController;
import com.sca4d.controllers.mg.tenant.TenantLdapProviderController;
import com.sca4d.controllers.mg.tenant.TenantOAuth2ProviderController;
import com.sca4d.controllers.mg.tenant.TenantOidcProviderController;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 验证 GLOBAL 和 TENANT OIDC 配置使用独立 REST 子资源和正确 Principal。 */
class OidcProviderControllerTest {

    @Test
    void shouldExposeGlobalOidcConfigurationForPlatformPrincipal() throws Exception {
        assertController(
                SystemOidcProviderController.class,
                "/mg/system/auth/providers/{providerId}/oidc",
                "",
                PrincipalType.PLATFORM,
                PlatformPrincipalSelection.class,
                "");
        assertThat(SystemOidcProviderController.class
                .getDeclaredMethod("discover", Long.class)
                .getAnnotation(PostMapping.class)
                .value())
                .containsExactly("/discoveries");
    }

    @Test
    void shouldExposeTenantOidcConfigurationForTenantPrincipal() throws Exception {
        assertController(
                TenantOidcProviderController.class,
                "/mg/tenant/tenants/{tenantId}/auth/providers/{providerId}/oidc",
                "",
                PrincipalType.TENANT,
                TenantPrincipalSelection.class,
                "#path.tenantId");
        assertThat(TenantOidcProviderController.class
                .getDeclaredMethod("discover", Long.class, Long.class)
                .getAnnotation(PostMapping.class)
                .value())
                .containsExactly("/discoveries");
    }

    @Test
    void shouldExposeGlobalOAuth2AndLdapConfigurationsForPlatformPrincipal() throws Exception {
        assertController(
                SystemOAuth2ProviderController.class,
                "/mg/system/auth/providers/{providerId}/oauth2",
                "",
                PrincipalType.PLATFORM,
                PlatformPrincipalSelection.class,
                "");
        assertController(
                SystemLdapProviderController.class,
                "/mg/system/auth/providers/{providerId}/ldap",
                "",
                PrincipalType.PLATFORM,
                PlatformPrincipalSelection.class,
                "");
    }

    @Test
    void shouldExposeTenantOAuth2AndLdapConfigurationsForTenantPrincipal() throws Exception {
        assertController(
                TenantOAuth2ProviderController.class,
                "/mg/tenant/tenants/{tenantId}/auth/providers/{providerId}/oauth2",
                "",
                PrincipalType.TENANT,
                TenantPrincipalSelection.class,
                "#path.tenantId");
        assertController(
                TenantLdapProviderController.class,
                "/mg/tenant/tenants/{tenantId}/auth/providers/{providerId}/ldap",
                "",
                PrincipalType.TENANT,
                TenantPrincipalSelection.class,
                "#path.tenantId");
    }

    /** 验证协议配置暴露 GET 和幂等 PUT，并使用预期 Principal 选择器。 */
    private void assertController(
            Class<?> controller,
            String path,
            String configurationPath,
            PrincipalType principalType,
            Class<?> selection,
            String tenantId)
            throws Exception {
        RequestMapping root = controller.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = controller.getAnnotation(AuthPrincipal.class);
        assertThat(root.value()).containsExactly(path);
        assertThat(principal.types()).containsExactly(principalType);
        assertThat(principal.selection()).isEqualTo(selection);
        assertThat(principal.tenantId()).isEqualTo(tenantId);
        String[] configurationMapping = controller
                .getDeclaredMethod("configuration", parameterTypes(controller))
                .getAnnotation(GetMapping.class)
                .value();
        if (configurationPath.isEmpty()) {
            assertThat(configurationMapping).isEmpty();
        } else {
            assertThat(configurationMapping).containsExactly(configurationPath);
        }
        assertThat(controller.getDeclaredMethods())
                .anySatisfy(method -> assertThat(method.getAnnotation(PutMapping.class)).isNotNull());
    }

    /** @return GLOBAL 或 TENANT GET 方法的参数类型。 */
    private Class<?>[] parameterTypes(Class<?> controller) {
        return controller.getSimpleName().startsWith("System")
                ? new Class<?>[] {Long.class}
                : new Class<?>[] {Long.class, Long.class};
    }
}
