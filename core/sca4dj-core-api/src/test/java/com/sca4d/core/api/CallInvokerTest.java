package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.Idempotent;
import com.sca4d.core.api.annotation.Locked;
import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.audit.AuditCallInterceptor;
import com.sca4d.core.api.audit.AuditPayloadFactory;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.audit.AuditResolverLookup;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyBeginResult;
import com.sca4d.core.api.concurrent.IdempotencyCallInterceptor;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.LockCallInterceptor;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.concurrent.RateLimitCallInterceptor;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.concurrent.RateLimitExceededException;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.permission.ResourceScope;
import com.sca4d.core.api.permission.ResourceScopeRequirement;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.resource.ResourceReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@SuppressWarnings({"ClassCanBeRecord", "ExtractMethodRecommender"})
class CallInvokerTest {

    private static final ScheduledExecutorService RENEWAL_SCHEDULER = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "call-invoker-test-renewal");
        thread.setDaemon(true);
        return thread;
    });

    @org.junit.jupiter.api.AfterAll
    static void stopRenewalScheduler() {
        RENEWAL_SCHEDULER.shutdown();
    }

    @Test
    @DisplayName("审计方法显式方式覆盖类型，类型显式方式覆盖应用默认")
    void selectsAuditModeByMethodTypeAndApplicationDefault() throws Exception {
        List<Audit.Mode> recorded = new ArrayList<>();
        AuditRecorder queued = new AuditRecorder() {
            @Override
            public @NotNull Audit.Mode mode() {
                return Audit.Mode.QUEUED;
            }

            @Override
            public void record(@NotNull AuditRecord record) {
                recorded.add(Audit.Mode.QUEUED);
            }
        };
        for (Audit.Mode defaultMode : List.of(Audit.Mode.DIRECT, Audit.Mode.QUEUED)) {
            recorded.clear();
            CallInvoker lifecycle = new CallInvoker(
                    List.of(
                            new IdentityCallInterceptor(),
                            new AuditCallInterceptor(
                                    List.<AuditRecorder>of(record -> recorded.add(Audit.Mode.DIRECT), queued),
                                    defaultMode,
                                    NO_AUDIT_RESOLVERS,
                                    NO_EXPRESSIONS,
                                    Clock.fixed(NOW, ZoneOffset.UTC))));
            for (Method method : List.of(
                    AuditModeService.class.getDeclaredMethod("inherited"),
                    AuditModeService.class.getDeclaredMethod("direct"),
                    AuditModeService.class.getDeclaredMethod("queued"))) {
                execute(
                        lifecycle,
                        Call.system(method.getName(), NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        AuditModeService.class,
                        method,
                        new Object[0],
                        () -> "done");
            }
            execute(
                    lifecycle,
                    Call.system("configured", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                    TargetedService.class,
                    TargetedService.class.getDeclaredMethod("update", long.class),
                    new Object[] {42L},
                    () -> "done");
            assertEquals(List.of(Audit.Mode.QUEUED, Audit.Mode.DIRECT, Audit.Mode.QUEUED, defaultMode), recorded);
        }
    }

    @Test
    @DisplayName("局部审计方式未安装时在业务执行前失败")
    void rejectsUnavailableAuditModeBeforeInvocation() {
        AtomicInteger invocations = new AtomicInteger();
        CallInvoker lifecycle = new CallInvoker(
                List.of(
                        new IdentityCallInterceptor(),
                        new AuditCallInterceptor(List.<AuditRecorder>of(record -> {
                            throw new AssertionError("未执行业务时不应记录成功审计");
                        }), Audit.Mode.DIRECT, NO_AUDIT_RESOLVERS, NO_EXPRESSIONS, Clock.fixed(NOW, ZoneOffset.UTC))));
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        lifecycle,
                        Call.system("unavailable", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        AuditModeService.class,
                        AuditModeService.class.getDeclaredMethod("queued"),
                        new Object[0],
                        () -> {
                            invocations.incrementAndGet();
                            return "done";
                        }));
        assertTrue(failure.getMessage().contains("QUEUED"));
        assertEquals(0, invocations.get());
    }

    @Test
    @DisplayName("Future 幂等声明在业务和存储操作之前被拒绝")
    void rejectsFutureIdempotencyBeforeInvocation() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicInteger invocations = new AtomicInteger();
        CallInvoker lifecycle = new CallInvoker(
                List.of(
                        new IdentityCallInterceptor(),
                        new IdempotencyCallInterceptor(new MemoryIdempotencyStore(events), type -> {
                            throw new AssertionError("非法返回类型不应解析幂等键");
                        },
                                NO_EXPRESSIONS,
                                IDEMPOTENCY_FINGERPRINTS,
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(1),
                                RENEWAL_SCHEDULER)));
        Method method = IdempotentService.class.getDeclaredMethod("submit");
        CallExecution execution = new CallExecution(
                Call.system("future", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                IdempotentService.class,
                method,
                new Object[0],
                method.getGenericReturnType());
        assertThrows(IllegalStateException.class, () -> lifecycle.execute(execution, () -> {
            invocations.incrementAndGet();
            return CompletableFuture.completedFuture("done");
        }));
        assertEquals(0, invocations.get());
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("幂等内层未返回结果时释放处理权")
    void releasesIdempotencyWhenInnerOutcomeIsNull() throws Exception {
        List<String> events = new ArrayList<>();
        IdempotencyCallInterceptor interceptor = new IdempotencyCallInterceptor(
                new MemoryIdempotencyStore(events),
                type -> new IdempotencyKeyResolver(),
                NO_EXPRESSIONS,
                IDEMPOTENCY_FINGERPRINTS,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                RENEWAL_SCHEDULER);
        Method method = IdempotentService.class.getDeclaredMethod("create");
        CallExecution execution = new CallExecution(
                Call.system("missing-outcome", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                IdempotentService.class,
                method,
                new Object[0],
                method.getGenericReturnType());

        assertThrows(NullPointerException.class, () -> interceptor.around(execution, () -> null));
        assertEquals(List.of("idempotency-begin", "idempotency-release"), events);
    }

    @Test
    @DisplayName("目标抛出 Error 时释放幂等处理权，下一次调用可以重新取得")
    void releasesIdempotencyOnError() throws Exception {
        List<String> events = new ArrayList<>();
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of()),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(events),
                new RecordingDistributedLock(events),
                record -> {
                },
                type -> new IdempotencyKeyResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1));
        Method method = IdempotentService.class.getDeclaredMethod("create");
        AssertionError original = new AssertionError("target failed");
        assertSame(
                original,
                assertThrows(
                        AssertionError.class,
                        () -> execute(
                                executor,
                                Call.system("error", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                                IdempotentService.class,
                                method,
                                new Object[0],
                                () -> {
                                    throw original;
                                })));
        assertEquals(
                "done",
                execute(
                        executor,
                        Call.system("retry", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        IdempotentService.class,
                        method,
                        new Object[0],
                        () -> "done"));
        assertTrue(events.contains("idempotency-release"));
    }

    @Test
    @DisplayName("同步解锁失败保留业务结果并允许同键重放")
    void replaysSuccessfulResultAfterSynchronousUnlockFailure() throws Exception {
        List<String> events = new ArrayList<>();
        RuntimeException unlockFailure = new IllegalStateException("unlock failed");
        DistributedLock lock = new DistributedLock() {
            @Override
            public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
                task.get();
                throw unlockFailure;
            }
        };
        CallInvoker invoker = idempotentLockedInvoker(new MemoryIdempotencyStore(events), lock);
        Method method = IdempotentService.class.getDeclaredMethod("createUnderLock");
        AtomicInteger invocations = new AtomicInteger();

        assertSame(
                unlockFailure,
                assertThrows(
                        IllegalStateException.class,
                        () -> execute(
                                invoker,
                                systemCall("first"),
                                IdempotentService.class,
                                method,
                                new Object[0],
                                () -> {
                                    invocations.incrementAndGet();
                                    return "created";
                                })));
        assertEquals(
                "created",
                execute(
                        invoker,
                        systemCall("retry"),
                        IdempotentService.class,
                        method,
                        new Object[0],
                        () -> {
                            invocations.incrementAndGet();
                            return "duplicate";
                        }));
        assertEquals(1, invocations.get());
        assertTrue(events.contains("idempotency-succeed"));
        assertFalse(events.contains("idempotency-release"));
    }

    @Test
    @DisplayName("异步解锁失败保留业务结果并允许同键重放")
    void replaysSuccessfulResultAfterAsynchronousUnlockFailure() throws Exception {
        List<String> events = new ArrayList<>();
        RuntimeException unlockFailure = new IllegalStateException("async unlock failed");
        DistributedLock lock = new DistributedLock() {
            @Override
            public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
                return task.get();
            }

            @Override
            public <T> CompletionStage<T> executeAsync(
                    LockKey key,
                    Duration waitTime,
                    Supplier<? extends CompletionStage<T>> task) {
                return task.get().thenCompose(value -> CompletableFuture.failedFuture(unlockFailure));
            }
        };
        CallInvoker invoker = idempotentLockedInvoker(new MemoryIdempotencyStore(events), lock);
        Method method = IdempotentService.class.getDeclaredMethod("submitUnderLock");
        AtomicInteger invocations = new AtomicInteger();

        assertSame(
                unlockFailure,
                assertThrows(
                        CompletionException.class,
                        () -> invoker.executeAsync(
                                new CallExecution(
                                        systemCall("first"),
                                        IdempotentService.class,
                                        method,
                                        new Object[0],
                                        String.class),
                                () -> {
                                    invocations.incrementAndGet();
                                    return CompletableFuture.completedFuture("created");
                                }).toCompletableFuture().join())
                        .getCause());
        assertEquals(
                "created",
                invoker.executeAsync(
                        new CallExecution(
                                systemCall("retry"),
                                IdempotentService.class,
                                method,
                                new Object[0],
                                String.class),
                        () -> {
                            invocations.incrementAndGet();
                            return CompletableFuture.completedFuture("duplicate");
                        }).toCompletableFuture().join());
        assertEquals(1, invocations.get());
        assertTrue(events.contains("idempotency-succeed"));
        assertFalse(events.contains("idempotency-release"));
    }

    private static Call systemCall(String callId) {
        return Call.system(callId, NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
    }

    private static CallInvoker idempotentLockedInvoker(IdempotencyStore store, DistributedLock lock) {
        CallValueResolverLookup resolvers = type -> {
            if (type == IdempotencyKeyResolver.class) {
                return new IdempotencyKeyResolver();
            }
            if (type == LockKeyResolver.class) {
                return new LockKeyResolver();
            }
            throw new IllegalArgumentException("未知解析器: " + type);
        };
        return new CallInvoker(
                List.of(
                        new IdempotencyCallInterceptor(
                                store,
                                resolvers,
                                NO_EXPRESSIONS,
                                IDEMPOTENCY_FINGERPRINTS,
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(1),
                                RENEWAL_SCHEDULER),
                        new LockCallInterceptor(lock, resolvers, NO_EXPRESSIONS)));
    }

    @Test
    @DisplayName("锁内扩展的重放结果经过外层仍保持 REPLAYED")
    void preservesInnerReplayThroughControls() throws Exception {
        CallInvoker executor = extended(
                List.of(
                        new Hook(
                                TreeCode.of("TEST/INNER_CACHE"),
                                Set.of(LockCallInterceptor.CODE),
                                Set.of(),
                                0,
                                next -> CallOutcome.replayed("cached"))));
        Method method = RateLimitedService.class.getDeclaredMethod("overloaded");
        CallExecution execution = new CallExecution(
                Call.system("inner-replay", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                RateLimitedService.class,
                method,
                new Object[0],
                String.class);
        assertEquals("cached", executor.execute(execution, () -> {
            throw new AssertionError("不能执行目标");
        }));
        assertEquals(CallOutcome.Status.REPLAYED, execution.outcome().orElseThrow().status());
    }

    @Test
    @DisplayName("扩展按依赖进入并反向退出，优先级不能越过依赖")
    void ordersExtensionsAndUnwindsThem() throws Exception {
        List<String> events = new ArrayList<>();
        TreeCode a = TreeCode.of("TEST/A");
        TreeCode b = TreeCode.of("TEST/B");
        CallInvoker executor = extended(
                List.of(
                        new Hook(b, Set.of(a), Set.of(IdempotencyCallInterceptor.CODE), -10, next -> {
                            events.add("b-enter");
                            CallOutcome result = next.proceed();
                            events.add("b-exit");
                            return result;
                        }),
                        new Hook(a, Set.of(PermissionCallInterceptor.CODE), Set.of(b), 10, next -> {
                            events.add("a-enter");
                            CallOutcome result = next.proceed();
                            events.add("a-exit");
                            return result;
                        })));
        assertEquals("done", invokeExtended(executor, () -> {
            events.add("target");
            return "done";
        }));
        assertEquals(List.of("a-enter", "b-enter", "target", "b-exit", "a-exit"), events);
    }

    @Test
    @DisplayName("缺失依赖、重复编码和环在构建时失败")
    void rejectsInvalidExtensionDependencies() {
        TreeCode a = TreeCode.of("TEST/A");
        TreeCode b = TreeCode.of("TEST/B");
        Hook first = new Hook(a, Set.of(b), Set.of(), 0, CallLifecycle.Next::proceed);
        assertThrows(IllegalArgumentException.class, () -> extended(List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> extended(List.of(first, first)));
        assertThrows(
                IllegalArgumentException.class,
                () -> extended(
                        List.of(
                                first,
                                new Hook(b, Set.of(a), Set.of(), 0, CallLifecycle.Next::proceed))));
    }

    @Test
    @DisplayName("短路必须标记重放且不得执行目标")
    void allowsExplicitReplayWithoutInvokingTarget() throws Exception {
        CallInvoker executor = extended(
                List.of(
                        new Hook(
                                TreeCode.of("TEST/CACHE"),
                                Set.of(RateLimitCallInterceptor.CODE),
                                Set.of(IdempotencyCallInterceptor.CODE),
                                0,
                                next -> CallOutcome.replayed("cached"))));
        assertEquals("cached", invokeExtended(executor, () -> {
            throw new AssertionError("不应执行目标");
        }));
    }

    @Test
    @DisplayName("Next 不能重复使用、回调结束后使用或吞掉目标失败")
    void rejectsRepeatedAndEscapedNext() {
        AtomicReference<CallLifecycle.Next> saved = new AtomicReference<>();
        CallInvoker executor = extended(
                List.of(
                        new Hook(
                                TreeCode.of("TEST/ONCE"),
                                Set.of(RateLimitCallInterceptor.CODE),
                                Set.of(IdempotencyCallInterceptor.CODE),
                                0,
                                next -> {
                                    saved.set(next);
                                    next.proceed();
                                    return next.proceed();
                                })));
        AtomicInteger invocations = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> invokeExtended(executor, () -> {
            invocations.incrementAndGet();
            return "done";
        }));
        assertEquals(1, invocations.get());
        assertThrows(IllegalStateException.class, () -> saved.get().proceed());
        CallInvoker swallow = extended(
                List.of(
                        new Hook(
                                TreeCode.of("TEST/SWALLOW"),
                                Set.of(RateLimitCallInterceptor.CODE),
                                Set.of(IdempotencyCallInterceptor.CODE),
                                0,
                                next -> {
                                    next.proceed();
                                    return CallOutcome.success("ignored");
                                })));
        IllegalStateException original = new IllegalStateException("target failure");
        assertSame(original, assertThrows(IllegalStateException.class, () -> invokeExtended(swallow, () -> {
            throw original;
        })));
        assertTrue(original.getSuppressed().length > 0);
    }

    /** 为扩展行为测试组装无外部存储访问的真实执行链。 */
    private static CallInvoker extended(List<CallLifecycle.Around> extensions) {
        CallValueResolverLookup resolvers = type -> {
            throw new AssertionError("不需要解析器");
        };
        List<CallLifecycle.Around> registered = new ArrayList<>(
                List.of(
                        new IdentityCallInterceptor(),
                        new PermissionCallInterceptor(new PermissionAssessment(List.of()), resolvers, NO_EXPRESSIONS),
                        new RateLimitCallInterceptor(
                                (key, limit) -> RateLimitDecision.allow(),
                                resolvers,
                                NO_EXPRESSIONS),
                        new IdempotencyCallInterceptor(
                                new MemoryIdempotencyStore(new ArrayList<>()),
                                resolvers,
                                NO_EXPRESSIONS,
                                IDEMPOTENCY_FINGERPRINTS,
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(1),
                                RENEWAL_SCHEDULER),
                        new LockCallInterceptor(
                                new RecordingDistributedLock(new ArrayList<>()),
                                resolvers,
                                NO_EXPRESSIONS),
                        new AuditCallInterceptor(List.<AuditRecorder>of(record -> {
                        }), Audit.Mode.DIRECT, NO_AUDIT_RESOLVERS, NO_EXPRESSIONS, Clock.fixed(NOW, ZoneOffset.UTC))));
        registered.addAll(extensions);
        return new CallInvoker(registered);
    }

    /** 在固定匿名方法范围中执行目标，复用同一组公开执行规则。 */
    private static String invokeExtended(CallInvoker lifecycle, Callable<String> target) throws Exception {
        Method method = RateLimitedService.class.getDeclaredMethod("overloaded");
        return execute(
                lifecycle,
                Call.system("extension", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                RateLimitedService.class,
                method,
                new Object[0],
                target);
    }

    /** 保存测试中的环绕声明和行为，测试断言由实际调用结果表达。 */
    private static class Hook implements CallLifecycle.Around {
        private final java.util.function.Function<CallLifecycle.Next, CallOutcome> action;

        private final Set<TreeCode> after;

        private final Set<TreeCode> before;

        private final TreeCode code;

        private final int priority;

        private Hook(
                TreeCode code,
                Set<TreeCode> after,
                Set<TreeCode> before,
                int priority,
                java.util.function.Function<CallLifecycle.Next, CallOutcome> action) {
            this.code = code;
            this.after = after;
            this.before = before;
            this.priority = priority;
            this.action = action;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public @NotNull Set<TreeCode> after() {
            return after;
        }

        @Override
        public @NotNull Set<TreeCode> before() {
            return before;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
            return action.apply(next);
        }
    }

    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    private static final IdempotencyFingerprints IDEMPOTENCY_FINGERPRINTS = new IdempotencyFingerprints(
            new ObjectMapper());

    private static final AuditResolverLookup NO_AUDIT_RESOLVERS = new AuditResolverLookup() {
        @Override
        public @NotNull AuditTargetResolver targetResolver(
                @NotNull Class<? extends AuditTargetResolver> resolverType) {
            throw new AssertionError("当前测试不应查找审计目标解析器: " + resolverType.getName());
        }

        @Override
        public @NotNull AuditPayloadFactory payloadFactory(
                @NotNull Class<? extends AuditPayloadFactory> factoryType) {
            throw new AssertionError("当前测试不应查找审计 payload 工厂: " + factoryType.getName());
        }
    };

    private static final SPICallValueResolver NO_EXPRESSIONS = (expression, execution) -> {
        throw new AssertionError("当前测试不应解析注解表达式: " + expression);
    };

    @Test
    @DisplayName("Executes Permission Rate Limit Idempotency Lock Invocation And Audit In Order")
    void executesPermissionRateLimitIdempotencyLockInvocationAndAuditInOrder() throws Exception {
        List<String> events = new ArrayList<>();
        MemoryIdempotencyStore idempotencyStore = new MemoryIdempotencyStore(events);
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of(new OperationPolicy(events))),
                (key, limit) -> {
                    events.add("rate-limit");
                    return RateLimitDecision.allow();
                },
                idempotencyStore,
                new RecordingDistributedLock(events),
                record -> events.add("audit-" + record.result().name().toLowerCase()),
                resolverType -> {
                    if (resolverType == RateKeyResolver.class) {
                        return new RateKeyResolver();
                    }
                    if (resolverType == IdempotencyKeyResolver.class) {
                        return new IdempotencyKeyResolver();
                    }
                    if (resolverType == LockKeyResolver.class) {
                        return new LockKeyResolver();
                    }
                    throw new IllegalArgumentException("未知测试解析器: " + resolverType.getName());
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
        Method method = SecuredService.class.getDeclaredMethod("update");
        Call call = Call.system("call-1", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));

        String result = execute(
                executor,
                call,
                SecuredService.class,
                method,
                new Object[0],
                () -> {
                    events.add("invoke");
                    return "done";
                });

        assertEquals("done", result);
        assertEquals(
                List.of(
                        "permission",
                        "rate-limit",
                        "rate-limit",
                        "idempotency-begin",
                        "lock",
                        "invoke",
                        "idempotency-succeed",
                        "audit-success"),
                events);

        events.clear();
        Call repeatedCall = Call.system("call-2", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        String repeated = execute(
                executor,
                repeatedCall,
                SecuredService.class,
                method,
                new Object[0],
                () -> {
                    throw new AssertionError("幂等结果存在时不应再次执行业务方法");
                });
        assertEquals("done", repeated);
        assertEquals(
                List.of("permission", "rate-limit", "rate-limit", "idempotency-begin", "audit-success"),
                events);
    }

    @Test
    @DisplayName("幂等结果未保存时不确认业务调用成功")
    void failsWhenIdempotencyResultCannotBeStored() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        IdempotencyStore store = new IdempotencyStore() {
            @Override
            public <T> @NotNull IdempotencyBeginResult<T> begin(
                    @NotNull IdempotencyKey key,
                    @NotNull String fingerprint,
                    @NotNull Type resultType,
                    @NotNull Duration processingTtl,
                    @NotNull Duration retryAfter) {
                return IdempotencyBeginResult.acquired("completion-token");
            }

            @Override
            public <T> boolean succeed(
                    @NotNull IdempotencyKey key,
                    @NotNull String token,
                    @NotNull String fingerprint,
                    @Nullable T result,
                    @NotNull Type resultType,
                    @NotNull Duration retention) {
                return false;
            }

            @Override
            public boolean release(@NotNull IdempotencyKey key, @NotNull String token) {
                throw new AssertionError("业务已经执行成功时不能释放幂等处理中记录");
            }

            @Override
            public boolean renew(@NotNull IdempotencyKey key, @NotNull String token, @NotNull Duration ttl) {
                return true;
            }
        };
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of()),
                (key, limit) -> RateLimitDecision.allow(),
                store,
                new RecordingDistributedLock(new ArrayList<>()),
                record -> {
                },
                resolverType -> new IdempotencyKeyResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("completion-failure", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        IdempotentService.class,
                        IdempotentService.class.getDeclaredMethod("create"),
                        new Object[0],
                        () -> {
                            invocations.incrementAndGet();
                            return "created";
                        }));

        assertEquals("业务调用已完成，但当前请求未能保存幂等结果", failure.getMessage());
        assertEquals(1, invocations.get());
    }

    @Test
    @DisplayName("审计记录抛出业务异常时只记录一次并把调用标记为失败")
    void failsWithoutRecordingAgainWhenAuditThrowsBusinessException() throws Exception {
        AtomicInteger records = new AtomicInteger();
        BusinessException auditFailure = new BusinessException(CoreErrorCode.INTERNAL);
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of()),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(new ArrayList<>()),
                new RecordingDistributedLock(new ArrayList<>()),
                record -> {
                    records.incrementAndGet();
                    throw auditFailure;
                },
                resolverType -> {
                    throw new AssertionError("当前审计声明不需要解析器");
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
        Method method = TargetedService.class.getDeclaredMethod("update", long.class);
        CallExecution execution = new CallExecution(
                Call.system("audit-failure", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                TargetedService.class,
                method,
                new Object[] {1L},
                String.class);

        BusinessException thrown = assertThrows(
                BusinessException.class,
                () -> executor.execute(execution, () -> "updated"));

        assertSame(auditFailure, thrown);
        assertEquals(1, records.get());
        CallOutcome outcome = execution.outcome().orElseThrow();
        assertEquals(CallOutcome.Status.SUCCESS, outcome.status());
        assertEquals("updated", outcome.result());
        assertSame(auditFailure, outcome.completionFailure());
    }

    @Test
    @DisplayName("从 AuditTarget 参数建立 ResourceReference")
    void resolvesSimpleAuditTargetParameter() throws Exception {
        AtomicReference<AuditRecord> recorded = new AtomicReference<>();
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of()),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(new ArrayList<>()),
                new RecordingDistributedLock(new ArrayList<>()),
                recorded::set,
                resolverType -> {
                    throw new AssertionError("简单 AuditTarget 不应查找 CallValueResolver");
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
        Method method = TargetedService.class.getDeclaredMethod("update", long.class);

        String result = execute(
                executor,
                Call.system("target-call", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                TargetedService.class,
                method,
                new Object[] {42L},
                () -> "done");

        assertEquals("done", result);
        Resource target = recorded.get().target().orElseThrow();
        assertEquals(new ResourceReference(Account.RESOURCE_TYPE, "platform:42"), target);
    }

    @Test
    @DisplayName("Records Business Resource Scope Without Exact Session Grant")
    void recordsBusinessResourceScopeWithoutExactSessionGrant() throws Exception {
        DeviceScope scope = new DeviceScope();
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of(new DevicePolicy(scope))),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(new ArrayList<>()),
                new RecordingDistributedLock(new ArrayList<>()),
                record -> {
                },
                resolverType -> {
                    if (resolverType == DeviceCollectionResolver.class) {
                        return new DeviceCollectionResolver();
                    }
                    throw new IllegalArgumentException("未知测试解析器: " + resolverType.getName());
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
        Method method = DeviceService.class.getDeclaredMethod("list");
        Call call = Call.system("device-list", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));

        Permission permission = new Permission(new DeviceCollection(), Action.READ);
        CallExecution execution = new CallExecution(
                call,
                DeviceService.class,
                method,
                new Object[0],
                String.class);
        String result = executor.execute(execution, () -> {
            assertSame(scope, ResourceScopeRequirement.consume(execution, permission, DeviceScope.class).orElseThrow());
            return "devices";
        });

        assertEquals("devices", result);
    }

    @Test
    @DisplayName("Rejects Successful Return When Resource Scope Was Not Consumed")
    void rejectsUnconsumedResourceScope() throws Exception {
        DeviceScope scope = new DeviceScope();
        CallInvoker lifecycle = lifecycle(
                new PermissionAssessment(List.of(new DevicePolicy(scope))),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(new ArrayList<>()),
                new RecordingDistributedLock(new ArrayList<>()),
                record -> {
                },
                resolverType -> new DeviceCollectionResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
        Method method = DeviceService.class.getDeclaredMethod("list");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        lifecycle,
                        Call.system("device-list-unconsumed", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        DeviceService.class,
                        method,
                        new Object[0],
                        () -> "devices"));

        assertTrue(failure.getMessage().contains("没有被 Repository 消费"));
    }

    @Test
    @DisplayName("Deduplicates Permission Declarations Before Policy Execution")
    void deduplicatesPermissionDeclarationsBeforePolicyExecution() throws Exception {
        List<String> events = new ArrayList<>();
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of(new OperationPolicy(events))),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(events),
                new RecordingDistributedLock(events),
                record -> {
                },
                resolverType -> {
                    throw new IllegalArgumentException("重复权限测试不需要动态值解析器");
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
        Method method = DuplicatePermissionService.class.getDeclaredMethod("invoke");
        Call call = Call.system("duplicate", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));

        String result = execute(
                executor,
                call,
                DuplicatePermissionService.class,
                method,
                new Object[0],
                () -> "done");

        assertEquals("done", result);
        assertEquals(List.of("permission"), events);
    }

    @Test
    @DisplayName("Assesses Every Distinct Resource Resolved By One Permission Declaration")
    void assessesEveryDistinctResolvedResource() throws Exception {
        List<String> events = new ArrayList<>();
        CallInvoker executor = batchExecutor(events, null, new BatchResourcesResolver());
        Method method = BatchService.class.getDeclaredMethod("read", List.class);

        String result = execute(
                executor,
                Call.system("batch-allowed", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                BatchService.class,
                method,
                new Object[] {List.of(1L, 2L, 1L)},
                () -> {
                    events.add("invoke");
                    return "done";
                });

        assertEquals("done", result);
        assertEquals(List.of("permission-1", "permission-2", "invoke"), events);
    }

    @Test
    @DisplayName("Stops A Resource Batch At The First Denied Permission")
    void stopsResourceBatchAtFirstDeniedPermission() throws Exception {
        List<String> events = new ArrayList<>();
        CallInvoker executor = batchExecutor(events, "2", new BatchResourcesResolver());
        Method method = BatchService.class.getDeclaredMethod("read", List.class);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> execute(
                        executor,
                        Call.system("batch-denied", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        BatchService.class,
                        method,
                        new Object[] {List.of(1L, 2L, 3L)},
                        () -> {
                            throw new AssertionError("批量资源中有拒绝结果时不应执行业务方法");
                        }));

        assertEquals(CoreErrorCode.PERMISSION_DENIED, failure.errorCode());
        assertEquals(List.of("permission-1", "permission-2"), events);
    }

    @Test
    @DisplayName("Rejects Empty Or Invalid Resource Collections")
    void rejectsEmptyOrInvalidResourceCollections() throws Exception {
        Method batchMethod = BatchService.class.getDeclaredMethod("read", List.class);
        CallInvoker emptyExecutor = batchExecutor(
                new ArrayList<>(),
                null,
                new BatchResourcesResolver());
        assertThrows(
                IllegalStateException.class,
                () -> execute(
                        emptyExecutor,
                        Call.system("batch-empty", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        BatchService.class,
                        batchMethod,
                        new Object[] {List.of()},
                        () -> "unexpected"));

        for (Class<?> serviceType : List.of(NullResourceService.class, InvalidResourceService.class)) {
            Method method = serviceType.getDeclaredMethod("read");
            CallValueResolver<?> resolver = serviceType == NullResourceService.class
                    ? new NullResourceResolver()
                    : new InvalidResourceResolver();
            CallInvoker executor = batchExecutor(new ArrayList<>(), null, resolver);
            assertThrows(
                    IllegalStateException.class,
                    () -> execute(
                            executor,
                            Call.system("batch-invalid", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                            serviceType,
                            method,
                            new Object[0],
                            () -> "unexpected"));
        }
    }

    /**
     * 创建批量资源权限测试使用的调用执行器。
     */
    private static CallInvoker batchExecutor(
            List<String> events,
            @Nullable String deniedId,
            CallValueResolver<?> resolver) {
        return lifecycle(
                new PermissionAssessment(List.of(new BatchPermissionPolicy(events, deniedId))),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(events),
                new RecordingDistributedLock(events),
                record -> {
                },
                resolverType -> resolver,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("Rejects protected calls before permission checks without a selected principal")
    void rejectsProtectedCallsBeforePermissionAssessmentWhenPrincipalWasNotSelected()
            throws Exception {
        List<String> events = new ArrayList<>();
        CallInvoker executor = lifecycle(
                new PermissionAssessment(List.of(new OperationPolicy(events))),
                (key, limit) -> RateLimitDecision.allow(),
                new MemoryIdempotencyStore(events),
                new RecordingDistributedLock(events),
                record -> {
                },
                resolverType -> {
                    throw new AssertionError("未选择 Principal 时不应解析权限参数");
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
        Subject subject = new Subject(1, SubjectType.HUMAN);
        Account account = new Account(2, subject.subjectId(), new RealmKey("test"));
        Principal principal = () -> 3;
        Session session = new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(principal),
                List.of(),
                List.of(
                        new Authentication(
                                account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/TEST"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW)),
                Map.of(principal.principalId(), List.of(new Operation("platform:account:update"))),
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
        Call call = new Call("unselected", NOW);
        call.authenticate(session);
        Method method = SecuredService.class.getDeclaredMethod("update");

        assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        call,
                        SecuredService.class,
                        method,
                        new Object[0],
                        () -> {
                            throw new AssertionError("未选择 Principal 时不应执行业务调用");
                        }));
        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("Consumes Every Built In Rate Limit Dimension From An Independent Bucket")
    void consumesEveryBuiltInRateLimitDimensionFromAnIndependentBucket() throws Exception {
        AtomicInteger acquisitions = new AtomicInteger();
        List<Key> acquiredKeys = new ArrayList<>();
        AtomicReference<RateLimit> acquiredLimit = new AtomicReference<>();
        RateLimiter limiter = (key, limit) -> {
            acquisitions.incrementAndGet();
            acquiredKeys.add(key);
            acquiredLimit.set(limit);
            return RateLimitDecision.allow();
        };
        CallInvoker executor = rateLimitExecutor(limiter, resolverType -> {
            throw new AssertionError("内置限流维度不应查找自定义解析器");
        });
        Method method = RateLimitedService.class.getDeclaredMethod("allDimensions");
        HttpCall httpCall = new HttpCall("POST", "/limited", "192.0.2.25", "Client/1.0");

        String result = execute(
                executor,
                authenticatedCall(httpCall),
                RateLimitedService.class,
                method,
                new Object[0],
                () -> "done");

        assertEquals("done", result);
        assertEquals(5, acquisitions.get());
        assertEquals(
                List.of("global", "ip", "ua", "subject", "account"),
                acquiredKeys.stream().map(CallInvokerTest::rateValue).toList());
        assertFalse(acquiredKeys.toString().contains("192.0.2.25"));
        assertFalse(acquiredKeys.toString().contains("Client/1.0"));
        assertEquals(4, acquiredLimit.get().permits());
        assertEquals(Duration.ofSeconds(30), acquiredLimit.get().window());
    }

    @Test
    @DisplayName("Uses Global By Default And Supports Custom Only Rate Limits")
    void usesGlobalByDefaultAndSupportsCustomOnlyRateLimits() throws Exception {
        List<Key> acquiredKeys = new ArrayList<>();
        CallInvoker executor = rateLimitExecutor(
                (key, limit) -> {
                    acquiredKeys.add(key);
                    return RateLimitDecision.allow();
                },
                resolverType -> new RateKeyResolver());

        execute(
                executor,
                Call.system("default-and-custom", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                RateLimitedService.class,
                RateLimitedService.class.getDeclaredMethod("defaultAndCustom"),
                new Object[0],
                () -> "default-and-custom");
        execute(
                executor,
                Call.system("custom-only", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                RateLimitedService.class,
                RateLimitedService.class.getDeclaredMethod("customOnly"),
                new Object[0],
                () -> "custom-only");

        assertEquals(3, acquiredKeys.size());
        assertEquals(
                List.of("global", "custom", "custom"),
                acquiredKeys.stream().map(CallInvokerTest::rateValue).toList());
        assertFalse(acquiredKeys.toString().contains("system"));
    }

    @Test
    @DisplayName("Scopes Global Rate Limits By Concrete Method Signature")
    void scopesGlobalRateLimitsByConcreteMethodSignature() throws Exception {
        List<Key> scopes = new ArrayList<>();
        CallInvoker executor = rateLimitExecutor(
                (key, limit) -> {
                    scopes.add(key);
                    return RateLimitDecision.allow();
                },
                resolverType -> {
                    throw new AssertionError("全局限流不应查找自定义解析器");
                });

        execute(
                executor,
                Call.system("overload-empty", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                RateLimitedService.class,
                RateLimitedService.class.getDeclaredMethod("overloaded"),
                new Object[0],
                () -> "empty");
        execute(
                executor,
                Call.system("overload-text", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                RateLimitedService.class,
                RateLimitedService.class.getDeclaredMethod("overloaded", String.class),
                new Object[] {"value"},
                () -> "text");

        assertEquals(2, scopes.size());
        assertNotEquals(scopes.get(0), scopes.get(1));
    }

    @Test
    @DisplayName("Rejects Duplicate Or Empty Rate Limit Dimension Declarations")
    void rejectsDuplicateOrEmptyRateLimitDimensionDeclarations() {
        CallInvoker executor = rateLimitExecutor(
                (key, limit) -> {
                    throw new AssertionError("无效限流声明不应取得额度");
                },
                resolverType -> {
                    throw new AssertionError("无效限流声明不应查找自定义解析器");
                });

        IllegalStateException duplicate = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("duplicate-rate-limit", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("duplicateDimensions"),
                        new Object[0],
                        () -> "unexpected"));
        IllegalStateException empty = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("empty-rate-limit", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("emptyDimensions"),
                        new Object[0],
                        () -> "unexpected"));

        assertEquals("限流声明不能重复使用同一内置维度", duplicate.getMessage());
        assertEquals("限流声明至少需要一个内置维度或自定义键解析器", empty.getMessage());
    }

    @Test
    @DisplayName("Rejects Rate Limit Dimensions Without Required Call Context")
    void rejectsRateLimitDimensionsWithoutRequiredCallContext() {
        CallInvoker executor = rateLimitExecutor(
                (key, limit) -> {
                    throw new AssertionError("缺少调用上下文时不应取得额度");
                },
                resolverType -> {
                    throw new AssertionError("内置限流维度不应查找自定义解析器");
                });

        IllegalStateException missingHttp = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("missing-http", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("byIp"),
                        new Object[0],
                        () -> "unexpected"));
        IllegalStateException missingSubject = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        new Call("missing-subject", NOW),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("anonymousBySubject"),
                        new Object[0],
                        () -> "unexpected"));
        IllegalStateException missingAccount = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("missing-account", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("byAccount"),
                        new Object[0],
                        () -> "unexpected"));

        assertEquals("IP 限流只适用于 HTTP 调用", missingHttp.getMessage());
        assertEquals("SUBJECT 限流要求当前调用存在 Subject", missingSubject.getMessage());
        assertEquals("ACCOUNT 限流要求当前调用存在 Account", missingAccount.getMessage());
    }

    @Test
    @DisplayName("Rejects Blank Custom Rate Limit Keys")
    void rejectsBlankCustomRateLimitKeys() {
        CallInvoker executor = rateLimitExecutor(
                (key, limit) -> {
                    throw new AssertionError("空白自定义键不应取得额度");
                },
                resolverType -> new BlankRateKeyResolver());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("blank-rate-limit", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("customOnly"),
                        new Object[0],
                        () -> "unexpected"));

        assertTrue(failure.getMessage().startsWith("限流键解析器不能返回空白文本:"));
    }

    @Test
    @DisplayName("Reports The Longest Token Bucket Wait When A Dimension Is Denied")
    void reportsTheLongestTokenBucketWaitWhenADimensionIsDenied() {
        AtomicInteger invocations = new AtomicInteger();
        CallInvoker executor = rateLimitExecutor(
                (key, limit) -> RateLimitDecision.deny(Duration.ofMillis(1501)),
                resolverType -> {
                    throw new AssertionError("全局限流不应查找自定义解析器");
                });

        RateLimitExceededException failure = assertThrows(
                RateLimitExceededException.class,
                () -> execute(
                        executor,
                        Call.system("limited", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("overloaded"),
                        new Object[0],
                        () -> {
                            invocations.incrementAndGet();
                            return "unexpected";
                        }));

        assertEquals(CoreErrorCode.RATE_LIMITED, failure.errorCode());
        assertEquals(2, failure.retryAfterSeconds());
        assertEquals(0, invocations.get());
    }

    /**
     * 创建只执行限流阶段的测试调用执行器。
     */
    private static CallInvoker rateLimitExecutor(
            RateLimiter limiter,
            CallValueResolverLookup resolverLookup) {
        return lifecycle(
                new PermissionAssessment(List.of()),
                limiter,
                new MemoryIdempotencyStore(new ArrayList<>()),
                new RecordingDistributedLock(new ArrayList<>()),
                record -> {
                },
                resolverLookup,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3));
    }

    /**
     * 创建包含固定 Subject、Account 和可选 HTTP 快照的已认证 Call。
     */
    private static Call authenticatedCall(@Nullable HttpCall httpCall) {
        Subject subject = new Subject(101, SubjectType.HUMAN);
        Account account = new Account(102, subject.subjectId(), new RealmKey("test"));
        Principal principal = () -> 103;
        Session session = new Session(
                new SessionId("rate-limit-session"),
                subject,
                account,
                List.of(principal),
                List.of(),
                List.of(
                        new Authentication(
                                account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/TEST"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW)),
                Map.of(principal.principalId(), List.of()),
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
        DataValues.Builder inputs = DataValues.builder();
        if (httpCall != null) {
            inputs.put(HttpCall.KEY, httpCall);
        }
        Call call = new Call("all-dimensions", NOW, inputs.build(), null);
        call.authenticate(session);
        return call;
    }

    /**
     * 使用原行为测试需要的依赖组装固定 {@code CallLifecycle}。
     *
     * @param permissionAssessment 权限策略入口
     * @param rateLimiter          限流器
     * @param idempotencyStore     幂等存储
     * @param distributedLock      分布式锁
     * @param auditRecorder        审计记录器
     * @param resolverLookup       调用值解析器目录
     * @param clock                审计时钟
     * @param processingTtl        幂等处理租约
     * @param retryAfter           幂等建议重试时间
     * @return 使用真实阶段组件的 {@code CallLifecycle}
     */
    private static CallInvoker lifecycle(
            PermissionAssessment permissionAssessment,
            RateLimiter rateLimiter,
            IdempotencyStore idempotencyStore,
            DistributedLock distributedLock,
            AuditRecorder auditRecorder,
            CallValueResolverLookup resolverLookup,
            Clock clock,
            Duration processingTtl,
            Duration retryAfter) {
        return new CallInvoker(
                List.of(
                        new IdentityCallInterceptor(),
                        new PermissionCallInterceptor(permissionAssessment, resolverLookup, NO_EXPRESSIONS),
                        new RateLimitCallInterceptor(rateLimiter, resolverLookup, NO_EXPRESSIONS),
                        new IdempotencyCallInterceptor(
                                idempotencyStore,
                                resolverLookup,
                                NO_EXPRESSIONS,
                                IDEMPOTENCY_FINGERPRINTS,
                                processingTtl,
                                retryAfter,
                                RENEWAL_SCHEDULER),
                        new LockCallInterceptor(distributedLock, resolverLookup, NO_EXPRESSIONS),
                        new AuditCallInterceptor(
                                List.of(auditRecorder),
                                Audit.Mode.DIRECT,
                                NO_AUDIT_RESOLVERS,
                                NO_EXPRESSIONS,
                                clock)));
    }

    /**
     * 建立 {@code CallExecution} 并交给固定 {@code CallLifecycle}。
     *
     * @param lifecycle     待测试的 {@code CallLifecycle}
     * @param call          当前调用
     * @param declaringType 具体业务类型
     * @param method        业务方法
     * @param arguments     方法实参
     * @param invocation    实际业务调用
     * @return 业务调用或幂等重放的文本结果
     * @throws Exception {@code CallLifecycle} 抛出的失败
     */
    private static String execute(
            CallInvoker lifecycle,
            Call call,
            Class<?> declaringType,
            Method method,
            Object[] arguments,
            Callable<String> invocation) throws Exception {
        return lifecycle.execute(
                new CallExecution(call, declaringType, method, arguments, String.class),
                invocation);
    }

    /**
     * 声明内置、自定义和非法限流组合的测试服务。
     */
    private static class RateLimitedService {

        @RateLimited(dimensions = {
                RateLimit.Dim.GLOBAL,
                RateLimit.Dim.IP,
                RateLimit.Dim.UA,
                RateLimit.Dim.SUBJECT,
                RateLimit.Dim.ACCOUNT
        }, permits = 4, windowSeconds = 30)
        private String allDimensions() {
            return "unused";
        }

        @RateLimited(keyResolver = RateKeyResolver.class, permits = 2, windowSeconds = 10)
        private String defaultAndCustom() {
            return "unused";
        }

        @RateLimited(dimensions = {}, keyResolver = RateKeyResolver.class, permits = 2, windowSeconds = 10)
        private String customOnly() {
            return "unused";
        }

        @RateLimited(permits = 2, windowSeconds = 10)
        private String overloaded() {
            return "unused";
        }

        @RateLimited(permits = 2, windowSeconds = 10)
        private String overloaded(String value) {
            return value;
        }

        @RateLimited(dimensions = {RateLimit.Dim.IP, RateLimit.Dim.IP}, permits = 2, windowSeconds = 10)
        private String duplicateDimensions() {
            return "unused";
        }

        @RateLimited(dimensions = {}, permits = 2, windowSeconds = 10)
        private String emptyDimensions() {
            return "unused";
        }

        @RateLimited(dimensions = RateLimit.Dim.IP, permits = 2, windowSeconds = 10)
        private String byIp() {
            return "unused";
        }

        @AnonymousAccess
        @RateLimited(dimensions = RateLimit.Dim.SUBJECT, permits = 2, windowSeconds = 10)
        private String anonymousBySubject() {
            return "unused";
        }

        @RateLimited(dimensions = RateLimit.Dim.ACCOUNT, permits = 2, windowSeconds = 10)
        private String byAccount() {
            return "unused";
        }
    }

    private static class SecuredService {

        @RequirePermission(operation = "platform:account:update")
        @RateLimited(keyResolver = RateKeyResolver.class, permits = 10, windowSeconds = 60)
        @Idempotent(keyResolver = IdempotencyKeyResolver.class, retentionSeconds = 300)
        @Locked(keyResolver = LockKeyResolver.class, waitMillis = 1000)
        @Audit(operation = "platform/account/update")
        private String update() {
            return "unused";
        }
    }

    private static class IdempotentService {

        @Idempotent(keyResolver = IdempotencyKeyResolver.class, retentionSeconds = 300)
        private String create() {
            return "unused";
        }

        @Idempotent(keyResolver = IdempotencyKeyResolver.class, retentionSeconds = 300)
        @Locked(keyResolver = LockKeyResolver.class, waitMillis = 1000)
        private String createUnderLock() {
            return "unused";
        }

        @Idempotent(keyResolver = IdempotencyKeyResolver.class, retentionSeconds = 300)
        @Locked(keyResolver = LockKeyResolver.class, waitMillis = 1000)
        private CompletionStage<String> submitUnderLock() {
            return CompletableFuture.completedFuture("unused");
        }

        /** 声明不能作为可重放结果的异步返回值。 */
        @Idempotent(keyResolver = IdempotencyKeyResolver.class, retentionSeconds = 300)
        private CompletionStage<String> submit() {
            return CompletableFuture.completedFuture("unused");
        }
    }

    /** 声明类型级队列方式和方法级覆盖，用于验证审计默认值继承。 */
    @Audit(operation = "TEST/TYPE", mode = Audit.Mode.QUEUED)
    private static class AuditModeService {
        /** 只覆盖审计操作，继续继承类型声明的方式。 */
        @Audit(operation = "TEST/INHERITED")
        private String inherited() {
            return "unused";
        }

        /** 在队列类型中要求同步记录。 */
        @Audit(operation = "TEST/DIRECT", mode = Audit.Mode.DIRECT)
        private String direct() {
            return "unused";
        }

        /** 明确要求可靠排队。 */
        @Audit(operation = "TEST/QUEUED", mode = Audit.Mode.QUEUED)
        private String queued() {
            return "unused";
        }
    }

    private static class TargetedService {

        @Audit(operation = "platform/account/status/change")
        private String update(
                @AuditTarget(resourceType = Account.RESOURCE_TYPE_VALUE, idPrefix = "platform:") long accountId) {
            return Long.toString(accountId);
        }
    }

    private static class DeviceService {

        @RequirePermission(resolver = DeviceCollectionResolver.class, action = "read")
        private String list() {
            return "unused";
        }
    }

    /**
     * 声明一次读取一组动态资源的测试服务。
     */
    private static class BatchService {

        @RequirePermission(resolver = BatchResourcesResolver.class, action = Action.READ_VALUE)
        private String read(List<Long> ids) {
            return ids.toString();
        }
    }

    /**
     * 声明返回空元素资源集合的测试服务。
     */
    private static class NullResourceService {

        @RequirePermission(resolver = NullResourceResolver.class, action = Action.READ_VALUE)
        @SuppressWarnings("unused") // 测试通过反射读取权限声明，不直接调用该方法。
        private String read() {
            return "unused";
        }
    }

    /**
     * 声明返回错误元素资源集合的测试服务。
     */
    private static class InvalidResourceService {

        @RequirePermission(resolver = InvalidResourceResolver.class, action = Action.READ_VALUE)
        @SuppressWarnings("unused") // 测试通过反射读取权限声明，不直接调用该方法。
        private String read() {
            return "unused";
        }
    }

    @RequirePermission(operation = "duplicate:operation")
    private static class DuplicatePermissionService {

        @RequirePermission(operation = "duplicate:operation")
        private String invoke() {
            return "unused";
        }
    }

    /** 读取限流 Key 的动态维度值，验证调用者传给 IO 适配器的维度而非 App 前缀。 */
    private static String rateValue(Key key) {
        return key.segments().get(2).toLowerCase(java.util.Locale.ROOT);
    }

    private static class RateKeyResolver implements CallValueResolver<String> {

        @Override
        public @NotNull String resolve(@NotNull CallExecution execution) {
            return "system";
        }
    }

    /**
     * 返回空白自定义限流键的测试解析器。
     */
    private static class BlankRateKeyResolver implements CallValueResolver<String> {

        @Override
        public @NotNull String resolve(@NotNull CallExecution execution) {
            return "  ";
        }
    }

    private static class IdempotencyKeyResolver
            implements
                CallValueResolver<IdempotencyKey> {

        @Override
        public @NotNull IdempotencyKey resolve(@NotNull CallExecution execution) {
            return new IdempotencyKey("update-system");
        }
    }

    private static class LockKeyResolver implements CallValueResolver<LockKey> {

        @Override
        public @NotNull LockKey resolve(@NotNull CallExecution execution) {
            return new LockKey(TreeCode.of("ACCOUNT_SYSTEM"));
        }
    }

    private static class DeviceCollectionResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        public Collection<Resource> resolve(CallExecution execution) {
            return List.of(new DeviceCollection());
        }
    }

    /**
     * 把方法第一个参数中的标识转为批量测试资源。
     */
    private static class BatchResourcesResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        public Collection<Resource> resolve(CallExecution execution) {
            Object[] arguments = execution.arguments();
            if (arguments.length == 0 || !(arguments[0] instanceof List<?> ids)) {
                throw new IllegalStateException("批量资源解析要求第一个参数为标识列表");
            }
            return ids.stream()
                    .map(value -> (Resource) new ResourceReference("BATCH/ITEM", value.toString()))
                    .toList();
        }
    }

    /**
     * 返回包含空元素的集合，用于验证 Core 拒绝非法解析结果。
     */
    private static class NullResourceResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        public Collection<Resource> resolve(CallExecution execution) {
            return Collections.singletonList(null);
        }
    }

    /**
     * 通过原始集合模拟编译期约束外的错误元素。
     */
    private static class InvalidResourceResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public Collection<Resource> resolve(CallExecution execution) {
            return (Collection) List.of("not-a-resource");
        }
    }

    /**
     * 记录批量资源的判断顺序，并可拒绝指定资源。
     */
    private static class BatchPermissionPolicy
            implements
                PermissionPolicy {

        private final List<String> events;

        private final @Nullable String deniedId;

        private BatchPermissionPolicy(List<String> events, @Nullable String deniedId) {
            this.events = events;
            this.deniedId = deniedId;
        }

        @Override
        public boolean supports(Resource resource) {
            return TreeCode.of("BATCH/ITEM").equals(resource.type());
        }

        @Override
        public PermissionDecision decide(Call call, Permission permission) {
            String id = permission.resource().id();
            events.add("permission-" + id);
            return id.equals(deniedId)
                    ? PermissionDecision.deny("测试资源已拒绝")
                    : PermissionDecision.allow();
        }
    }

    private static class DevicePolicy
            implements
                PermissionPolicy {

        private final DeviceScope scope;

        private DevicePolicy(DeviceScope scope) {
            this.scope = scope;
        }

        @Override
        public boolean supports(Resource resource) {
            return resource.type().equals(TreeCode.ofSlash("TEST/DEVICE"));
        }

        @Override
        public PermissionDecision decide(Call call, Permission permission) {
            return PermissionDecision.allow(scope);
        }
    }

    private static class DeviceCollection implements Resource {

        @Override
        public @NotNull TreeCode type() {
            return TreeCode.ofSlash("TEST/DEVICE");
        }

        @Override
        public @NotNull String id() {
            return "collection";
        }
    }

    private static class DeviceScope implements ResourceScope {

        @Override
        public TreeCode resourceType() {
            return TreeCode.ofSlash("TEST/DEVICE");
        }
    }

    private static class OperationPolicy
            implements
                PermissionPolicy {

        private final List<String> events;

        private OperationPolicy(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean supports(@NotNull Resource resource) {
            return resource instanceof Operation;
        }

        @Override
        public @NotNull PermissionDecision decide(
                @NotNull Call call,
                @NotNull Permission permission) {
            events.add("permission");
            return PermissionDecision.allow();
        }
    }

    private static class MemoryIdempotencyStore implements IdempotencyStore {

        private final List<String> events;

        private final Map<IdempotencyKey, Object> results = new HashMap<>();

        private final Map<IdempotencyKey, String> tokens = new HashMap<>();

        private MemoryIdempotencyStore(List<String> events) {
            this.events = events;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @NotNull IdempotencyBeginResult<T> begin(
                @NotNull IdempotencyKey key,
                @NotNull String fingerprint,
                @NotNull Type resultType,
                @NotNull Duration processingTtl,
                @NotNull Duration retryAfter) {
            events.add("idempotency-begin");
            if (results.containsKey(key)) {
                return IdempotencyBeginResult.replay((T) results.get(key));
            }
            if (tokens.containsKey(key)) {
                return IdempotencyBeginResult.inProgress(retryAfter);
            }
            tokens.put(key, "test-token");
            return IdempotencyBeginResult.acquired("test-token");
        }

        @Override
        public <T> boolean succeed(
                @NotNull IdempotencyKey key,
                @NotNull String token,
                @NotNull String fingerprint,
                @Nullable T result,
                @NotNull Type resultType,
                @NotNull Duration retention) {
            events.add("idempotency-succeed");
            if (!token.equals(tokens.remove(key))) {
                return false;
            }
            results.put(key, result);
            return true;
        }

        @Override
        public boolean release(@NotNull IdempotencyKey key, @NotNull String token) {
            events.add("idempotency-release");
            return token.equals(tokens.remove(key));
        }

        @Override
        public boolean renew(@NotNull IdempotencyKey key, @NotNull String token, @NotNull Duration ttl) {
            return token.equals(tokens.get(key));
        }
    }

    private static class RecordingDistributedLock
            implements
                DistributedLock {

        private final List<String> events;

        private RecordingDistributedLock(List<String> events) {
            this.events = events;
        }

        @Override
        public <T> @NotNull T execute(
                @NotNull LockKey key,
                @NotNull Duration waitTime,
                @NotNull Supplier<T> task) {
            events.add("lock");
            return task.get();
        }
    }
}
