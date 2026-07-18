package com.sca4d.common.concurrent.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.util.List;

/** 验证 Redis 幂等记录的首次写入、完成、重放和过期处理。 */
class RedisIdempotencyStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcquireWhenProcessingKeyIsAbsent() {
        RedisFixture fixture = fixture();
        when(fixture.scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<String>>any(),
                eq(List.of("key")),
                anyString(),
                eq("5000"),
                eq("ACQUIRED")))
                .thenReturn("ACQUIRED");

        IdempotencyBeginResult result = fixture.store.begin(
                "key", "fingerprint", Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertThat(result.getStatus()).isEqualTo(IdempotencyBeginStatus.ACQUIRED);
        assertThat(result.getToken()).isNotBlank();
    }

    @Test
    void shouldReplayWhenSuccessfulRecordHasSameFingerprint() throws Exception {
        RedisFixture fixture = fixture();
        when(fixture.scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<String>>any(),
                eq(List.of("key")),
                anyString(),
                eq("5000"),
                eq("ACQUIRED")))
                .thenReturn(write(IdempotencyRecord.succeeded("fingerprint", "\"ok\"", "java.lang.String")));

        IdempotencyBeginResult result = fixture.store.begin(
                "key", "fingerprint", Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertThat(result.getStatus()).isEqualTo(IdempotencyBeginStatus.REPLAY);
        assertThat(result.getRecord()).isNotNull();
        assertThat(result.getRecord().getResponseJson()).isEqualTo("\"ok\"");
    }

    @Test
    void shouldReportInProgressWhenProcessingRecordHasSameFingerprint() throws Exception {
        RedisFixture fixture = fixture();
        when(fixture.scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<String>>any(),
                eq(List.of("key")),
                anyString(),
                eq("5000"),
                eq("ACQUIRED")))
                .thenReturn(write(IdempotencyRecord.processing("fingerprint", "token-1")));

        IdempotencyBeginResult result = fixture.store.begin(
                "key", "fingerprint", Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertThat(result.getStatus()).isEqualTo(IdempotencyBeginStatus.IN_PROGRESS);
        assertThat(result.getRetryAfter()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldReportConflictWhenFingerprintDiffers() throws Exception {
        RedisFixture fixture = fixture();
        when(fixture.scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<String>>any(),
                eq(List.of("key")),
                anyString(),
                eq("5000"),
                eq("ACQUIRED")))
                .thenReturn(write(IdempotencyRecord.processing("other", "token-1")));

        IdempotencyBeginResult result = fixture.store.begin(
                "key", "fingerprint", Duration.ofSeconds(5), Duration.ofSeconds(3));

        assertThat(result.getStatus()).isEqualTo(IdempotencyBeginStatus.CONFLICT);
    }

    @Test
    void shouldUseTokenGuardedScriptWhenSucceeding() throws Exception {
        RedisFixture fixture = fixture();
        when(fixture.scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                eq(List.of("key")),
                eq("token-1"),
                anyString(),
                eq("8000")))
                .thenReturn(1L);

        boolean stored = fixture.store.succeed(
                "key", "token-1", "fingerprint", "\"ok\"", "java.lang.String", Duration.ofSeconds(8));

        assertThat(stored).isTrue();
        verify(fixture.scripts)
                .execute(
                        ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                        eq(List.of("key")),
                        eq("token-1"),
                        anyString(),
                        eq("8000"));
    }

    @Test
    void shouldUseTokenGuardedScriptWhenReleasing() {
        RedisFixture fixture = fixture();
        when(fixture.scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                eq(List.of("key")),
                eq("token-1")))
                .thenReturn(1L);

        boolean released = fixture.store.release("key", "token-1");

        assertThat(released).isTrue();
        verify(fixture.scripts)
                .execute(
                        ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                        eq(List.of("key")),
                        eq("token-1"));
    }

    @Test
    void shouldRejectNonPositiveRecordTtl() {
        RedisFixture fixture = fixture();

        assertThatThrownBy(() -> fixture.store.begin(
                "key", "fingerprint", Duration.ZERO, Duration.ofSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("幂等记录 TTL 必须大于 0");
        assertThatThrownBy(() -> fixture.store.succeed(
                "key", "token", "fingerprint", null, "java.lang.Void", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("幂等记录 TTL 必须大于 0");
    }

    /**
     * 创建共享同一 Redis Lua 执行边界 mock 的幂等存储夹具。
     *
     * @return 可验证 Lua 调用和存储结果的依赖组合
     */
    private @NotNull RedisFixture fixture() {
        RedisScriptExecutor scripts = mock(RedisScriptExecutor.class);
        return new RedisFixture(scripts, new RedisIdempotencyStore(scripts, objectMapper));
    }

    /**
     * 使用被测存储相同的 ObjectMapper 序列化幂等记录。
     *
     * @param record 待写入 Redis mock 的幂等记录
     * @return JSON 文本
     * @throws Exception JSON 序列化失败时抛出
     */
    private @NotNull String write(@NotNull IdempotencyRecord record) throws Exception {
        return objectMapper.writeValueAsString(record);
    }

    /** Redis 幂等存储测试需要共同持有的依赖组合。 */
    private static final class RedisFixture {

        private final RedisScriptExecutor scripts;
        private final RedisIdempotencyStore store;

        /**
         * 固化单个测试场景共享的 Redis 依赖和被测存储。
         *
         * @param scripts Redis Lua 执行边界 mock
         * @param store 被测 Redis 幂等存储
         */
        private RedisFixture(
                @NotNull RedisScriptExecutor scripts,
                @NotNull RedisIdempotencyStore store) {
            this.scripts = scripts;
            this.store = store;
        }
    }
}
