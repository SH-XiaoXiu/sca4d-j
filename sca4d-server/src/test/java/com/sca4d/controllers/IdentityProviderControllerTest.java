package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.controllers.mg.system.SystemIdentityProviderController;
import com.sca4d.controllers.mg.tenant.TenantIdentityProviderController;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 验证 GLOBAL 和 TENANT IdentityProvider 使用不同 REST 根路径与 Principal 选择。 */
class IdentityProviderControllerTest {

    @Test
    void shouldExposeGlobalProviderResourcesForPlatformPrincipal() throws Exception {
        RequestMapping root = SystemIdentityProviderController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = SystemIdentityProviderController.class.getAnnotation(AuthPrincipal.class);

        assertThat(root.value()).containsExactly("/mg/system/auth/providers");
        assertThat(principal.types()).containsExactly(PrincipalType.PLATFORM);
        assertThat(principal.selection()).isEqualTo(PlatformPrincipalSelection.class);
        assertResourceMappings(SystemIdentityProviderController.class);
    }

    @Test
    void shouldExposeTenantScopedResourcesForTenantPrincipal() throws Exception {
        RequestMapping root = TenantIdentityProviderController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = TenantIdentityProviderController.class.getAnnotation(AuthPrincipal.class);

        assertThat(root.value()).containsExactly("/mg/tenant/tenants/{tenantId}/auth/providers");
        assertThat(principal.types()).containsExactly(PrincipalType.TENANT);
        assertThat(principal.selection()).isEqualTo(TenantPrincipalSelection.class);
        assertThat(principal.tenantId()).isEqualTo("#path.tenantId");
        assertResourceMappings(TenantIdentityProviderController.class);
    }

    /** 验证集合创建与单资源读取、替换、状态修改和删除使用对应 HTTP 方法。 */
    private void assertResourceMappings(Class<?> controller) throws Exception {
        assertThat(controller.getDeclaredMethods())
                .anySatisfy(method -> assertThat(method.getAnnotation(PostMapping.class)).isNotNull())
                .anySatisfy(method -> assertThat(method.getAnnotation(PutMapping.class)).isNotNull())
                .anySatisfy(method -> assertThat(method.getAnnotation(PatchMapping.class)).isNotNull())
                .anySatisfy(method -> assertThat(method.getAnnotation(DeleteMapping.class)).isNotNull())
                .anySatisfy(method -> assertThat(method.getAnnotation(GetMapping.class)).isNotNull());
    }
}
