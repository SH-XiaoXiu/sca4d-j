package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.authentication.me.MePrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.web.PrincipalSelectionInput;
import com.sca4d.common.auth.web.PrincipalSelectionInputResolver;
import com.sca4d.common.permission.AccessPolicy;
import com.sca4d.common.request.CurrentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.Map;

/** 验证当前用户接口的路由、权限声明和请求类型。 */
class MeControllerContractTest {

    @Test
    void currentUserEndpointShouldSelectPrincipalFromOptionalQueryParameters() throws Exception {
        RequestMapping mapping = MeController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = MeController.class.getAnnotation(AuthPrincipal.class);
        Method current = MeController.class.getDeclaredMethod("current", PrincipalType.class, Long.class);

        assertThat(mapping.value()).containsExactly("/me");
        assertThat(principal.selection()).isEqualTo(MePrincipalSelection.class);
        assertThat(principal.selectionKey()).isEqualTo("#query.principalType");
        assertThat(principal.tenantId()).isEqualTo("#query.tenantId");
        assertThat(current.getParameterAnnotations()[0])
                .filteredOn(annotation -> annotation instanceof RequestParam)
                .extracting(annotation -> ((RequestParam) annotation).required())
                .containsExactly(false);
        assertThat(current.getParameterAnnotations()[1])
                .filteredOn(annotation -> annotation instanceof RequestParam)
                .extracting(annotation -> ((RequestParam) annotation).required())
                .containsExactly(false);
    }

    @Test
    void currentUserSelectionExpressionsShouldResolveFromRuntimeQueryFacts() throws Exception {
        Method current = MeController.class.getDeclaredMethod("current", PrincipalType.class, Long.class);
        AuthPrincipal principal = AccessPolicy.resolve(current, MeController.class).getPrincipal();
        CurrentRequest request = new CurrentRequest();
        request.setQueryParameters(Map.of("principalType", "PLATFORM"));

        PrincipalSelectionInput input = new PrincipalSelectionInputResolver()
                .resolve(principal, current, new Object[] {null, null}, request);

        assertThat(input.getSelectionKey()).isEqualTo("PLATFORM");
        assertThat(input.getTenantId()).isNull();
    }
}
