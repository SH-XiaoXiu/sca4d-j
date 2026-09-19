package com.sca4d.core.spring.redis;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.concurrent.IdempotencyBeginResult;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.spring.web.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 使用 Redis Lua 原子维护幂等处理所有权和成功结果。 */
public final class RedisIdempotencyStore implements IdempotencyStore {

    private static final String ACQUIRED = "ACQUIRED";

    private static final String PROCESSING = "PROCESSING";

    private static final String SUCCEEDED = "SUCCEEDED";

    private static final DefaultRedisScript<String> BEGIN = script(
            "redis/idempotency/begin.lua",
            String.class);

    private static final DefaultRedisScript<Long> SUCCEED = script(
            "redis/idempotency/succeed.lua",
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE = script(
            "redis/idempotency/release.lua",
            Long.class);

    private final StringRedisTemplate redis;

    private final RuntimeNamespace runtimeNamespace;

    private final ObjectMapper objectMapper;

    /**
     * 创建使用脚手架 Redis 模板和内部稳定 JSON 契约的幂等存储。
     *
     * @param redis 脚手架内部字符串 Redis 模板
     * @param runtimeNamespace 当前应用的运行时命名空间
     * @param objectMapper 脚手架内部 JSON 映射器
     */
    public RedisIdempotencyStore(
            @NotNull StringRedisTemplate redis,
            @NotNull RuntimeNamespace runtimeNamespace,
            @NotNull ObjectMapper objectMapper) {
        this.redis = Objects.requireNonNull(redis, "Redis 模板不能为空");
        this.runtimeNamespace = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public <T> @NotNull IdempotencyBeginResult<T> begin(
            @NotNull IdempotencyKey key,
            @NotNull String fingerprint,
            @NotNull Type resultType,
            @NotNull Duration processingTtl,
            @NotNull Duration retryAfter) {
        String checkedFingerprint = requireText(fingerprint, "请求指纹");
        JavaType javaType = objectMapper.getTypeFactory().constructType(Objects.requireNonNull(resultType, "返回类型不能为空"));
        String token = UUID.randomUUID().toString();
        ObjectNode processing = objectMapper.createObjectNode();
        processing.put("status", PROCESSING);
        processing.put("fingerprint", checkedFingerprint);
        processing.put("token", token);
        String existing = redis.execute(
                BEGIN,
                List.of(storageKey(key)),
                write(processing),
                Long.toString(ttl(processingTtl).toMillis()),
                ACQUIRED);
        if (ACQUIRED.equals(existing)) {
            return IdempotencyBeginResult.acquired(token);
        }

        JsonNode record = read(existing);
        if (!checkedFingerprint.equals(requiredText(record, "fingerprint"))) {
            return IdempotencyBeginResult.conflict();
        }
        String status = requiredText(record, "status");
        if (PROCESSING.equals(status)) {
            return IdempotencyBeginResult.inProgress(ttl(retryAfter));
        }
        if (!SUCCEEDED.equals(status)) {
            throw new IllegalStateException("Redis 幂等记录状态不正确");
        }
        String storedType = requiredText(record, "responseType");
        if (!javaType.toCanonical().equals(storedType)) {
            throw new IllegalStateException("Redis 幂等结果类型与当前方法不一致");
        }
        JsonNode response = record.get("response");
        if (response == null || response.isNull()) {
            return IdempotencyBeginResult.replay(null);
        }
        try {
            T result = objectMapper.readerFor(javaType).readValue(response);
            if (result instanceof ApiResponse<?> apiResponse && record.has("httpHeaders")) {
                Map<String, List<String>> savedHeaders = objectMapper.readerFor(
                        new TypeReference<Map<String, List<String>>>() {
                        }).readValue(record.get("httpHeaders"));
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(savedHeaders);
                JsonNode statusCode = record.get("httpStatus");
                apiResponse.restoreResponse(
                        statusCode == null || statusCode.isNull() ? null : HttpStatus.valueOf(statusCode.intValue()),
                        headers);
            }
            return IdempotencyBeginResult.replay(result);
        } catch (IOException exception) {
            throw new IllegalStateException("Redis 幂等结果无法按当前方法类型读取", exception);
        }
    }

    @Override
    public <T> boolean succeed(
            @NotNull IdempotencyKey key,
            @NotNull String token,
            @NotNull String fingerprint,
            @Nullable T result,
            @NotNull Type resultType,
            @NotNull Duration retention) {
        JavaType javaType = objectMapper.getTypeFactory().constructType(Objects.requireNonNull(resultType, "返回类型不能为空"));
        ObjectNode succeeded = objectMapper.createObjectNode();
        succeeded.put("status", SUCCEEDED);
        succeeded.put("fingerprint", requireText(fingerprint, "请求指纹"));
        succeeded.put("responseType", javaType.toCanonical());
        succeeded.set("response", objectMapper.valueToTree(result));
        // HTTP 元数据只进入 Redis 记录，不混入对外响应 JSON。
        if (result instanceof ApiResponse<?> response) {
            HttpStatus status = response.responseStatus();
            succeeded.put("httpStatus", status == null ? null : status.value());
            Map<String, List<String>> headers = new LinkedHashMap<>();
            response.responseHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
            succeeded.set("httpHeaders", objectMapper.valueToTree(headers));
        }
        Long updated = redis.execute(
                SUCCEED,
                List.of(storageKey(key)),
                requireText(token, "所有权 token"),
                write(succeeded),
                Long.toString(ttl(retention).toMillis()));
        return Long.valueOf(1L).equals(updated);
    }

    @Override
    public boolean release(@NotNull IdempotencyKey key, @NotNull String token) {
        Long deleted = redis.execute(
                RELEASE,
                List.of(storageKey(key)),
                requireText(token, "所有权 token"));
        return Long.valueOf(1L).equals(deleted);
    }

    /** 使用摘要后的幂等键生成当前应用独立的 Redis key。 */
    private String storageKey(IdempotencyKey key) {
        return runtimeNamespace.sca4dj(
                "idempotency",
                DigestUtil.sha256Hex(Objects.requireNonNull(key, "幂等键不能为空").value()));
    }

    /** 将正数时间规范到 Redis 毫秒精度。 */
    private static Duration ttl(Duration value) {
        Duration checked = Objects.requireNonNull(value, "Redis TTL 不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException("Redis TTL 必须为正数");
        }
        return Duration.ofMillis(Math.max(1, checked.toMillis()));
    }

    /** 读取必需的非空白文本字段。 */
    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("Redis 幂等记录缺少字段: " + field);
        }
        return value.textValue();
    }

    /** 校验写入 Redis 的关键文本。 */
    private static String requireText(String value, String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空白文本");
        }
        return checked;
    }

    /** 把幂等记录写为 Redis String。 */
    private String write(JsonNode record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis 幂等记录序列化失败", exception);
        }
    }

    /** 把 Redis String 还原为结构化幂等记录。 */
    private JsonNode read(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Redis 幂等开始脚本没有返回记录");
        }
        try {
            JsonNode record = objectMapper.readTree(value);
            if (!record.isObject()) {
                throw new IllegalStateException("Redis 幂等记录不是 JSON 对象");
            }
            return record;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis 幂等记录无法读取", exception);
        }
    }

    /** 创建从 classpath 读取的 Redis Lua 脚本定义。 */
    private static <T> DefaultRedisScript<T> script(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }
}
