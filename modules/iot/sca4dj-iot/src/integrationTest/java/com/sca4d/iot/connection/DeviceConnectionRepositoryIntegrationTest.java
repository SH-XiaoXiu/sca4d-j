package com.sca4d.iot.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.redis.serializer.StringRedisSerializer.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.RedisHashTag;
import com.sca4d.core.spring.redis.RedisPipelineProperties;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.state.RedisReportedStateStore;
import com.sca4d.iot.state.ReportedStateKeyMigration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

/** 使用真实 Redis 验证 Device 连接批量查询、分批 pipeline 和索引清理。 */
@Testcontainers
class DeviceConnectionRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-27T02:00:00Z");

    @Test
    @DisplayName("reported批量读取分批且只返回指定绑定，单设备与批量结果一致")
    void readsReportedGroupsAcrossBatchesWithoutMixingBindings() {
        RedisReportedStateStore store = new RedisReportedStateStore(
                redis,
                new ThingDataJsonCodec(new ObjectMapper()),
                new RedisBatches(new RedisPipelineProperties()));
        var first = new ReportedStateUpdate(
                "status",
                ReportedMergeMode.SNAPSHOT,
                Set.of("power"),
                Map.of("power", 1),
                "test",
                "1",
                NOW);
        var rebound = new ReportedStateUpdate(
                "status",
                ReportedMergeMode.SNAPSHOT,
                Set.of("power"),
                Map.of("power", 2),
                "test",
                "1",
                NOW.plusSeconds(60));
        store.update(71, 1, first, first.values(), "test", "1", NOW);
        store.update(71, 2, rebound, rebound.values(), "test", "1", NOW.plusSeconds(60));
        Map<Long, Long> bindings = new LinkedHashMap<>();
        LongStream.rangeClosed(1, 1001).forEach(id -> bindings.put(id, 1L));

        var result = store.find(bindings, "status");

        assertThat(result).containsOnlyKeys(71L);
        assertThat(result.get(71L).values()).containsEntry("power", 1);
        assertThat(result.get(71L).observedAt()).isEqualTo(NOW);
        assertThat(store.find(71, 1).getFirst().values()).isEqualTo(result.get(71L).values());
        assertThat(store.find(bindings, "missing")).isEmpty();
        assertThat(store.find(Map.of(), "status")).isEmpty();
    }

    @Test
    void copiesNonexpiringReportedDataWithoutOverwritingOrRemovingTheSource() {
        String index = "old-app:sca4dj:iot:reported-groups:71:1";
        String group = "old-app:sca4dj:iot:reported-group:71:1:Status:a";
        rawRedis.opsForSet().add(index, "Status:a");
        rawRedis.opsForHash().put(group, "version", "7");
        rawRedis.opsForHash().put(group, "value:power", "1");
        var migration = new ReportedStateKeyMigration(rawRedis, redis);
        assertThat(migration.verify("old-app")).isEqualTo(2);
        assertThat(migration.copy("old-app")).isEqualTo(2);
        assertThat(migration.copy("old-app")).isEqualTo(2);
        var target = Key.of(TreeCode.of("IOT:REPORTED_GROUP"), 71L, 1L).part("Status:a");
        assertThat(redis.opsForHash().get(target, "version")).isEqualTo("7");
        assertThat(rawRedis.hasKey(group)).isTrue();
        redis.opsForHash().put(target, "version", "8");
        assertThatThrownBy(() -> migration.copy("old-app")).hasMessageContaining("禁止覆盖");
        assertThat(rawRedis.opsForHash().get(group, "version")).isEqualTo("7");
    }

    @Container
    @SuppressWarnings("resource") // Testcontainers 扩展负责测试类结束时停止静态容器。
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.2.14-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    private static RedisTemplate<Key, String> redis;

    private static StringRedisTemplate rawRedis;

    @BeforeAll
    static void connectRedis() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(
                        REDIS.getHost(),
                        REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        AppName.publish("sca4d-test");
        rawRedis = new StringRedisTemplate(connectionFactory);
        redis = new RedisTemplate<>();
        redis.setConnectionFactory(connectionFactory);
        redis.setKeySerializer(new KeyRedisSerializer(List.of(new RedisHashTag(TreeCode.of("IOT")))));
        redis.setValueSerializer(UTF_8);
        redis.setHashKeySerializer(UTF_8);
        redis.setHashValueSerializer(UTF_8);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnectRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clearRedis() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("批量查询保留首次请求顺序并返回全部 Device")
    @SuppressWarnings("DataFlowIssue") // 负向修改用于验证公开 Map 的不可修改契约。
    void returnsAllRequestedDevicesInFirstOccurrenceOrder() {
        DeviceConnectionRepository repository = repository(Clock.fixed(NOW, ZoneOffset.UTC));
        DeviceConnection first = device71Connection("connection-a", NOW.minusSeconds(2));
        DeviceConnection second = device71Connection("connection-b", NOW.minusSeconds(1));
        repository.connected(first);
        repository.connected(second);

        Map<Long, List<DeviceConnection>> result = repository.connections(List.of(72L, 71L, 72L));

        assertThat(new ArrayList<>(result.keySet())).containsExactly(72L, 71L);
        assertThat(result.get(72L)).isEmpty();
        assertThat(result.get(71L))
                .extracting(connection -> connection.id().value())
                .containsExactlyInAnyOrder("connection-a", "connection-b");
        assertThatThrownBy(() -> result.put(73L, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("超过单次 pipeline 的查询仍完整返回")
    void partitionsLargeRequestsWithoutChangingTheContract() {
        DeviceConnectionRepository repository = repository(Clock.fixed(NOW, ZoneOffset.UTC));
        List<Long> deviceIds = LongStream.rangeClosed(1, 1_001).boxed().toList();

        Map<Long, List<DeviceConnection>> result = repository.connections(deviceIds);

        assertThat(result).hasSize(1_001).allSatisfy((deviceId, connections) -> assertThat(connections).isEmpty());
    }

    @Test
    @DisplayName("查询删除过期索引和缺少主记录的索引")
    void removesExpiredAndDanglingIndexes() {
        DeviceConnectionRepository writer = repository(Clock.fixed(NOW, ZoneOffset.UTC));
        writer.connected(device71Connection("expired", NOW));
        redis.opsForZSet().add(deviceKey(72L), "missing", NOW.plusSeconds(60).toEpochMilli());

        DeviceConnectionRepository reader = repository(Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));
        Map<Long, List<DeviceConnection>> result = reader.connections(List.of(71L, 72L));

        assertThat(result.get(71L)).isEmpty();
        assertThat(result.get(72L)).isEmpty();
        assertThat(redis.opsForZSet().size(deviceKey(71L))).isZero();
        assertThat(redis.opsForZSet().size(deviceKey(72L))).isZero();
    }

    @Test
    @DisplayName("损坏的连接主记录明确失败")
    void rejectsCorruptedConnectionFields() {
        redis.opsForZSet().add(deviceKey(71L), "corrupt", NOW.plusSeconds(60).toEpochMilli());
        redis.opsForHash().put(corruptConnectionKey(), "deviceId", "71");

        assertThatThrownBy(() -> repository(Clock.fixed(NOW, ZoneOffset.UTC)).connections(71L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("protocol");
    }

    /** 创建使用当前测试 App 隔离名称和一分钟 TTL 的 Repository。 */
    private static DeviceConnectionRepository repository(Clock clock) {
        return new DeviceConnectionRepository(
                redis,
                clock,
                Duration.ofMinutes(1),
                new DeviceChangeNotifier(List.of()),
                new RedisBatches(new RedisPipelineProperties()));
    }

    /** 创建 Device 71 不使用 Session 的 MQTT 连接信息。 */
    private static DeviceConnection device71Connection(String id, Instant connectedAt) {
        return new DeviceConnection(
                new DeviceConnectionId(id),
                71L,
                DeviceConnectionProtocol.MQTT,
                null,
                DeviceAccessMethod.anonymous(),
                connectedAt);
    }

    /** 返回当前测试 App 隔离名称下的 Device 反向索引 key。 */
    private static Key deviceKey(long deviceId) {
        return Key.of(TreeCode.of("IOT:DEVICE_CONNECTIONS"), deviceId);
    }

    /** 返回损坏数据测试使用的 Connection 主记录 key。 */
    private static Key corruptConnectionKey() {
        return Key.of(TreeCode.of("IOT:CONNECTION"), "corrupt");
    }
}
