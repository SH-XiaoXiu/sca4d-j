package com.sca4d.core.spring.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryDecision;
import com.sca4d.core.api.retry.RetryListener;
import com.sca4d.core.api.retry.RetryPolicy;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.api.retry.exception.RetryInterruptedException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证 SpringRetryExecutor 同步执行时保留原异常、按 RetryRule 等待、中断后停止重试、隔离观察者异常，并让并发调用各自计数。 */
class SpringRetryExecutorTest {
    @Test
    @DisplayName("按共同规则等待且每次失败只判断一次")
    void evaluatesEachFailureOnlyOnce() {
        List<Long> sleeps = new ArrayList<>();
        SpringRetryExecutor executor = new SpringRetryExecutor(sleeps::add);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        RetryPolicy<Throwable> rule = RetryPolicy.always(
                RetryRule.atMost(
                        4,
                        Backoff.exponential(Duration.ofMillis(1), 2, Duration.ofMillis(10))));
        String value = executor.execute(context -> {
            evaluations.incrementAndGet();
            return rule.evaluate(context);
        }, () -> {
            if (calls.incrementAndGet() < 4)
                throw new IllegalStateException("temporary");
            return "done";
        });
        assertThat(value).isEqualTo("done");
        assertThat(sleeps).containsExactly(1L, 2L, 4L);
        assertThat(evaluations).hasValue(3);
    }

    @Test
    @DisplayName("耗尽保留原检查异常，取消和 Error 不重试")
    void preservesFailuresAndDoesNotRetryCancellation() {
        SpringRetryExecutor executor = new SpringRetryExecutor(millis -> {
        });
        RetryPolicy<Throwable> policy = RetryPolicy.always(RetryRule.atMost(3, Backoff.fixed(Duration.ZERO)));
        IOException original = new IOException("failure");
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(policy, () -> {
            attempts.incrementAndGet();
            throw original;
        })).isSameAs(original);
        assertThat(attempts).hasValue(3);
        attempts.set(0);
        CancellationException cancelled = new CancellationException();
        assertThatThrownBy(() -> executor.execute(policy, () -> {
            attempts.incrementAndGet();
            throw cancelled;
        })).isSameAs(cancelled);
        assertThat(attempts).hasValue(1);
        AssertionError error = new AssertionError("fatal");
        assertThatThrownBy(() -> executor.execute(policy, () -> {
            throw error;
        })).isSameAs(error);
    }

    @Test
    @DisplayName("等待中断保留线程标志且不开始下一次操作")
    void interruptionStopsWaiting() {
        AtomicInteger attempts = new AtomicInteger();
        SpringRetryExecutor executor = new SpringRetryExecutor(millis -> {
            throw new InterruptedException();
        });
        try {
            assertThatThrownBy(
                    () -> executor.execute(
                            RetryPolicy.always(RetryRule.atMost(3, Backoff.fixed(Duration.ofMillis(1)))),
                            () -> {
                                attempts.incrementAndGet();
                                throw new IllegalStateException();
                            }))
                    .isInstanceOf(RetryInterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(attempts).hasValue(1);
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test
    @DisplayName("观察异常不能重跑成功操作或改变次数")
    void isolatesListenerFailure() {
        AtomicInteger attempts = new AtomicInteger();
        String result = new SpringRetryExecutor(millis -> {
        }).execute(
                RetryPolicy.always(RetryRule.atMost(2, Backoff.fixed(Duration.ZERO))),
                () -> {
                    if (attempts.incrementAndGet() == 1)
                        throw new IllegalStateException();
                    return "ok";
                },
                new RetryListener() {
                    @Override
                    public void failed(@NotNull RetryContext<Throwable> context, @NotNull RetryDecision decision) {
                        throw new IllegalStateException();
                    }

                    @Override
                    public void succeeded(long attempt) {
                        throw new IllegalStateException();
                    }
                });
        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("共享执行器和策略的并发操作使用独立计数")
    void isolatesConcurrentCalls() throws Exception {
        SpringRetryExecutor retries = new SpringRetryExecutor(millis -> {
        });
        RetryPolicy<Throwable> policy = RetryPolicy.always(RetryRule.atMost(3, Backoff.fixed(Duration.ZERO)));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> runOperation(retries, policy));
            var second = workers.submit(() -> runOperation(retries, policy));
            assertThat(first.get()).isEqualTo(3);
            assertThat(second.get()).isEqualTo(3);
        }
    }

    private static int runOperation(SpringRetryExecutor executor, RetryPolicy<Throwable> policy) {
        AtomicInteger attempts = new AtomicInteger();
        return Objects.requireNonNull(executor.execute(policy, () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3)
                throw new IllegalStateException();
            return attempt;
        }));
    }
}
