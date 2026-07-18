package com.sca4d.common.concurrent.idempotency;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.WebConstants;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 幂等保护切面。
 */
@Aspect
@Order(150)
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;
    private final SpelKeyEvaluator keyEvaluator;
    private final Sca4djConcurrentProperties properties;
    private final ObjectProvider<HttpServletResponse> responseProvider;

    /**
     * 创建使用 Redis 记录、共用 JSON 契约和固定操作编码的幂等切面。
     *
     * @param store 集群共享幂等记录存储
     * @param objectMapper 请求指纹和响应重放使用的全局 JSON 映射器
     * @param keyEvaluator 显式幂等 key 使用的共用 SpEL 解析器
     * @param properties 处理中租约、成功保留期和重试建议配置
     * @param responseProvider HTTP 重放标记响应头的可选响应来源
     */
    public IdempotencyAspect(
            @NotNull IdempotencyStore store,
            @NotNull ObjectMapper objectMapper,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull Sca4djConcurrentProperties properties,
            @NotNull ObjectProvider<HttpServletResponse> responseProvider) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.keyEvaluator = keyEvaluator;
        this.properties = properties;
        this.responseProvider = responseProvider;
    }

    /**
     * 在声明了固定幂等操作编码的方法外包裹集群幂等逻辑。
     *
     * @param joinPoint 提供方法、参数和业务执行入口的 AOP 调用点
     * @return 首次执行结果或同一操作、key 和请求指纹对应的历史成功结果
     * @throws Throwable 业务执行失败、缺少 key、请求冲突或记录无法读取时抛出
     */
    @Around("@annotation(com.sca4d.common.concurrent.idempotency.Idempotent)")
    public Object around(@NotNull ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (target != null) {
            method = AopUtils.getMostSpecificMethod(method, target.getClass());
        }
        Idempotent annotation = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        if (annotation == null || !properties.getIdempotency().isEnabled()) {
            return joinPoint.proceed();
        }
        if (!StringUtils.hasText(annotation.operation())) {
            throw new IllegalStateException("@Idempotent operation 必须是有效文本");
        }

        IdempotentRequest request = findRequest(joinPoint.getArgs());
        String rawKey = rawKey(annotation, request, joinPoint);
        String operation = annotation.operation().trim();
        String storeKey = storeKey(rawKey, operation);
        String fingerprint = fingerprint(operation, request == null ? joinPoint.getArgs() : request);

        IdempotencyBeginResult beginResult = store.begin(
                storeKey,
                fingerprint,
                properties.getIdempotency().getProcessingTtl(),
                properties.getIdempotency().getRetryAfter());
        log.trace(
                "幂等请求状态已确定 | operation={} status={} storeKey={}",
                operation,
                beginResult.getStatus(),
                storeKey);
        return switch (beginResult.getStatus()) {
            case ACQUIRED -> proceedAndStore(
                    joinPoint,
                    method,
                    operation,
                    storeKey,
                    fingerprint,
                    Objects.requireNonNull(beginResult.getToken(), "幂等 ACQUIRED 结果缺少所有权 token"));
            case REPLAY -> replay(method, beginResult.getRecord());
            case IN_PROGRESS -> throw new IdempotencyInProgressException(beginResult.getRetryAfter());
            case CONFLICT -> throw new IdempotencyKeyConflictException();
        };
    }

    /**
     * 执行首次获得所有权的业务调用，并按最终结果推进或释放 Redis 记录。
     *
     * <p>业务失败时释放仅属于当前 token 的处理中记录。业务成功后落档失败不能改变业务结果，也不能主动释放 key；处理中记录保留到租约过期，缩小立即重复执行窗口。
     *
     * @param joinPoint 首次执行业务方法的 AOP 调用点
     * @param method 用于还原重放返回类型的方法
     * @param operation 固定业务操作编码
     * @param storeKey 集群共享 Redis key
     * @param fingerprint 当前业务请求指纹
     * @param token 当前节点本次处理的所有权 token
     * @return 原业务方法返回值
     * @throws Throwable 原业务方法失败时抛出，并附加可能的 Redis 释放异常
     */
    private Object proceedAndStore(
            @NotNull ProceedingJoinPoint joinPoint,
            @NotNull Method method,
            @NotNull String operation,
            @NotNull String storeKey,
            @NotNull String fingerprint,
            @NotNull String token)
            throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            try {
                if (!store.release(storeKey, token)) {
                    log.warn("幂等处理中记录未由当前请求释放，等待自动过期 | operation={} storeKey={}", operation, storeKey);
                }
            } catch (RuntimeException releaseException) {
                throwable.addSuppressed(releaseException);
                log.error("幂等处理中记录释放失败，等待自动过期 | operation={} storeKey={}",
                        operation, storeKey, releaseException);
            }
            throw throwable;
        }
        try {
            String responseJson = serializeResult(result);
            boolean stored = store.succeed(
                    storeKey,
                    token,
                    fingerprint,
                    responseJson,
                    method.getGenericReturnType().getTypeName(),
                    properties.getIdempotency().getSuccessTtl());
            if (!stored) {
                log.warn("幂等结果未由当前请求保存，处理中记录将自动过期 | operation={} storeKey={}", operation, storeKey);
            } else {
                log.trace("幂等结果已保存 | operation={} storeKey={}", operation, storeKey);
            }
        } catch (RuntimeException exception) {
            // 业务调用可能已经提交。保留处理中键直到租约过期，避免立即放开重复执行窗口；客户端仍收到真实成功结果。
            log.error("幂等结果保存失败，保留处理中记录等待自动过期 | operation={} storeKey={}",
                    operation, storeKey, exception);
        }
        return result;
    }

    /**
     * 按当前方法泛型返回类型反序列化历史成功响应。
     *
     * @param method 当前业务方法，用于恢复声明的返回类型
     * @param record Redis 返回的成功记录
     * @return 与首次调用相同类型的业务响应
     * @throws InternalServiceException 记录缺失或历史 JSON 无法按当前契约读取时抛出
     */
    private Object replay(@NotNull Method method, @Nullable IdempotencyRecord record) {
        if (record == null) {
            throw new InternalServiceException("幂等重放记录为空");
        }
        log.trace("幂等请求复用历史结果 | responseType={}", record.getResponseType());
        HttpServletResponse response = responseProvider.getIfAvailable();
        if (response != null && !response.isCommitted()) {
            response.setHeader(WebConstants.IDEMPOTENCY_REPLAYED_HEADER, "true");
        }
        String currentResponseType = method.getGenericReturnType().getTypeName();
        if (!Objects.equals(record.getResponseType(), currentResponseType)) {
            throw new InternalServiceException("幂等响应类型与当前方法契约不一致");
        }
        String responseJson = record.getResponseJson();
        if (responseJson == null) {
            return null;
        }
        try {
            JavaType returnType = objectMapper.getTypeFactory().constructType(method.getGenericReturnType());
            if (ApiResponse.class.isAssignableFrom(returnType.getRawClass())) {
                StoredHttpResponse stored = objectMapper.readValue(responseJson, StoredHttpResponse.class);
                ApiResponse<?> body = objectMapper.convertValue(stored.getBody(), returnType);
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(stored.getHeaders());
                body.restoreResponse(HttpStatus.resolve(stored.getStatus()), headers);
                return body;
            }
            if (ResponseEntity.class.isAssignableFrom(returnType.getRawClass())) {
                StoredHttpResponse stored = objectMapper.readValue(responseJson, StoredHttpResponse.class);
                JavaType bodyType = returnType.containedType(0);
                Object body = stored.getBody() == null || stored.getBody().isNull()
                        ? null
                        : objectMapper.convertValue(stored.getBody(), bodyType);
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(stored.getHeaders());
                return new ResponseEntity<>(body, headers, HttpStatusCode.valueOf(stored.getStatus()));
            }
            return objectMapper.readValue(responseJson, returnType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InternalServiceException("幂等响应重放失败", exception);
        }
    }

    /**
     * 按注解 SpEL 优先、请求对象其次的顺序解析客户端提交意图 key。
     *
     * @param annotation 当前方法的幂等声明
     * @param request 参数中实现 IdempotentRequest 的请求对象，可能不存在
     * @param joinPoint 提供 SpEL 参数变量的 AOP 调用点
     * @return 去除首尾空白但保持大小写的客户端幂等 key
     * @throws IdempotencyKeyRequiredException 两个来源均未提供有效文本时抛出
     */
    private @NotNull String rawKey(
            @NotNull Idempotent annotation,
            @Nullable IdempotentRequest request,
            @NotNull ProceedingJoinPoint joinPoint) {
        String declaredKey = keyEvaluator.evaluate(annotation.key(), joinPoint);
        if (StringUtils.hasText(declaredKey)) {
            return declaredKey.trim();
        }
        if (request != null && StringUtils.hasText(request.getIdempotencyKey())) {
            return request.getIdempotencyKey().trim();
        }
        throw new IdempotencyKeyRequiredException();
    }

    /**
     * 查找方法参数中唯一约定的幂等请求对象。
     *
     * @param args Spring AOP 提供的已绑定参数
     * @return 首个 IdempotentRequest 参数，没有时返回 null
     */
    private @Nullable IdempotentRequest findRequest(@NotNull Object[] args) {
        for (Object arg : args) {
            if (arg instanceof IdempotentRequest request) {
                return request;
            }
        }
        return null;
    }

    /**
     * 生成不泄漏客户端 key 且不受 Java 方法签名重构影响的 Redis key。
     *
     * @param rawKey 客户端提交意图 key
     * @param operation 固定业务操作编码
     * @return 配置前缀、操作摘要和原始 key 摘要组成的有界 Redis key
     */
    private @NotNull String storeKey(@NotNull String rawKey, @NotNull String operation) {
        return properties.getIdempotency().getKeyPrefix() + ":" + DigestUtil.sha256Hex(operation) + ":"
                + DigestUtil.sha256Hex(rawKey.trim());
    }

    /**
     * 使用全局 JSON 契约生成可跨节点比较的请求指纹。
     *
     * @param operation 固定业务操作编码
     * @param source IdempotentRequest 或完整方法实参数组
     * @return 操作编码与请求 JSON 的 SHA-256 摘要
     * @throws InternalServiceException 请求无法固定序列化时抛出，业务方法不会执行
     */
    private @NotNull String fingerprint(@NotNull String operation, @NotNull Object source) {
        try {
            return DigestUtil.sha256Hex(operation + ":" + objectMapper.writeValueAsString(source));
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("幂等请求指纹生成失败", exception);
        }
    }

    /**
     * 使用全局 JSON 契约序列化首次成功响应。
     *
     * @param result 原业务返回值
     * @return null 返回值保持为 null，其他结果返回 JSON
     * @throws InternalServiceException 结果无法序列化时抛出，由首次执行操作隔离为落档故障
     */
    private @Nullable String serializeResult(@Nullable Object result) {
        if (result == null) {
            return null;
        }
        try {
            if (result instanceof ApiResponse<?> response) {
                return objectMapper.writeValueAsString(new StoredHttpResponse(response, objectMapper));
            }
            if (result instanceof ResponseEntity<?> response) {
                return objectMapper.writeValueAsString(new StoredHttpResponse(response, objectMapper));
            }
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("幂等响应缓存失败", exception);
        }
    }

    /** Redis 中稳定保存的 HTTP 状态、响应头和业务响应体。 */
    private static final class StoredHttpResponse {

        private int status;
        private Map<String, List<String>> headers = Map.of();
        private JsonNode body;

        /** 供 Jackson 恢复幂等 HTTP 响应。 */
        private StoredHttpResponse() {
        }

        /** 从首次成功结果提取重放所需的 HTTP 契约。 */
        private StoredHttpResponse(@NotNull ResponseEntity<?> response, @NotNull ObjectMapper objectMapper) {
            this.status = response.getStatusCode().value();
            Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
            response.getHeaders().forEach((name, values) -> copiedHeaders.put(name, List.copyOf(values)));
            this.headers = copiedHeaders;
            this.body = objectMapper.valueToTree(response.getBody());
        }

        /** 从共用业务响应提取重放所需的显式 HTTP 契约。 */
        private StoredHttpResponse(@NotNull ApiResponse<?> response, @NotNull ObjectMapper objectMapper) {
            this.status = response.responseStatus() == null
                    ? HttpStatus.OK.value()
                    : response.responseStatus().value();
            Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
            response.responseHeaders().forEach((name, values) -> copiedHeaders.put(name, List.copyOf(values)));
            this.headers = copiedHeaders;
            this.body = objectMapper.valueToTree(response);
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public @NotNull Map<String, List<String>> getHeaders() {
            return headers;
        }

        public void setHeaders(@NotNull Map<String, List<String>> headers) {
            this.headers = new LinkedHashMap<>(headers);
        }

        public @Nullable JsonNode getBody() {
            return body;
        }

        public void setBody(@Nullable JsonNode body) {
            this.body = body;
        }
    }

}
