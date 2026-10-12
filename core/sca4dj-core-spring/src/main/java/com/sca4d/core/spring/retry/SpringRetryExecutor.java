package com.sca4d.core.spring.retry;

import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryDecision;
import com.sca4d.core.api.retry.RetryExecutor;
import com.sca4d.core.api.retry.RetryInterruptedException;
import com.sca4d.core.api.retry.RetryListener;
import com.sca4d.core.api.retry.RetryOperation;
import com.sca4d.core.api.retry.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetryTemplate;

import java.io.Serial;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * 使用 Spring Retry 执行同步重试，共同策略是次数和退避判断的唯一来源。
 */
public class SpringRetryExecutor implements RetryExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(SpringRetryExecutor.class);

    private final Sleeper sleeper;

    /**
     * 使用可中断的线程等待。
     */
    public SpringRetryExecutor() {
        this(Thread::sleep);
    }

    SpringRetryExecutor(Sleeper sleeper) {
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    @Override
    public <T, E extends Exception> @Nullable T execute(
            @NotNull RetryPolicy<Throwable> policy,
            @NotNull RetryOperation<T, E> operation,
            @NotNull RetryListener listener) throws E {
        Objects.requireNonNull(policy, "重试策略不能为空");
        Objects.requireNonNull(operation, "重试操作不能为空");
        Objects.requireNonNull(listener, "重试监听器不能为空");
        // 每次执行独立创建 Spring 上下文，规则对象可在调用间共享。
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new org.springframework.retry.RetryPolicy() {
            @Override
            public boolean canRetry(org.springframework.retry.RetryContext context) {
                Attempt state = (Attempt) context;
                RetryDecision decision = state.decision;
                return state.attempt == 0 || decision != null && decision.retrying();
            }

            @Override
            public org.springframework.retry.RetryContext open(org.springframework.retry.RetryContext parent) {
                return new Attempt(parent);
            }

            @Override
            public void close(org.springframework.retry.RetryContext context) {
            }

            @Override
            public void registerThrowable(org.springframework.retry.RetryContext context, Throwable throwable) {
                Attempt state = (Attempt) context;
                state.registerThrowable(throwable);
                state.decision = null;
                RetryContext<Throwable> failure = RetryContext.failed(state.attempt, throwable);
                state.failure = failure;
                if (throwable instanceof Error
                        || throwable instanceof CancellationException
                        || throwable instanceof RetryInterruptedException
                        || Thread.currentThread().isInterrupted())
                    return;
                if (throwable instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                // Spring 会反复询问 canRetry，每次失败只在此处计算一次决定。
                RetryDecision decision = Objects.requireNonNull(policy.evaluate(failure), "重试策略不能返回 null");
                state.decision = decision;
                notifySafely(() -> listener.failed(failure, decision));
                LOG.trace("同步操作重试判断已完成 | attempt={} retrying={}", state.attempt, decision.retrying());
            }
        });
        template.setBackOffPolicy(new BackOffPolicy() {
            @Override
            public BackOffContext start(org.springframework.retry.RetryContext context) {
                return (Attempt) context;
            }

            @Override
            public void backOff(BackOffContext context) {
                Attempt state = (Attempt) context;
                Duration delay = Objects.requireNonNull(state.decision).retryDelay().orElseThrow();
                RetryContext<Throwable> contextFailure = Objects.requireNonNull(state.failure);
                notifySafely(() -> listener.waiting(contextFailure, delay));
                try {
                    sleeper.sleep(delay.toMillis());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new RetryInterruptedException(failure);
                } finally {
                    notifySafely(() -> listener.waited(contextFailure));
                }
            }
        });
        return template.execute(context -> {
            Attempt state = (Attempt) context;
            state.attempt = Math.incrementExact(state.attempt);
            if (Thread.currentThread().isInterrupted()) {
                throw new RetryInterruptedException(new InterruptedException("调用线程已经中断"));
            }
            T result = operation.run();
            notifySafely(() -> listener.succeeded(state.attempt));
            return result;
        });
    }

    /**
     * 观察失败不会改变业务结果，也不会触发下一次操作。
     */
    private static void notifySafely(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException failure) {
            LOG.warn("重试观察器失败，不影响执行结果 | failureType={}", failure.getClass().getSimpleName());
        }
    }

    /**
     * 保存单次 execute 内的 long 计数和已计算决定，不依赖 Spring 的 int 次数；失败判断不参与上下文序列化。
     */
    private static class Attempt extends RetryContextSupport implements BackOffContext {
        @Serial
        private static final long serialVersionUID = 1L;

        private long attempt;

        private transient @Nullable RetryContext<Throwable> failure;

        private transient @Nullable RetryDecision decision;

        private Attempt(org.springframework.retry.RetryContext parent) {
            super(parent);
        }
    }
}
