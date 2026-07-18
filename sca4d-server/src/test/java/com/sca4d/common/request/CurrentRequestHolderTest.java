package com.sca4d.common.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.test.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 验证 CurrentRequest 的线程绑定、嵌套恢复和清理。 */
class CurrentRequestHolderTest {

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldKeepCurrentRequestInThreadLocal() {
        CurrentRequest request = CurrentRequest.authenticated(TestSession.of(1L));

        CurrentRequestHolder.set(request);

        assertThat(CurrentRequestHolder.current()).isSameAs(request);
        assertThat(CurrentRequestHolder.current().authenticated()).isTrue();
    }

    @Test
    void shouldFailWhenCurrentRequestIsMissing() {
        assertThatThrownBy(CurrentRequestHolder::current)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldClearCurrentRequest() {
        CurrentRequestHolder.set(new CurrentRequest());

        CurrentRequestHolder.clear();

        assertThat(CurrentRequestHolder.currentOrNull()).isNull();
    }
}
