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
import com.sca4d.core.api.audit.AuditPayloadFactory;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.audit.AuditResolverLookup;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.audit.HttpCall;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.AuditCallRecorder;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.CallValueResolver;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.IdempotentCallExecutor;
import com.sca4d.core.api.call.LockedCallExecutor;
import com.sca4d.core.api.call.PermissionCallAssessor;
import com.sca4d.core.api.call.RateLimitedCallAssessor;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyBeginResult;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.concurrent.RateLimitDimension;
import com.sca4d.core.api.concurrent.RateLimitExceededException;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.permission.ResourceScope;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@SuppressWarnings({"ClassCanBeRecord", "ExtractMethodRecommender"})
class CallLifecycleTest {

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

    @Test
    @DisplayName("Executes Permission Rate Limit Idempotency Lock Invocation And Audit In Order")
    void executesPermissionRateLimitIdempotencyLockInvocationAndAuditInOrder() throws Exception {
        List<String> events = new ArrayList<>();
        MemoryIdempotencyStore idempotencyStore = new MemoryIdempotencyStore(events);
        CallLifecycle executor = lifecycle(
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
        Call call = Call.system("call-1", NOW);

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
        Call repeatedCall = Call.system("call-2", NOW);
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
        };
        CallLifecycle executor = lifecycle(
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
                        Call.system("completion-failure", NOW),
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
        CallLifecycle executor = lifecycle(
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
                Call.system("audit-failure", NOW),
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
        assertEquals(CallOutcome.Status.FAILED, outcome.status());
        assertSame(auditFailure, outcome.error());
    }

    @Test
    @DisplayName("从 AuditTarget 参数建立 ResourceReference")
    void resolvesSimpleAuditTargetParameter() throws Exception {
        AtomicReference<AuditRecord> recorded = new AtomicReference<>();
        CallLifecycle executor = lifecycle(
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
                Call.system("target-call", NOW),
                TargetedService.class,
                method,
                new Object[] {42L},
                () -> "done");

        assertEquals("done", result);
        Resource target = recorded.get().target().orElseThrow();
        assertEquals(new ResourceReference("account", "42"), target);
    }

    @Test
    @DisplayName("Records Business Resource Scope Without Exact Session Grant")
    void recordsBusinessResourceScopeWithoutExactSessionGrant() throws Exception {
        DeviceScope scope = new DeviceScope();
        CallLifecycle executor = lifecycle(
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
        Call call = Call.system("device-list", NOW);

        Permission permission = new Permission(new DeviceCollection(), Action.READ);
        CallExecution execution = new CallExecution(
                call,
                DeviceService.class,
                method,
                new Object[0],
                String.class);
        String result = executor.execute(execution, () -> {
            assertSame(scope, execution.consumeResourceScope(permission, DeviceScope.class).orElseThrow());
            return "devices";
        });

        assertEquals("devices", result);
    }

    @Test
    @DisplayName("Rejects Successful Return When Resource Scope Was Not Consumed")
    void rejectsUnconsumedResourceScope() throws Exception {
        DeviceScope scope = new DeviceScope();
        CallLifecycle lifecycle = lifecycle(
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
                        Call.system("device-list-unconsumed", NOW),
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
        CallLifecycle executor = lifecycle(
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
        Call call = Call.system("duplicate", NOW);

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
        CallLifecycle executor = batchExecutor(events, null, new BatchResourcesResolver());
        Method method = BatchService.class.getDeclaredMethod("read", List.class);

        String result = execute(
                executor,
                Call.system("batch-allowed", NOW),
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
        CallLifecycle executor = batchExecutor(events, "2", new BatchResourcesResolver());
        Method method = BatchService.class.getDeclaredMethod("read", List.class);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> execute(
                        executor,
                        Call.system("batch-denied", NOW),
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
        CallLifecycle emptyExecutor = batchExecutor(
                new ArrayList<>(),
                null,
                new BatchResourcesResolver());
        assertThrows(
                IllegalStateException.class,
                () -> execute(
                        emptyExecutor,
                        Call.system("batch-empty", NOW),
                        BatchService.class,
                        batchMethod,
                        new Object[] {List.of()},
                        () -> "unexpected"));

        for (Class<?> serviceType : List.of(NullResourceService.class, InvalidResourceService.class)) {
            Method method = serviceType.getDeclaredMethod("read");
            CallValueResolver<?> resolver = serviceType == NullResourceService.class
                    ? new NullResourceResolver()
                    : new InvalidResourceResolver();
            CallLifecycle executor = batchExecutor(new ArrayList<>(), null, resolver);
            assertThrows(
                    IllegalStateException.class,
                    () -> execute(
                            executor,
                            Call.system("batch-invalid", NOW),
                            serviceType,
                            method,
                            new Object[0],
                            () -> "unexpected"));
        }
    }

    /** 创建批量资源权限测试使用的调用执行器。 */
    private static CallLifecycle batchExecutor(
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
        CallLifecycle executor = lifecycle(
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
                                new AuthenticationMethodCode("test"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW)),
                Map.of(principal.principalId(), List.of(new Operation("platform.account.update"))),
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
        List<String> acquiredKeys = new ArrayList<>();
        AtomicReference<RateLimit> acquiredLimit = new AtomicReference<>();
        RateLimiter limiter = (key, limit) -> {
            acquisitions.incrementAndGet();
            acquiredKeys.add(key);
            acquiredLimit.set(limit);
            return RateLimitDecision.allow();
        };
        CallLifecycle executor = rateLimitExecutor(limiter, resolverType -> {
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
        assertTrue(acquiredKeys.get(0).matches("call\\.[0-9a-f]{64}:global"));
        assertTrue(acquiredKeys.get(1).matches("call\\.[0-9a-f]{64}:ip=[0-9a-f]{64}"));
        assertTrue(acquiredKeys.get(2).matches("call\\.[0-9a-f]{64}:ua=[0-9a-f]{64}"));
        assertTrue(acquiredKeys.get(3).matches("call\\.[0-9a-f]{64}:subject=[0-9a-f]{64}"));
        assertTrue(acquiredKeys.get(4).matches("call\\.[0-9a-f]{64}:account=[0-9a-f]{64}"));
        assertFalse(String.join(":", acquiredKeys).contains("192.0.2.25"));
        assertFalse(String.join(":", acquiredKeys).contains("Client/1.0"));
        assertEquals(4, acquiredLimit.get().permits());
        assertEquals(Duration.ofSeconds(30), acquiredLimit.get().window());
    }

    @Test
    @DisplayName("Uses Global By Default And Supports Custom Only Rate Limits")
    void usesGlobalByDefaultAndSupportsCustomOnlyRateLimits() throws Exception {
        List<String> acquiredKeys = new ArrayList<>();
        CallLifecycle executor = rateLimitExecutor(
                (key, limit) -> {
                    acquiredKeys.add(key);
                    return RateLimitDecision.allow();
                },
                resolverType -> new RateKeyResolver());

        execute(
                executor,
                Call.system("default-and-custom", NOW),
                RateLimitedService.class,
                RateLimitedService.class.getDeclaredMethod("defaultAndCustom"),
                new Object[0],
                () -> "default-and-custom");
        execute(
                executor,
                Call.system("custom-only", NOW),
                RateLimitedService.class,
                RateLimitedService.class.getDeclaredMethod("customOnly"),
                new Object[0],
                () -> "custom-only");

        assertEquals(3, acquiredKeys.size());
        assertTrue(acquiredKeys.get(0).endsWith(":global"));
        assertTrue(acquiredKeys.get(1).matches("call\\.[0-9a-f]{64}:custom=[0-9a-f]{64}"));
        assertTrue(acquiredKeys.get(2).matches("call\\.[0-9a-f]{64}:custom=[0-9a-f]{64}"));
        assertFalse(acquiredKeys.toString().contains("system"));
    }

    @Test
    @DisplayName("Scopes Global Rate Limits By Concrete Method Signature")
    void scopesGlobalRateLimitsByConcreteMethodSignature() throws Exception {
        List<String> scopes = new ArrayList<>();
        CallLifecycle executor = rateLimitExecutor(
                (key, limit) -> {
                    scopes.add(key);
                    return RateLimitDecision.allow();
                },
                resolverType -> {
                    throw new AssertionError("全局限流不应查找自定义解析器");
                });

        execute(
                executor,
                Call.system("overload-empty", NOW),
                RateLimitedService.class,
                RateLimitedService.class.getDeclaredMethod("overloaded"),
                new Object[0],
                () -> "empty");
        execute(
                executor,
                Call.system("overload-text", NOW),
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
        CallLifecycle executor = rateLimitExecutor(
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
                        Call.system("duplicate-rate-limit", NOW),
                        RateLimitedService.class,
                        RateLimitedService.class.getDeclaredMethod("duplicateDimensions"),
                        new Object[0],
                        () -> "unexpected"));
        IllegalStateException empty = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("empty-rate-limit", NOW),
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
        CallLifecycle executor = rateLimitExecutor(
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
                        Call.system("missing-http", NOW),
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
                        Call.system("missing-account", NOW),
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
        CallLifecycle executor = rateLimitExecutor(
                (key, limit) -> {
                    throw new AssertionError("空白自定义键不应取得额度");
                },
                resolverType -> new BlankRateKeyResolver());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> execute(
                        executor,
                        Call.system("blank-rate-limit", NOW),
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
        CallLifecycle executor = rateLimitExecutor(
                (key, limit) -> RateLimitDecision.deny(Duration.ofMillis(1501)),
                resolverType -> {
                    throw new AssertionError("全局限流不应查找自定义解析器");
                });

        RateLimitExceededException failure = assertThrows(
                RateLimitExceededException.class,
                () -> execute(
                        executor,
                        Call.system("limited", NOW),
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

    /** 创建只执行限流阶段的测试调用执行器。 */
    private static CallLifecycle rateLimitExecutor(
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

    /** 创建包含固定 Subject、Account 和可选 HTTP 快照的已认证 Call。 */
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
                                new AuthenticationMethodCode("test"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW)),
                Map.of(principal.principalId(), List.of()),
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
        Call call = new Call("all-dimensions", NOW, httpCall);
        call.authenticate(session);
        return call;
    }

    /**
     * 使用原行为测试需要的依赖组装固定 {@code CallLifecycle}。
     *
     * @param permissionAssessment 权限策略入口
     * @param rateLimiter 限流器
     * @param idempotencyStore 幂等存储
     * @param distributedLock 分布式锁
     * @param auditRecorder 审计记录器
     * @param resolverLookup 调用值解析器目录
     * @param clock 审计时钟
     * @param processingTtl 幂等处理租约
     * @param retryAfter 幂等建议重试时间
     * @return 使用真实阶段组件的 {@code CallLifecycle}
     */
    private static CallLifecycle lifecycle(
            PermissionAssessment permissionAssessment,
            RateLimiter rateLimiter,
            IdempotencyStore idempotencyStore,
            DistributedLock distributedLock,
            AuditRecorder auditRecorder,
            CallValueResolverLookup resolverLookup,
            Clock clock,
            Duration processingTtl,
            Duration retryAfter) {
        return new CallLifecycle(
                new PermissionCallAssessor(permissionAssessment, resolverLookup),
                new RateLimitedCallAssessor(rateLimiter, resolverLookup),
                new IdempotentCallExecutor(
                        idempotencyStore,
                        resolverLookup,
                        IDEMPOTENCY_FINGERPRINTS,
                        processingTtl,
                        retryAfter),
                new LockedCallExecutor(distributedLock, resolverLookup),
                new AuditCallRecorder(auditRecorder, NO_AUDIT_RESOLVERS, clock));
    }

    /**
     * 建立 {@code CallExecution} 并交给固定 {@code CallLifecycle}。
     *
     * @param lifecycle 待测试的 {@code CallLifecycle}
     * @param call 当前调用
     * @param declaringType 具体业务类型
     * @param method 业务方法
     * @param arguments 方法实参
     * @param invocation 实际业务调用
     * @return 业务调用或幂等重放的文本结果
     * @throws Exception {@code CallLifecycle} 抛出的失败
     */
    private static String execute(
            CallLifecycle lifecycle,
            Call call,
            Class<?> declaringType,
            Method method,
            Object[] arguments,
            Callable<String> invocation) throws Exception {
        return lifecycle.execute(
                new CallExecution(call, declaringType, method, arguments, String.class),
                invocation);
    }

    /** 声明内置、自定义和非法限流组合的测试服务。 */
    private static final class RateLimitedService {

        @RateLimited(dimensions = {
                RateLimitDimension.GLOBAL,
                RateLimitDimension.IP,
                RateLimitDimension.UA,
                RateLimitDimension.SUBJECT,
                RateLimitDimension.ACCOUNT
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

        @RateLimited(dimensions = {RateLimitDimension.IP, RateLimitDimension.IP}, permits = 2, windowSeconds = 10)
        private String duplicateDimensions() {
            return "unused";
        }

        @RateLimited(dimensions = {}, permits = 2, windowSeconds = 10)
        private String emptyDimensions() {
            return "unused";
        }

        @RateLimited(dimensions = RateLimitDimension.IP, permits = 2, windowSeconds = 10)
        private String byIp() {
            return "unused";
        }

        @AnonymousAccess
        @RateLimited(dimensions = RateLimitDimension.SUBJECT, permits = 2, windowSeconds = 10)
        private String anonymousBySubject() {
            return "unused";
        }

        @RateLimited(dimensions = RateLimitDimension.ACCOUNT, permits = 2, windowSeconds = 10)
        private String byAccount() {
            return "unused";
        }
    }

    private static final class SecuredService {

        @RequirePermission(operation = "platform.account.update")
        @RateLimited(keyResolver = RateKeyResolver.class, permits = 10, windowSeconds = 60)
        @Idempotent(keyResolver = IdempotencyKeyResolver.class, retentionSeconds = 300)
        @Locked(keyResolver = LockKeyResolver.class, waitMillis = 1000)
        @Audit(operation = "platform/account/update")
        private String update() {
            return "unused";
        }
    }

    private static final class IdempotentService {

        @Idempotent(keyResolver = IdempotencyKeyResolver.class, retentionSeconds = 300)
        private String create() {
            return "unused";
        }
    }

    private static final class TargetedService {

        @Audit(operation = "platform/account/status/change")
        private String update(@AuditTarget(resourceType = Account.RESOURCE_TYPE) long accountId) {
            return Long.toString(accountId);
        }
    }

    private static final class DeviceService {

        @RequirePermission(resourceResolver = DeviceCollectionResolver.class, action = "read")
        private String list() {
            return "unused";
        }
    }

    /** 声明一次读取一组动态资源的测试服务。 */
    private static final class BatchService {

        @RequirePermission(resourceResolver = BatchResourcesResolver.class, action = Action.READ_VALUE)
        private String read(List<Long> ids) {
            return ids.toString();
        }
    }

    /** 声明返回空元素资源集合的测试服务。 */
    private static final class NullResourceService {

        @RequirePermission(resourceResolver = NullResourceResolver.class, action = Action.READ_VALUE)
        @SuppressWarnings("unused") // 测试通过反射读取权限声明，不直接调用该方法。
        private String read() {
            return "unused";
        }
    }

    /** 声明返回错误元素资源集合的测试服务。 */
    private static final class InvalidResourceService {

        @RequirePermission(resourceResolver = InvalidResourceResolver.class, action = Action.READ_VALUE)
        @SuppressWarnings("unused") // 测试通过反射读取权限声明，不直接调用该方法。
        private String read() {
            return "unused";
        }
    }

    @RequirePermission(operation = "duplicate.operation")
    private static final class DuplicatePermissionService {

        @RequirePermission(operation = "duplicate.operation")
        private String invoke() {
            return "unused";
        }
    }

    private static final class RateKeyResolver implements CallValueResolver<String> {

        @Override
        public @NotNull String resolve(@NotNull CallExecution execution) {
            return "system";
        }
    }

    /** 返回空白自定义限流键的测试解析器。 */
    private static final class BlankRateKeyResolver implements CallValueResolver<String> {

        @Override
        public @NotNull String resolve(@NotNull CallExecution execution) {
            return "  ";
        }
    }

    private static final class IdempotencyKeyResolver
            implements
                CallValueResolver<IdempotencyKey> {

        @Override
        public @NotNull IdempotencyKey resolve(@NotNull CallExecution execution) {
            return new IdempotencyKey("update-system");
        }
    }

    private static final class LockKeyResolver implements CallValueResolver<LockKey> {

        @Override
        public @NotNull LockKey resolve(@NotNull CallExecution execution) {
            return new LockKey("account-system");
        }
    }

    private static final class DeviceCollectionResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        public Collection<Resource> resolve(CallExecution execution) {
            return List.of(new DeviceCollection());
        }
    }

    /** 把方法第一个参数中的标识转为批量测试资源。 */
    private static final class BatchResourcesResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        public Collection<Resource> resolve(CallExecution execution) {
            Object[] arguments = execution.arguments();
            if (arguments.length == 0 || !(arguments[0] instanceof List<?> ids)) {
                throw new IllegalStateException("批量资源解析要求第一个参数为标识列表");
            }
            return ids.stream()
                    .map(value -> (Resource) new ResourceReference("batch-item", value.toString()))
                    .toList();
        }
    }

    /** 返回包含空元素的集合，用于验证 Core 拒绝非法解析结果。 */
    private static final class NullResourceResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        public Collection<Resource> resolve(CallExecution execution) {
            return Collections.singletonList(null);
        }
    }

    /** 通过原始集合模拟编译期约束外的错误元素。 */
    private static final class InvalidResourceResolver
            implements
                CallValueResolver<Collection<Resource>> {

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public Collection<Resource> resolve(CallExecution execution) {
            return (Collection) List.of("not-a-resource");
        }
    }

    /** 记录批量资源的判断顺序，并可拒绝指定资源。 */
    private static final class BatchPermissionPolicy
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
            return "batch-item".equals(resource.type());
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

    private static final class DevicePolicy
            implements
                PermissionPolicy {

        private final DeviceScope scope;

        private DevicePolicy(DeviceScope scope) {
            this.scope = scope;
        }

        @Override
        public boolean supports(Resource resource) {
            return resource.type().equals("device");
        }

        @Override
        public PermissionDecision decide(Call call, Permission permission) {
            return PermissionDecision.allow(scope);
        }
    }

    private static final class DeviceCollection implements Resource {

        @Override
        public @NotNull String type() {
            return "device";
        }

        @Override
        public @NotNull String id() {
            return "collection";
        }
    }

    private static final class DeviceScope implements ResourceScope {

        @Override
        public String resourceType() {
            return "device";
        }
    }

    private static final class OperationPolicy
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

    private static final class MemoryIdempotencyStore implements IdempotencyStore {

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
    }

    private static final class RecordingDistributedLock
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
