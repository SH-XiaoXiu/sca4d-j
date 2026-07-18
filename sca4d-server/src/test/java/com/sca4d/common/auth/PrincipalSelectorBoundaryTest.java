package com.sca4d.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

/** 验证 Principal 选择实现只能读取允许的请求信息。 */
class PrincipalSelectorBoundaryTest {

    @Test
    void defaultPrincipalTypeIsDefault() {
        assertThat(PrincipalType.defaultType()).isEqualTo(PrincipalType.DEFAULT);
    }

    @Test
    void principalShouldNotCarryPermissionCodes() {
        assertThat(Arrays.stream(Principal.class.getDeclaredFields()).map(field -> field.getName()))
                .doesNotContain("roles", "permissions", "roleCodes", "permissionCodes");
    }
}
