package com.sca4d.iot.emqx.connection;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 使用 Redis 保存 EMQX 终止连接和故障对账所需的 Client ID 与 Broker 节点事实。 */
public class EmqxConnectionRepository {

    private static final DefaultRedisScript<Long> PROJECT = script("project.lua");

    private static final DefaultRedisScript<Long> REMOVE = script("remove.lua");

    private static final DefaultRedisScript<String> DISCONNECT_CLIENT = disconnectClientScript();

    private static final DefaultRedisScript<Long> CLEANUP_CLIENT = script("cleanup-client.lua");

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull Key connectionPrefix;

    private final @NotNull Key clientPrefix;

    private final @NotNull Key connectionIdsKey;

    private final @NotNull Key clientIdsKey;

    private final @NotNull RedisBatches redisBatches;

    /**
     * 创建只保存 EMQX 专用连接字段的 Redis Repository。
     *
     * @param redis IoT 共享 Redis
     * @param redisBatches Redis pipeline 批处理器
     */
    public EmqxConnectionRepository(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull RedisBatches redisBatches) {
        this.redis = Objects.requireNonNull(redis, "EMQX Connection Redis 不能为空");

        this.connectionPrefix = Key.of(TreeCode.ofColon("IOT:EMQX_CONNECTION"));
        this.clientPrefix = Key.of(TreeCode.ofColon("IOT:EMQX_CLIENT_CONNECTION"));
        this.connectionIdsKey = Key.of(TreeCode.ofColon("IOT:EMQX_CONNECTIONS"));
        this.clientIdsKey = Key.of(TreeCode.ofColon("IOT:EMQX_CLIENTS"));
        this.redisBatches = Objects.requireNonNull(redisBatches, "Redis 批处理器不能为空");
    }

    /**
     * 保存或刷新一条 EMQX 当前连接；较旧的对账快照不能覆盖较新的连接代次。
     *
     * @param connection EMQX 当前连接信息
     * @param projectedAt 当前实例取得连接信息的时间
     * @return 当前观察结果是否成为 Client ID 的最新投影
     */
    public boolean connected(@NotNull EmqxClientConnection connection, @NotNull Instant projectedAt) {
        EmqxClientConnection checked = Objects.requireNonNull(connection, "EMQX 连接信息不能为空");
        Instant observed = Objects.requireNonNull(projectedAt, "EMQX 连接观察时间不能为空");
        DeviceConnectionId connectionId = checked.connectionId();
        Long projected = redis.execute(
                PROJECT,
                List.of(
                        connectionKey(connectionId),
                        clientKey(checked.clientId()),
                        connectionIdsKey,
                        clientIdsKey),
                checked.clientId(),
                checked.brokerNode(),
                Long.toString(checked.connectedAt().toEpochMilli()),
                Long.toString(observed.toEpochMilli()),
                connectionId.value());
        return Long.valueOf(1).equals(projected);
    }

    /** 删除指定连接代次；旧连接断线不会删除同一 Client ID 的较新连接。 */
    public boolean disconnected(@NotNull DeviceConnectionId connectionId) {
        DeviceConnectionId checked = Objects.requireNonNull(connectionId, "DeviceConnectionId 不能为空");
        Long removed = redis.execute(
                REMOVE,
                List.of(connectionKey(checked), connectionIdsKey, clientIdsKey),
                KeyRedisSerializer.keyText(redis, clientPrefix) + ":",
                checked.value());
        return Long.valueOf(1).equals(removed);
    }

    /**
     * 把 Client ID 在指定观察时间确认成离线，并返回当时仍映射的连接代次。
     *
     * @param clientId MQTT Client ID
     * @param observedAt 调用 Management API 查询当前状态之前的时间
     * @return 被删除的连接代次；没有旧投影或已有更新观察结果时为空
     */
    public @NotNull Optional<DeviceConnectionId> disconnected(
            @NotNull String clientId,
            @NotNull Instant observedAt) {
        String checkedClientId = Objects.requireNonNull(clientId, "MQTT Client ID 不能为空");
        String removed = redis.execute(
                DISCONNECT_CLIENT,
                List.of(clientKey(checkedClientId), connectionIdsKey, clientIdsKey),
                KeyRedisSerializer.keyText(redis, connectionPrefix) + ":",
                checkedClientId,
                Long.toString(Objects.requireNonNull(observedAt, "EMQX 断线观察时间不能为空").toEpochMilli()));
        return Optional.of(removed)
                .filter(value -> !value.isBlank())
                .map(DeviceConnectionId::new);
    }

