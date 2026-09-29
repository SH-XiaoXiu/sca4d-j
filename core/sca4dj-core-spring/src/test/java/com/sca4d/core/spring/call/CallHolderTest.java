package com.sca4d.core.spring.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.data.DataValues;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 验证独立工作线程的 Call 绑定、失败清理及原 MDC 恢复。 */
class CallHolderTest {
    @AfterEach
    void clear() {
        CallHolder.clear();
        MDC.clear();
    }

    @Test
    void bindsIndependentWorkAndRestoresTheWorkerThread() throws Exception {
        Call parent = new Call("parent", Instant.EPOCH);
        CallHolder.set(parent);
        Call child = parent.fork("child", Instant.EPOCH, DataValues.builder().build(), null);
        try (var worker = Executors.newSingleThreadExecutor()) {
            assertThat(worker.submit(() -> CallHolder.run(child, () -> {
                assertThat(MDC.get("callId")).isEqualTo("child");
                return CallHolder.current();
            })).get(5, TimeUnit.SECONDS)).isSameAs(child);
            assertThat(worker.submit(CallHolder::currentOrNull).get(5, TimeUnit.SECONDS)).isNull();
        }
        assertThat(CallHolder.current()).isSameAs(parent);
    }

    @Test
    void restoresPriorBindingAndMdcOnFailure() {
        Call parent = new Call("parent", Instant.EPOCH);
        CallHolder.set(parent);
        MDC.put("callId", "parent");
        Call child = parent.fork("child", Instant.EPOCH, DataValues.builder().build(), null);
        assertThatThrownBy(() -> CallHolder.run(child, () -> {
            throw new IllegalStateException("failure");
        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(CallHolder.current()).isSameAs(parent);
        assertThat(MDC.get("callId")).isEqualTo("parent");
    }
}
