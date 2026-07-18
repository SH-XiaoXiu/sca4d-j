package com.sca4d.common.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 验证资源访问等级的包含关系和资源主键读取规则。 */
class ResourceAccessTest {

    private final ResourceAccess resourceAccess = new ResourceAccess();

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldReturnCriteriaResolvedByAccessAspect() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));
        request.setPrincipal(Principal.of(10L, PrincipalType.DEFAULT, 1L, null));
        DemoCriteria criteria = new DemoCriteria();
        request.putAccess(DemoCriteria.class, criteria);
        CurrentRequestHolder.set(request);

        assertThat(resourceAccess.require(DemoCriteria.class)).isSameAs(criteria);
    }

    @Test
    void shouldFailClosedWithoutPrincipalOrResolvedCriteria() {
        CurrentRequestHolder.set(CurrentRequest.authenticated(TestSession.of(1L)));
        assertThatThrownBy(() -> resourceAccess.require(DemoCriteria.class))
                .isInstanceOfSatisfying(ResourceAccessDeniedException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.common.forbidden"));

        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));
        request.setPrincipal(Principal.of(10L, PrincipalType.DEFAULT, 1L, null));
        CurrentRequestHolder.set(request);
        assertThatThrownBy(() -> resourceAccess.require(DemoCriteria.class))
                .isInstanceOfSatisfying(ResourceAccessDeniedException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.common.forbidden"));
    }

    @Test
    void shouldRejectDuplicateCriteriaForSameDomain() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));
        request.setPrincipal(Principal.of(10L, PrincipalType.DEFAULT, 1L, null));
        request.putAccess(DemoCriteria.class, new DemoCriteria());

        assertThatThrownBy(() -> request.putAccess(DemoCriteria.class, new DemoCriteria()))
                .isInstanceOfSatisfying(ResourceAccessDeniedException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.common.forbidden"));
    }

    /** 固定返回测试资源主键和访问等级的最小 AccessCriteria。 */
    private static final class DemoCriteria implements AccessCriteria {
    }
}