    /**
     * @param connectionId EMQX 连接代次
     * @return 当前 Redis 投影仍保存的 MQTT Client ID
     */
    @CheckReturnValue
    public @NotNull Optional<String> findClientId(@NotNull DeviceConnectionId connectionId) {
        Object value = redis.opsForHash()
                .get(
                        connectionKey(Objects.requireNonNull(connectionId, "DeviceConnectionId 不能为空")),
                        "clientId");
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    /**
     * @param clientId MQTT Client ID
     * @return 当前反向索引仍映射的连接代次；离线观察标记返回空
     */
    @CheckReturnValue
    public @NotNull Optional<DeviceConnectionId> findConnectionId(@NotNull String clientId) {
        Object value = redis.opsForHash()
                .get(clientKey(Objects.requireNonNull(clientId, "MQTT Client ID 不能为空")), "connectionId");
        if (value == null || value.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DeviceConnectionId(value.toString()));
    }

    /** 返回 Client ID 的当前反向索引是否仍指向指定连接代次。 */
    @CheckReturnValue
    public boolean isCurrent(@NotNull String clientId, @NotNull DeviceConnectionId connectionId) {
        return findConnectionId(clientId)
                .filter(Objects.requireNonNull(connectionId, "DeviceConnectionId 不能为空")::equals)
                .isPresent();
    }

    /**
     * 使用一次 Redis pipeline 读取一批 Client ID 当前映射的连接代次。
     *
     * @param clientIds MQTT Client ID 集合
     * @return 仍有在线连接映射的 Client ID 和连接代次
     */
    @CheckReturnValue
    public @NotNull Map<String, DeviceConnectionId> findConnectionIds(
            @NotNull Collection<String> clientIds) {
        List<String> ids = List.copyOf(
                new LinkedHashSet<>(
                        Objects.requireNonNull(clientIds, "MQTT Client ID 不能为空")));
        if (ids.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, DeviceConnectionId> result = new LinkedHashMap<>();
        redisBatches.forEach(ids, 1, batch -> {
            List<Object> values = redis.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                    for (String clientId : batch) {
                        operations.opsForHash().get((K) clientKey(clientId), "connectionId");
                    }
                    return null;
                }
            });
            if (values.size() != batch.size()) {
                throw new IllegalStateException("EMQX Connection pipeline 返回数量与请求不一致");
            }
            for (int index = 0; index < batch.size(); index++) {
                Object value = values.get(index);
                if (value != null && !value.toString().isBlank()) {
                    result.put(batch.get(index), new DeviceConnectionId(value.toString()));
                }
            }
        });
        return Map.copyOf(result);
    }

    /**
     * 用 Management API 快照修复 EMQX 专用投影，并保留快照读取期间收到的新事件。
     *
     * @param connections 快照中的全部已识别 Device 连接
     * @param observedAt 开始读取 Management API 之前的时间
     */
    public void reconciled(
            @NotNull List<EmqxClientConnection> connections,
            @NotNull Instant observedAt) {
        List<EmqxClientConnection> current = List.copyOf(
                Objects.requireNonNull(connections, "EMQX 对账连接不能为空"));
        Instant cutoff = Objects.requireNonNull(observedAt, "EMQX 对账观察时间不能为空");
        // 把 Broker 快照中的全部当前连接脚本放入一个 pipeline，并记录不能被清理的连接代次。
        Set<DeviceConnectionId> retained = new HashSet<>();
        for (EmqxClientConnection connection : current) {
            retained.add(connection.connectionId());
        }
        projectAll(current, cutoff);

        // 批量读取快照之外的候选连接，只有观察时间不晚于快照时点的旧投影才允许删除。
        Set<String> ids = redis.opsForSet().members(connectionIdsKey);
        if (ids != null) {
            List<DeviceConnectionId> candidates = ids.stream()
                    .map(DeviceConnectionId::new)
                    .filter(id -> !retained.contains(id))
                    .toList();
            List<Map<Object, Object>> hashes = connectionHashes(candidates);
            List<DeviceConnectionId> expired = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                Map<Object, Object> fields = hashes.get(index);
                Object projectedAt = fields.get("projectedAtMillis");
                if (projectedAt == null || Long.parseLong(projectedAt.toString()) <= cutoff.toEpochMilli()) {
                    expired.add(candidates.get(index));
                }
            }
            removeAll(expired);
        }

        // 对全部 Client 反向索引执行同一观察时间清理脚本，新事件写入的索引会由脚本保留。
        Set<String> clientIds = redis.opsForSet().members(clientIdsKey);
        if (clientIds != null) {
            cleanupClients(clientIds, cutoff);
        }
    }

    /** 使用一个 pipeline 执行快照中全部连接投影脚本。 */
    private void projectAll(@NotNull List<EmqxClientConnection> connections, @NotNull Instant observedAt) {
        redisBatches.forEach(connections, 1, batch -> {
            redis.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                    for (EmqxClientConnection connection : batch) {
                        DeviceConnectionId connectionId = connection.connectionId();
                        operations.execute(
                                PROJECT,
                                List.of(
                                        (K) connectionKey(connectionId),
                                        (K) clientKey(connection.clientId()),
                                        (K) connectionIdsKey,
                                        (K) clientIdsKey),
                                connection.clientId(),
                                connection.brokerNode(),
                                Long.toString(connection.connectedAt().toEpochMilli()),
                                Long.toString(observedAt.toEpochMilli()),
                                connectionId.value());
                    }
                    return null;
                }
            });
        });
    }

    /** 使用一个 pipeline 读取候选连接 Hash，并保持输入顺序。 */
    private @NotNull List<Map<Object, Object>> connectionHashes(
            @NotNull List<DeviceConnectionId> connectionIds) {
        return redisBatches.query(connectionIds, 1, batch -> {
            List<Object> values = redis.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                    for (DeviceConnectionId connectionId : batch) {
                        operations.opsForHash().entries((K) connectionKey(connectionId));
                    }
                    return null;
                }
            });
            return values.stream().map(EmqxConnectionRepository::redisHash).toList();
        });
    }

    /** 使用一个 pipeline 删除已经确认过期的连接代次。 */
    private void removeAll(@NotNull List<DeviceConnectionId> connectionIds) {
        redisBatches.forEach(connectionIds, 1, batch -> {
            redis.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                    for (DeviceConnectionId connectionId : batch) {
                        operations.execute(
                                REMOVE,
                                List.of((K) connectionKey(connectionId), (K) connectionIdsKey, (K) clientIdsKey),
                                KeyRedisSerializer.keyText(redis, clientPrefix) + ":",
                                connectionId.value());
                    }
                    return null;
                }
            });
        });
    }

    /** 使用一个 pipeline 清理全部 Client 反向索引中的旧观察结果。 */
    private void cleanupClients(@NotNull Set<String> clientIds, @NotNull Instant cutoff) {
        redisBatches.forEach(clientIds, 1, batch -> {
            redis.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                    for (String clientId : batch) {
                        operations.execute(
                                CLEANUP_CLIENT,
                                List.of((K) clientKey(clientId), (K) clientIdsKey),
                                clientId,
                                Long.toString(cutoff.toEpochMilli()));
                    }
                    return null;
                }
            });
        });
    }

    /** 把 pipeline 返回值恢复为 Redis Hash 字段。 */
    @SuppressWarnings("unchecked")
    private static @NotNull Map<Object, Object> redisHash(@NotNull Object value) {
        if (!(value instanceof Map<?, ?> fields)) {
            throw new IllegalStateException("EMQX Connection pipeline 返回了无法识别的 Hash");
        }
        return (Map<Object, Object>) fields;
    }

    /** 返回 DeviceConnectionId 对应的 EMQX 专用 Hash key。 */
    private @NotNull Key connectionKey(@NotNull DeviceConnectionId connectionId) {
        return connectionPrefix.part(connectionId.value());
    }

    /** 返回 MQTT Client ID 当前连接代次的反向索引 key。 */
    private @NotNull Key clientKey(@NotNull String clientId) {
        return clientPrefix.part(clientId);
    }

    /** 从 classpath 加载返回 Long 的 EMQX 连接 Redis 脚本。 */
    private static @NotNull DefaultRedisScript<Long> script(@NotNull String fileName) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/iot/emqx-connection/" + fileName));
        script.setResultType(Long.class);
        return script;
    }

    /** 从 classpath 加载返回连接代次字符串的 Client 断线脚本。 */
    private static @NotNull DefaultRedisScript<String> disconnectClientScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/iot/emqx-connection/disconnect-client.lua"));
        script.setResultType(String.class);
        return script;
    }
}
