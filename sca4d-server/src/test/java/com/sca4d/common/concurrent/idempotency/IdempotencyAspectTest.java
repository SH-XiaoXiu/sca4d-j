package com.sca4d.common.concurrent.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.WebConstants;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;

/** 验证幂等切面对首次请求、处理中冲突和成功响应重放的处理。 */
class IdempotencyAspectTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Sca4djConcurrentProperties properties = properties();

    @Test
    void shouldProceedAndStoreSucceededResponseWhenAcquired() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest("ORDER 1", "demo", null));
        when(joinPoint.proceed()).thenReturn("created");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("created");
        assertThat(store.key).startsWith("idem:");
        assertThat(store.fingerprint).isNotBlank();
        assertThat(store.succeeded).isTrue();
        assertThat(store.succeededResponseJson).isEqualTo("\"created\"");
        assertThat(store.succeededResponseType).isEqualTo("java.lang.String");
        assertThat(store.succeededTtl).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void shouldUseDeclaredSpelKeyBeforeRequestKey() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint(
                "createWithDeclaredKey", new DemoService(), new DemoRequest("request-key", "demo", "intent-key"));
        when(joinPoint.proceed()).thenReturn("created");

        aspect.around(joinPoint);

        FakeIdempotencyStore otherStore = new FakeIdempotencyStore();
        ProceedingJoinPoint otherJoinPoint = joinPoint(
                "createWithDeclaredKey", new DemoService(), new DemoRequest("request-key", "demo", "other-intent"));
        when(otherJoinPoint.proceed()).thenReturn("created");
        aspect(otherStore).around(otherJoinPoint);

        assertThat(store.key).startsWith("idem:").isNotEqualTo(otherStore.key);
    }

    @Test
    void shouldReplayStoredSuccessfulResponse() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        store.beginResult = IdempotencyBeginResult.replay(
                IdempotencyRecord.succeeded("same", objectMapper.writeValueAsString("created"), "java.lang.String"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        IdempotencyAspect aspect = aspect(store, responseProvider(response));
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest("retry", "demo", null));

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("created");
        assertThat(response.getHeader(WebConstants.IDEMPOTENCY_REPLAYED_HEADER)).isEqualTo("true");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldRejectReplayCreatedForDifferentReturnType() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        store.beginResult = IdempotencyBeginResult.replay(
                IdempotencyRecord.succeeded("same", objectMapper.writeValueAsString("created"), "java.lang.Long"));
        ProceedingJoinPoint joinPoint = joinPoint(
                "create", new DemoService(), new DemoRequest("retry", "demo", null));

        assertThatThrownBy(() -> aspect(store).around(joinPoint))
                .isInstanceOf(InternalServiceException.class)
                .hasMessage("幂等响应类型与当前方法契约不一致");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldReplayApiResponseWithStatusHeadersAndTypedBody() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        IdempotencyAspect aspect = aspect(store);
        DemoRequest request = new DemoRequest("response-key", "demo", null);
        ProceedingJoinPoint first = joinPoint("createResource", new DemoService(), request);
        ApiResponse<String> created = ApiResponse.created("created", URI.create("/resources/42"), 0L);
        when(first.proceed()).thenReturn(created);

        Object firstResult = aspect.around(first);

        assertThat(firstResult).isSameAs(created);
        store.beginResult = IdempotencyBeginResult.replay(IdempotencyRecord.succeeded(
                store.fingerprint, store.succeededResponseJson, store.succeededResponseType));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ProceedingJoinPoint replay = joinPoint("createResource", new DemoService(), request);

        Object replayed = aspect(store, responseProvider(servletResponse)).around(replay);

        assertThat(replayed).isInstanceOfSatisfying(ApiResponse.class, response -> {
            assertThat(response.responseStatus()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.responseHeaders().getLocation()).isEqualTo(URI.create("/resources/42"));
            assertThat(response.responseHeaders().getETag()).isEqualTo("\"0\"");
            assertThat(response.getData()).isEqualTo("created");
        });
        assertThat(servletResponse.getHeader(WebConstants.IDEMPOTENCY_REPLAYED_HEADER)).isEqualTo("true");
        verify(replay, never()).proceed();
    }

    @Test
    void shouldRejectDuplicateRequestWhileOriginalIsProcessing() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        store.beginResult = IdempotencyBeginResult.inProgress(Duration.ofSeconds(7));
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest("retry", "demo", null));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(IdempotencyInProgressException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(7L);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldRejectSameKeyWithDifferentFingerprint() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        store.beginResult = IdempotencyBeginResult.conflict();
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest("same-key", "demo", null));

        assertThatThrownBy(() -> aspect.around(joinPoint)).isInstanceOf(IdempotencyKeyConflictException.class);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldReleaseProcessingRecordWhenBusinessExecutionFails() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest("fail", "demo", null));
        RuntimeException failure = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.around(joinPoint)).isSameAs(failure);
        assertThat(store.released).isTrue();
        assertThat(store.releasedToken).isEqualTo("token-1");
        assertThat(store.succeeded).isFalse();
    }

    @Test
    void shouldReturnBusinessSuccessWithoutReleasingKeyWhenSuccessRecordFails() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        store.succeedFailure = new IllegalStateException("redis unavailable");
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest("key", "demo", null));
        when(joinPoint.proceed()).thenReturn("created");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("created");
        assertThat(store.released).isFalse();
    }

    @Test
    void shouldPreserveBusinessFailureWhenProcessingReleaseFails() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        store.releaseFailure = new IllegalStateException("redis unavailable");
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest("key", "demo", null));
        IllegalArgumentException businessFailure = new IllegalArgumentException("business failed");
        when(joinPoint.proceed()).thenThrow(businessFailure);

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isSameAs(businessFailure)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .singleElement()
                        .isSameAs(store.releaseFailure));
    }

    @Test
    void shouldRequireKeyWhenNoDeclaredOrRequestKeyExists() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        IdempotencyAspect aspect = aspect(store);
        ProceedingJoinPoint joinPoint = joinPoint("create", new DemoService(), new DemoRequest(null, "demo", null));

        assertThatThrownBy(() -> aspect.around(joinPoint)).isInstanceOf(IdempotencyKeyRequiredException.class);
        assertThat(store.beginCalled).isFalse();
    }

    @Test
    void shouldKeepStoreKeyStableAcrossJavaMethodRename() throws Throwable {
        FakeIdempotencyStore originalStore = new FakeIdempotencyStore();
        ProceedingJoinPoint original = joinPoint("create", new DemoService(), new DemoRequest("same-key", "demo", null));
        when(original.proceed()).thenReturn("created");
        aspect(originalStore).around(original);

        FakeIdempotencyStore renamedStore = new FakeIdempotencyStore();
        ProceedingJoinPoint renamed = joinPoint("createAlias", new DemoService(), new DemoRequest("same-key", "demo", null));
        when(renamed.proceed()).thenReturn("created");
        aspect(renamedStore).around(renamed);

        assertThat(renamedStore.key).isEqualTo(originalStore.key);
        assertThat(renamedStore.fingerprint).isEqualTo(originalStore.fingerprint);
    }

    @Test
    void shouldTreatIdempotencyKeyAsCaseSensitiveOpaqueValue() throws Throwable {
        FakeIdempotencyStore upperStore = new FakeIdempotencyStore();
        ProceedingJoinPoint upper = joinPoint("create", new DemoService(), new DemoRequest("Key", "demo", null));
        when(upper.proceed()).thenReturn("created");
        aspect(upperStore).around(upper);

        FakeIdempotencyStore lowerStore = new FakeIdempotencyStore();
        ProceedingJoinPoint lower = joinPoint("create", new DemoService(), new DemoRequest("key", "demo", null));
        when(lower.proceed()).thenReturn("created");
        aspect(lowerStore).around(lower);

        assertThat(upperStore.key).isNotEqualTo(lowerStore.key);
    }

    @Test
    void shouldRejectBlankStableOperationBeforeBusinessExecution() throws Throwable {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        ProceedingJoinPoint joinPoint = joinPoint("invalidOperation", new DemoService(), new DemoRequest("key", "demo", null));

        assertThatThrownBy(() -> aspect(store).around(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("@Idempotent operation 必须是有效文本");
        verify(joinPoint, never()).proceed();
        assertThat(store.beginCalled).isFalse();
    }

    @Test
    void shouldRejectInvalidIdempotencyConfiguration() {
        Sca4djConcurrentProperties.Idempotency configuration = new Sca4djConcurrentProperties.Idempotency();

        assertThatThrownBy(() -> configuration.setKeyPrefix(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("幂等 key 前缀不能为空");
        assertThatThrownBy(() -> configuration.setProcessingTtl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("幂等处理租约必须大于 0");
        assertThatThrownBy(() -> configuration.setSuccessTtl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("幂等成功记录 TTL 必须大于 0");
        assertThatThrownBy(() -> configuration.setRetryAfter(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("幂等重试等待时间必须大于 0");
    }

    /**
     * 创建没有当前 HTTP 响应对象的幂等切面夹具。
     *
     * @param store 当前场景的幂等存储
     * @return 使用共用测试配置的幂等切面
     */
    private @NotNull IdempotencyAspect aspect(@NotNull FakeIdempotencyStore store) {
        return aspect(store, responseProvider(null));
    }

    /**
     * 创建使用指定存储和 HTTP 响应提供者的幂等切面夹具。
     *
     * @param store 当前场景的幂等存储
     * @param responseProvider 可选 HTTP 响应对象提供者
     * @return 使用真实 SpEL 解析器和 ObjectMapper 的幂等切面
     */
    private @NotNull IdempotencyAspect aspect(
            @NotNull FakeIdempotencyStore store,
            @NotNull ObjectProvider<HttpServletResponse> responseProvider) {
        return new IdempotencyAspect(store, objectMapper, new SpelKeyEvaluator(), properties, responseProvider);
    }

    /**
     * 创建固定返回指定响应对象的 ObjectProvider 测试替身。
     *
     * @param response 当前 HTTP 响应；无 Web 响应场景为 {@code null}
     * @return 不访问 Spring 容器的响应提供者
     */
    private @NotNull ObjectProvider<HttpServletResponse> responseProvider(@Nullable HttpServletResponse response) {
        return new ObjectProvider<>() {

            @Override
            public @Nullable HttpServletResponse getObject(@NotNull Object... args) {
                return response;
            }

            @Override
            public @Nullable HttpServletResponse getIfAvailable() {
                return response;
            }
        };
    }

    /**
     * 创建固定前缀、处理租约、成功保留期和重试等待时间的并发配置。
     *
     * @return 仅覆盖幂等测试所需字段的配置
     */
    private @NotNull Sca4djConcurrentProperties properties() {
        Sca4djConcurrentProperties concurrentProperties = new Sca4djConcurrentProperties();
        concurrentProperties.getIdempotency().setKeyPrefix("idem");
        concurrentProperties.getIdempotency().setProcessingTtl(Duration.ofMinutes(2));
        concurrentProperties.getIdempotency().setSuccessTtl(Duration.ofHours(2));
        concurrentProperties.getIdempotency().setRetryAfter(Duration.ofSeconds(5));
        return concurrentProperties;
    }

    /**
     * 创建绑定单个 DemoRequest 参数和真实反射方法的 JoinPoint mock。
     *
     * @param methodName DemoService 方法名
     * @param target 切面目标对象
     * @param arg 唯一方法参数
     * @return 可供幂等切面读取签名、目标和参数的 JoinPoint
     * @throws NoSuchMethodException 方法契约发生漂移时抛出
     */
    private @NotNull ProceedingJoinPoint joinPoint(
            @NotNull String methodName, @NotNull Object target, @NotNull Object arg)
            throws NoSuchMethodException {
        Method method = target.getClass().getDeclaredMethod(methodName, DemoRequest.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[] {"request"});

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[] {arg});
        return joinPoint;
    }

    /** 声明多种幂等注解配置并记录实际执行次数的服务。 */
    private static class DemoService {

        /** 使用请求默认幂等键返回固定成功响应。 */
        @Idempotent(operation = "demo.create")
        @SuppressWarnings("unused")
        public @NotNull String create(@NotNull DemoRequest request) {
            return "created";
        }

        /** 使用相同 operation 验证不同方法仍可重放同一响应。 */
        @Idempotent(operation = "demo.create")
        @SuppressWarnings("unused")
        public @NotNull String createAlias(@NotNull DemoRequest request) {
            return "created";
        }

        /** 返回携带完整 HTTP 契约的资源创建结果。 */
        @Idempotent(operation = "demo.create-resource")
        @SuppressWarnings("unused")
        public @NotNull ApiResponse<String> createResource(@NotNull DemoRequest request) {
            return ApiResponse.created("created", URI.create("/resources/42"), 0L);
        }

        /** 使用 SpEL 声明字段作为幂等键。 */
        @Idempotent(operation = "demo.create-declared", key = "#request.customKey")
        @SuppressWarnings("unused")
        public @NotNull String createWithDeclaredKey(@NotNull DemoRequest request) {
            return "created";
        }

        /** 使用空白 operation 触发注解配置校验。 */
        @Idempotent(operation = " ")
        @SuppressWarnings("unused")
        public @NotNull String invalidOperation(@NotNull DemoRequest request) {
            return "created";
        }
    }

    /** 幂等切面测试使用的请求对象。 */
    private static final class DemoRequest implements IdempotentRequest {

        private final String idempotencyKey;
        private final String name;
        private final String customKey;

        /**
         * 创建同时覆盖请求幂等键、业务指纹字段和声明式 key 的测试请求。
         *
         * @param idempotencyKey 请求契约提供的可选幂等键
         * @param name 参与请求指纹的业务字段
         * @param customKey SpEL 声明读取的可选业务意图键
         */
        private DemoRequest(
                @Nullable String idempotencyKey, @NotNull String name, @Nullable String customKey) {
            this.idempotencyKey = idempotencyKey;
            this.name = name;
            this.customKey = customKey;
        }

        @Override
        public @Nullable String getIdempotencyKey() {
            return idempotencyKey;
        }

        public @Nullable String getCustomKey() {
            return customKey;
        }

        @SuppressWarnings("unused")
        public @NotNull String getName() {
            return name;
        }
    }

    /** 在内存中记录幂等状态和响应，供切面断言读写结果。 */
    private static class FakeIdempotencyStore implements IdempotencyStore {

        private IdempotencyBeginResult beginResult = IdempotencyBeginResult.acquired("token-1");
        private boolean beginCalled;
        private String key;
        private String fingerprint;
        private boolean succeeded;
        private String succeededResponseJson;
        private String succeededResponseType;
        private Duration succeededTtl;
        private boolean released;
        private String releasedToken;
        private RuntimeException succeedFailure;
        private RuntimeException releaseFailure;

        @Override
        public @NotNull IdempotencyBeginResult begin(
                @NotNull String key,
                @NotNull String fingerprint,
                @NotNull Duration processingTtl,
                @NotNull Duration retryAfter) {
            this.beginCalled = true;
            this.key = key;
            this.fingerprint = fingerprint;
            return beginResult;
        }

        @Override
        public boolean succeed(
                @NotNull String key,
                @NotNull String token,
                @NotNull String fingerprint,
                @Nullable String responseJson,
                @NotNull String responseType,
                @NotNull Duration successTtl) {
            if (succeedFailure != null) {
                throw succeedFailure;
            }
            this.succeeded = true;
            this.succeededResponseJson = responseJson;
            this.succeededResponseType = responseType;
            this.succeededTtl = successTtl;
            return true;
        }

        @Override
        public boolean release(@NotNull String key, @NotNull String token) {
            if (releaseFailure != null) {
                throw releaseFailure;
            }
            this.released = true;
            this.releasedToken = token;
            return true;
        }
    }
}
