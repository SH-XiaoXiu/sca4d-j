package com.sca4d.common.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.access.AccessCriteria;
import com.sca4d.common.access.ResourceAccessDeniedException;
import org.junit.jupiter.api.Test;

/** 验证请求信息、认证对象、Principal 和访问条件的读写。 */
class CurrentRequestTest {

    @Test
    void shouldReadResolvedAccessCriteria() {
        CurrentRequest request = new CurrentRequest();
        TestAccessCriteria criteria = new TestAccessCriteria();

        request.putAccess(TestAccessCriteria.class, criteria);

        assertThat(request.requireAccess(TestAccessCriteria.class)).isSameAs(criteria);
    }

    @Test
    void shouldRejectMissingAccessCriteria() {
        CurrentRequest request = new CurrentRequest();

        assertThatThrownBy(() -> request.requireAccess(TestAccessCriteria.class))
                .isInstanceOfSatisfying(ResourceAccessDeniedException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.common.forbidden"));
    }

    @Test
    void shouldRejectDuplicateAccessCriteria() {
        CurrentRequest request = new CurrentRequest();
        request.putAccess(TestAccessCriteria.class, new TestAccessCriteria());

        assertThatThrownBy(
                () -> request.putAccess(TestAccessCriteria.class, new TestAccessCriteria()))
                .isInstanceOfSatisfying(ResourceAccessDeniedException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.common.forbidden"));
    }

    /** 保存测试资源主键和访问等级的请求访问条件。 */
    private static final class TestAccessCriteria implements AccessCriteria {
    }
}
