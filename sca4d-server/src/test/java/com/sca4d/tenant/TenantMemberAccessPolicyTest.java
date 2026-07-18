package com.sca4d.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.tenant.policy.TenantAccessCriteria;
import com.sca4d.tenant.policy.TenantMemberAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证成员工作流对 Controller 写入的租户访问范围进行二次约束。 */
class TenantMemberAccessPolicyTest {

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldAllowTenantWithinGrantedScope() {
        TenantMemberAccessPolicy policy = policy(10L, AccessAction.MANAGE, 10L);

        policy.require(10L, AccessAction.WRITE);
    }

    @Test
    void shouldRejectTenantOutsideTargetScope() {
        TenantMemberAccessPolicy policy = policy(10L, AccessAction.WRITE, 10L);

        assertThatThrownBy(() -> policy.require(20L, AccessAction.READ))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void shouldRejectAccessLevelBelowRequiredAction() {
        TenantMemberAccessPolicy policy = policy(10L, AccessAction.READ, null);

        assertThatThrownBy(() -> policy.require(10L, AccessAction.WRITE))
                .isInstanceOfSatisfying(ResourceAccessDeniedException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.common.forbidden"));
    }

    private TenantMemberAccessPolicy policy(Long tenantId, AccessAction action, Long targetTenantId) {
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(Principal.of(1L, PrincipalType.TENANT, 1L, tenantId));
        request.putAccess(
                TenantAccessCriteria.class,
                new TenantAccessCriteria(false, action, Set.of(tenantId), Set.of(), targetTenantId));
        CurrentRequestHolder.set(request);
        return new TenantMemberAccessPolicy(new ResourceAccess());
    }
}
