package com.sca4d.core.spring.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.concurrent.IdempotencyBeginResult;
import com.sca4d.core.api.concurrent.IdempotencyBeginStatus;
import com.sca4d.core.api.concurrent.IdempotencyKey;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.spring.config.CoreJacksonAutoConfiguration;
import com.sca4d.core.spring.web.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 验证 Redis 幂等存储公开状态、结果类型和所有权 token。 */
class RedisIdempotencyStoreTest {

    private final ObjectMapper objectMapper = new CoreJacksonAutoConfiguration().sca4djObjectMapper();

    private final Map<Key, String> storedRecords = new ConcurrentHashMap<>();

    private RedisIdempotencyStore store;

    @BeforeEach
    @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 的泛型。
    void setUp() {
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        doAnswer(
                invocation -> executeScript(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length)))
                .when(redis)
                .execute(any(RedisScript.class), any(List.class), any(Object[].class));
        store = new RedisIdempotencyStore(
                redis,
                objectMapper);
    }

    @Test
    @DisplayName("Distinguishes acquired in-progress conflict and replay states")
    void distinguishesAllBeginStates() {
        IdempotencyKey key = new IdempotencyKey("request-1");

        IdempotencyBeginResult<String> acquired = store.begin(
                key,
                "fingerprint-1",
                String.class,
                Duration.ofMinutes(1),
                Duration.ofSeconds(2));
        IdempotencyBeginResult<String> inProgress = store.begin(
                key,
                "fingerprint-1",
                String.class,
                Duration.ofMinutes(1),
                Duration.ofSeconds(2));
        IdempotencyBeginResult<String> conflict = store.begin(
                key,
                "fingerprint-2",
                String.class,
                Duration.ofMinutes(1),
                Duration.ofSeconds(2));
        assertThat(
                store.succeed(
                        key,
                        acquired.token(),
                        "fingerprint-1",
                        "done",
                        String.class,
                        Duration.ofHours(1)))
                .isTrue();
        IdempotencyBeginResult<String> replay = store.begin(
                key,
                "fingerprint-1",
                String.class,
                Duration.ofMinutes(1),
                Duration.ofSeconds(2));

        assertThat(acquired.status()).isEqualTo(IdempotencyBeginStatus.ACQUIRED);
        assertThat(acquired.token()).isNotBlank();
        assertThat(inProgress.status()).isEqualTo(IdempotencyBeginStatus.IN_PROGRESS);
        assertThat(inProgress.retryAfter()).isEqualTo(Duration.ofSeconds(2));
        assertThat(conflict.status()).isEqualTo(IdempotencyBeginStatus.CONFLICT);
        assertThat(replay.status()).isEqualTo(IdempotencyBeginStatus.REPLAY);
        assertThat(replay.replayResult()).isEqualTo("done");
    }

    @Test
    @DisplayName("Only the current owner can succeed or release processing")
    void enforcesOwnershipToken() {
        IdempotencyKey key = new IdempotencyKey("request-2");
        IdempotencyBeginResult<String> acquired = store.begin(
                key,
                "fingerprint",
                String.class,
                Duration.ofMinutes(1),
                Duration.ofSeconds(1));

        assertThat(
                store.succeed(
                        key,
                        "other-token",
                        "fingerprint",
                        "done",
                        String.class,
                        Duration.ofHours(1)))
                .isFalse();
        assertThat(store.release(key, "other-token")).isFalse();
        assertThat(store.release(key, acquired.token())).isTrue();
        assertThat(
                store.begin(
                        key,
                        "fingerprint",
                        String.class,
                        Duration.ofMinutes(1),
                        Duration.ofSeconds(1))
                        .status())
                .isEqualTo(IdempotencyBeginStatus.ACQUIRED);
    }

    @Test
    @DisplayName("Replays generic and explicit null results")
    void replaysGenericAndNullResults() {
        Type listType = new TypeReference<List<String>>() {
        }.getType();
        IdempotencyKey listKey = new IdempotencyKey("request-list");
        IdempotencyBeginResult<List<String>> acquiredList = store.begin(
                listKey,
                "list-fingerprint",
                listType,
                Duration.ofMinutes(1),
                Duration.ofSeconds(1));
        assertThat(
                store.succeed(
                        listKey,
                        acquiredList.token(),
                        "list-fingerprint",
                        List.of("a", "b"),
                        listType,
                        Duration.ofHours(1)))
                .isTrue();
        assertThat(
                store.<List<String>>begin(
                        listKey,
                        "list-fingerprint",
                        listType,
                        Duration.ofMinutes(1),
                        Duration.ofSeconds(1))
                        .replayResult())
                .containsExactly("a", "b");

        IdempotencyKey nullKey = new IdempotencyKey("request-null");
        IdempotencyBeginResult<String> acquiredNull = store.begin(
                nullKey,
                "null-fingerprint",
                String.class,
                Duration.ofMinutes(1),
                Duration.ofSeconds(1));
        assertThat(
                store.succeed(
                        nullKey,
                        acquiredNull.token(),
                        "null-fingerprint",
                        null,
                        String.class,
                        Duration.ofHours(1)))
                .isTrue();
        IdempotencyBeginResult<String> nullReplay = store.begin(
                nullKey,
                "null-fingerprint",
                String.class,
                Duration.ofMinutes(1),
                Duration.ofSeconds(1));
        assertThat(nullReplay.status()).isEqualTo(IdempotencyBeginStatus.REPLAY);
        assertThat(nullReplay.replayResult()).isNull();
    }

    @Test
    @DisplayName("重放 HTTP 返回类型、状态码、资源响应头和空结果")
    void replaysHttpBodyAndResourceMetadata() throws Exception {
        Type resultType = new TypeReference<ApiResponse<List<Long>>>() {
        }.getType();
        long resourceId = 2096182659930652672L;
        List<ApiResponse<List<Long>>> responses = List.of(
                ApiResponse.ok(List.of(resourceId)),
                ApiResponse.created(
                        List.of(resourceId),
                        URI.create("/groups/1/tests/plans/2")),
                ApiResponse.accepted(List.of(resourceId), URI.create("/groups/1/tests/3")),
                ApiResponse.accepted(null));
        for (int index = 0; index < responses.size(); index++) {
            ApiResponse<List<Long>> original = responses.get(index);
            IdempotencyKey key = new IdempotencyKey("http-result-" + index);
            var acquired = store.begin(key, "same-request", resultType, Duration.ofMinutes(1), Duration.ofSeconds(1));
            assertThat(
                    store.succeed(
                            key,
                            Objects.requireNonNull(acquired.token()),
                            "same-request",
                            original,
                            resultType,
                            Duration.ofHours(1)))
                    .isTrue();

            ApiResponse<List<Long>> restored = store.<ApiResponse<List<Long>>>begin(
                    key,
                    "same-request",
                    resultType,
                    Duration.ofMinutes(1),
                    Duration.ofSeconds(1))
                    .replayResult();

            assertThat(restored).isNotNull();
            assertThat(restored.getData()).isEqualTo(original.getData());
            assertThat(restored.responseStatus()).isEqualTo(original.responseStatus());
            assertThat(restored.responseHeaders()).isEqualTo(original.responseHeaders());
            assertThat(restored.getTimestamp()).isEqualTo(original.getTimestamp());
            assertThat(objectMapper.writeValueAsString(restored))
                    .isEqualTo(objectMapper.writeValueAsString(original))
                    .doesNotContain("httpStatus", "httpHeaders", "responseStatus", "responseHeaders");
        }

        Type emptyType = new TypeReference<ApiResponse<Void>>() {
        }.getType();
        IdempotencyKey emptyKey = new IdempotencyKey("http-empty");
        var acquired = store.begin(emptyKey, "same-request", emptyType, Duration.ofMinutes(1), Duration.ofSeconds(1));
        assertThat(
                store.succeed(
                        emptyKey,
                        Objects.requireNonNull(acquired.token()),
                        "same-request",
                        ApiResponse.noContent(new Version(8)),
                        emptyType,
                        Duration.ofHours(1)))
                .isTrue();

        ApiResponse<Void> empty = store.<ApiResponse<Void>>begin(
                emptyKey,
                "same-request",
                emptyType,
                Duration.ofMinutes(1),
                Duration.ofSeconds(1))
                .replayResult();
        assertThat(empty).isNotNull();
        assertThat(empty.responseStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(empty.responseHeaders().getETag()).isEqualTo("\"8\"");
        assertThat(empty.getData()).isNull();
    }

    @Test
    @DisplayName("重放没有 HTTP 元数据的历史 ApiResponse 记录")
    void replaysLegacyHttpResultWithoutMetadata() throws Exception {
        Type resultType = new TypeReference<ApiResponse<String>>() {
        }.getType();
        IdempotencyKey key = new IdempotencyKey("legacy-http-result");
        var acquired = store.begin(key, "same-request", resultType, Duration.ofMinutes(1), Duration.ofSeconds(1));
        assertThat(
                store.succeed(
                        key,
                        Objects.requireNonNull(acquired.token()),
                        "same-request",
                        ApiResponse.created("resource", URI.create("/resources/1")),
                        resultType,
                        Duration.ofHours(1)))
                .isTrue();
        for (Map.Entry<Key, String> entry : storedRecords.entrySet()) {
            var legacy = (ObjectNode) objectMapper.readTree(entry.getValue());
            legacy.remove(List.of("httpStatus", "httpHeaders"));
            entry.setValue(objectMapper.writeValueAsString(legacy));
        }

        ApiResponse<String> restored = store.<ApiResponse<String>>begin(
                key,
                "same-request",
                resultType,
                Duration.ofMinutes(1),
                Duration.ofSeconds(1))
                .replayResult();

        assertThat(restored).isNotNull();
        assertThat(restored.getData()).isEqualTo("resource");
        assertThat(restored.responseStatus()).isNull();
        assertThat(restored.responseHeaders()).isEmpty();
    }

    /** 在测试内按三个生产 Lua 脚本的状态转换维护一条 Redis String。 */
    private Object executeScript(RedisScript<?> script, List<Key> keys, Object[] arguments) throws Exception {
        Key key = keys.getFirst();
        if (script.getResultType() == String.class) {
            String current = storedRecords.get(key);
            if (current != null) {
                return current;
            }
            storedRecords.put(key, (String) arguments[0]);
            return arguments[2];
        }

        String current = storedRecords.get(key);
        if (current == null) {
            return 0L;
        }
        JsonNode record = objectMapper.readTree(current);
        if (!"PROCESSING".equals(record.path("status").asText())
                || !arguments[0].equals(record.path("token").asText())) {
            return 0L;
        }
        if (arguments.length == 1) {
            storedRecords.remove(key);
        } else {
            storedRecords.put(key, (String) arguments[1]);
        }
        return 1L;
    }
}
