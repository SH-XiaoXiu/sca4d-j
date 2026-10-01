package com.sca4d.core.api.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.data.DataValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证异步幂等处理权在目标完成前续期，所有权丢失后不保存结果。 */
class IdempotencyCallInterceptorAsyncTest {

    @Test
    void renewsUntilCompletionAndStopsBeforeSaving() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        try {
            Store store = new Store();
            IdempotencyCallInterceptor interceptor = interceptor(store, scheduler);
            CompletableFuture<CallOutcome> target = new CompletableFuture<>();

            CompletionStage<CallOutcome> result = interceptor.aroundAsync(execution(), () -> target);
            assertTrue(store.renewed.await(2, TimeUnit.SECONDS));
            assertEquals(0, store.saved.get());
            target.complete(CallOutcome.success("done"));

            assertEquals("done", result.toCompletableFuture().join().result());
            assertEquals(1, store.saved.get());
            assertEquals(0, store.released.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void doesNotSaveWhenRenewalLosesOwnership() throws Exception {
        CountDownLatch renewalStopped = new CountDownLatch(1);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1) {
            @Override
            protected void afterExecute(Runnable task, Throwable failure) {
                super.afterExecute(task, failure);
                if (task instanceof Future<?> future && future.isDone()) {
                    renewalStopped.countDown();
                }
            }
        };
        try {
            Store store = new Store();
            store.ownsProcessing = false;
            IdempotencyCallInterceptor interceptor = interceptor(store, scheduler);
            CompletableFuture<CallOutcome> target = new CompletableFuture<>();

            CompletionStage<CallOutcome> result = interceptor.aroundAsync(execution(), () -> target);
            assertTrue(store.renewed.await(2, TimeUnit.SECONDS));
            assertTrue(renewalStopped.await(2, TimeUnit.SECONDS));
            target.complete(CallOutcome.success("done"));

            assertNotNull(result.toCompletableFuture().join().completionFailure());
            assertEquals(0, store.saved.get());
            assertEquals(1, store.released.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static IdempotencyCallInterceptor interceptor(Store store, ScheduledThreadPoolExecutor scheduler) {
        CallValueResolverLookup lookup = type -> {
            throw new AssertionError("不应读取解析器");
        };
        SPICallValueResolver expressions = (expression, execution) -> {
            throw new AssertionError("不应读取表达式");
        };
        return new IdempotencyCallInterceptor(
                store,
                lookup,
                expressions,
                new IdempotencyFingerprints(new ObjectMapper()),
                Duration.ofMillis(90),
                Duration.ofMillis(10),
                scheduler);
    }

    private static CallExecution execution() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("submit");
        Call call = new Call(
                "async-idempotency",
                Instant.EPOCH,
                DataValues.builder().put(IdempotencyKey.INPUT, "client-key").build(),
                null);
        return new CallExecution(call, Fixture.class, method, new Object[0], String.class);
    }

    private static class Fixture {
        @Idempotent(retentionSeconds = 60)
        private CompletionStage<String> submit() {
            return CompletableFuture.completedFuture("done");
        }
    }

    private static class Store implements IdempotencyStore {
        private final CountDownLatch renewed = new CountDownLatch(1);

        private final AtomicInteger saved = new AtomicInteger();

        private final AtomicInteger released = new AtomicInteger();

        private volatile boolean ownsProcessing = true;

        @Override
        public <T> @NotNull IdempotencyBeginResult<T> begin(
                @NotNull IdempotencyKey key,
                @NotNull String fingerprint,
                @NotNull Type resultType,
                @NotNull Duration processingTtl,
                @NotNull Duration retryAfter) {
            return IdempotencyBeginResult.acquired("owner");
        }

        @Override
        public <T> boolean succeed(
                @NotNull IdempotencyKey key,
                @NotNull String token,
                @NotNull String fingerprint,
                @Nullable T result,
                @NotNull Type resultType,
                @NotNull Duration retention) {
            saved.incrementAndGet();
            return ownsProcessing;
        }

        @Override
        public boolean renew(@NotNull IdempotencyKey key, @NotNull String token, @NotNull Duration processingTtl) {
            renewed.countDown();
            return ownsProcessing;
        }

        @Override
        public boolean release(@NotNull IdempotencyKey key, @NotNull String token) {
            released.incrementAndGet();
            return ownsProcessing;
        }
    }
}
