package com.sca4d.iot.connection;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionQueryService;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.change.DeviceChangeNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 使用 Redis 保存可由设备接入实现重建的当前连接信息。 */
public class DeviceConnectionRepository implements DeviceConnectionReceiver, DeviceConnectionQueryService {

    private static final DefaultRedisScript<Long> CONNECT = script("connect.lua");

    private static final DefaultRedisScript<Long> DISCONNECT = script("disconnect.lua");

    private static final DefaultRedisScript<List<Object>> FIND_BY_DEVICE;

    static {
        DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/iot/connection/find-by-device.lua"));
        @SuppressWarnings("unchecked")
        Class<List<Object>> resultType = (Class<List<Object>>) (Class<?>) List.class;
        script.setResultType(resultType);
        FIND_BY_DEVICE = script;
    }

    private final @NotNull Clock clock;

    private final @NotNull Duration ttl;

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull Key connectionPrefix;

    private final @NotNull Key devicePrefix;

    private final @NotNull Key protocolPrefix;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull RedisBatches redisBatches;

    /**
     * 创建只保存共享运行事实的 Redis Repository。
     *
     * @param redis IoT 共享 Redis
     * @param clock 计算连接过期时间的时钟
     * @param ttl 未续期连接信息的保留时间
     * @param changes Device 运行区域变化通知器
     * @param redisBatches Redis pipeline 批处理器
     */
    public DeviceConnectionRepository(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull Clock clock,
            @NotNull Duration ttl,
            @NotNull DeviceChangeNotifier changes,
            @NotNull RedisBatches redisBatches) {
        this.redis = Objects.requireNonNull(redis, "Connection Redis 不能为空");
        this.clock = Objects.requireNonNull(clock, "Connection 时钟不能为空");
        this.ttl = Objects.requireNonNull(ttl, "Connection TTL 不能为空");

        this.connectionPrefix = Key.of(TreeCode.ofColon("IOT:CONNECTION"));
        this.devicePrefix = Key.of(TreeCode.ofColon("IOT:DEVICE_CONNECTIONS"));
        this.protocolPrefix = Key.of(TreeCode.ofColon("IOT:PROTOCOL_CONNECTIONS"));
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.redisBatches = Objects.requireNonNull(redisBatches, "Redis 批处理器不能为空");
    }

    /** 相同 `DeviceConnectionId` 重复登记时刷新共享事实和索引有效期。 */
    @Override
    public void connected(@NotNull DeviceConnection connection) {
        DeviceConnection checked = Objects.requireNonNull(connection, "DeviceConnection 不能为空");
        long expiresAt = clock.millis() + ttl.toMillis();
        long changedDeviceId = Objects.requireNonNull(
                redis.execute(
                        CONNECT,
                        List.of(
                                connectionKey(checked.id()),
                                deviceKey(checked.deviceId()),
                                protocolKey(checked.protocol())),
                        Long.toString(checked.deviceId()),
                        checked.protocol().code(),
                        checked.sessionId().map(SessionId::value).orElse(""),
                        checked.accessMethod().code().format(COLON),
                        Long.toString(checked.connectedAt().toEpochMilli()),
                        Long.toString(ttl.toMillis()),
                        Long.toString(expiresAt),
                        checked.id().value()),
                "连接 Redis Lua 脚本没有返回设备标识");
        if (changedDeviceId > 0) {
            changes.changed(changedDeviceId, DeviceChangeArea.CONNECTION, clock.instant());
        }
    }

    /** 只删除协议和 `DeviceConnectionId` 都与断线事实一致的当前连接。 */
    @Override
    public boolean disconnected(
            @NotNull DeviceConnectionProtocol protocol,
            @NotNull DeviceConnectionId connectionId) {
        DeviceConnectionProtocol checkedProtocol = Objects.requireNonNull(protocol, "DeviceConnectionProtocol 不能为空");
        DeviceConnectionId checkedId = Objects.requireNonNull(connectionId, "DeviceConnectionId 不能为空");
        long changedDeviceId = Objects.requireNonNull(
                redis.execute(
                        DISCONNECT,
                        List.of(connectionKey(checkedId), protocolKey(checkedProtocol)),
                        checkedProtocol.code(),
                        KeyRedisSerializer.keyText(redis, devicePrefix) + ":",
                        checkedId.value()),
                "断开 Redis Lua 脚本没有返回设备标识");
        if (changedDeviceId > 0) {
            changes.changed(changedDeviceId, DeviceChangeArea.CONNECTION, clock.instant());
            return true;
        }
        return false;
    }

    /** 对账只删除读取接入实现快照之前已经登记且快照中不存在的连接。 */
    @Override
    public void reconciled(
            @NotNull DeviceConnectionProtocol protocol,
            @NotNull List<DeviceConnection> connections,
            @NotNull Instant observedAt) {
        DeviceConnectionProtocol checkedProtocol = Objects.requireNonNull(protocol, "DeviceConnectionProtocol 不能为空");
        List<DeviceConnection> current = List.copyOf(Objects.requireNonNull(connections, "连接集合不能为空"));
        Instant cutoff = Objects.requireNonNull(observedAt, "连接对账时间不能为空");
        Set<DeviceConnectionId> retained = new HashSet<>();
        for (DeviceConnection connection : current) {
            if (!checkedProtocol.equals(connection.protocol())) {
                throw new IllegalArgumentException("连接对账包含其他 DeviceConnectionProtocol");
            }
            retained.add(connection.id());
            connected(connection);
        }
        for (DeviceConnection projected : find(protocolKey(checkedProtocol))) {
            if (!projected.connectedAt().isAfter(cutoff) && !retained.contains(projected.id())) {
                disconnected(checkedProtocol, projected.id());
            }
        }
    }

    /** 使用分批 pipeline 读取连接信息，每个 Device 的 Lua 脚本同时清理失效索引。 */
    @Override
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceConnection>> connections(
            @NotNull Collection<@NotNull Long> deviceIds) {
        // 校验并按首次出现顺序合并 Device，确保每个输入标识都有一个返回项。
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long deviceId : Objects.requireNonNull(deviceIds, "Device 标识集合不能为空")) {
            Long checkedId = Objects.requireNonNull(deviceId, "Device 标识不能为空");
            if (checkedId <= 0) {
                throw new IllegalArgumentException("Device 标识必须为正数");
            }
            unique.add(checkedId);
        }
        if (unique.isEmpty()) {
            return Map.of();
        }
        List<Long> ordered = List.copyOf(unique);
        String observedAt = Long.toString(clock.millis());
        LinkedHashMap<Long, List<DeviceConnection>> result = new LinkedHashMap<>();

        // 分批执行 Redis pipeline；每个 Lua 调用读取一个 Device 并清理它的失效索引。
        redisBatches.forEach(ordered, 1, batch -> {
            List<Object> rows = redis.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                    for (long deviceId : batch) {
                        operations.execute(
                                FIND_BY_DEVICE,
                                List.of((K) deviceKey(deviceId)),
                                KeyRedisSerializer.keyText(redis, connectionPrefix) + ":",
                                observedAt);
                    }
                    return null;
                }
            });
            if (rows.size() != batch.size()) {
                throw new IllegalStateException("Connection Redis pipeline 返回数量与请求不一致");
            }

            // pipeline 结果顺序与命令顺序一致，按同一位置恢复 DeviceConnection 集合。
            for (int index = 0; index < batch.size(); index++) {
                result.put(batch.get(index), connections(rows.get(index)));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    /** 清理过期索引，并忽略已经没有 Redis 主记录的 DeviceConnectionId。 */
    private @NotNull List<DeviceConnection> find(@NotNull Key indexKey) {
        redis.opsForZSet().removeRangeByScore(indexKey, Double.NEGATIVE_INFINITY, clock.millis());
        Set<String> ids = redis.opsForZSet().rangeByScore(indexKey, clock.millis(), Double.POSITIVE_INFINITY);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> ordered = List.copyOf(ids);
        List<DeviceConnection> result = new ArrayList<>();
        redisBatches.forEach(ordered, 1, batch -> {
            List<Object> rows = redis.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                    for (String value : batch) {
                        operations.opsForHash().entries((K) connectionKey(new DeviceConnectionId(value)));
                    }
                    return null;
                }
            });
            if (rows.size() != batch.size()) {
                throw new IllegalStateException("Connection Redis protocol pipeline 返回数量与请求不一致");
            }
            List<String> missing = new ArrayList<>();
            for (int index = 0; index < batch.size(); index++) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> fields = (Map<Object, Object>) rows.get(index);
                if (fields.isEmpty()) {
                    missing.add(batch.get(index));
                    continue;
                }
                DeviceConnectionId id = new DeviceConnectionId(batch.get(index));
                result.add(connection(id, fields));
            }
            if (!missing.isEmpty()) {
                redis.opsForZSet().remove(indexKey, missing.toArray());
            }
        });
        return List.copyOf(result);
    }

    /** 把 Redis Hash 恢复成协议无关的连接信息。 */
    private static @NotNull DeviceConnection connection(
            @NotNull DeviceConnectionId id,
            @NotNull Map<Object, Object> fields) {
        String sessionId = string(fields, "sessionId");
        return new DeviceConnection(
                id,
                Long.parseLong(string(fields, "deviceId")),
                new DeviceConnectionProtocol(string(fields, "protocol")),
                sessionId.isEmpty() ? null : new SessionId(sessionId),
                DeviceAccessMethod.parse(string(fields, "accessMethod")),
                Instant.ofEpochMilli(Long.parseLong(string(fields, "connectedAtMillis"))));
    }

    /** 把 Lua 返回的固定六字段平铺列表恢复为连接信息。 */
    private static @NotNull List<DeviceConnection> connections(@Nullable Object value) {
        if (!(value instanceof List<?> fields) || fields.isEmpty()) {
            return List.of();
        }
        if (fields.size() % 6 != 0) {
            throw new IllegalStateException("Connection Redis 批量查询返回字段数量错误");
        }
        List<DeviceConnection> result = new ArrayList<>(fields.size() / 6);
        for (int offset = 0; offset < fields.size(); offset += 6) {
            String connectionId = requiredText(fields.get(offset), "connectionId");
            String deviceId = requiredText(fields.get(offset + 1), "deviceId");
            String protocol = requiredText(fields.get(offset + 2), "protocol");
            String sessionId = text(fields.get(offset + 3));
            String accessMethod = requiredText(fields.get(offset + 4), "accessMethod");
            String connectedAtMillis = requiredText(fields.get(offset + 5), "connectedAtMillis");
            result.add(
                    new DeviceConnection(
                            new DeviceConnectionId(connectionId),
                            Long.parseLong(deviceId),
                            new DeviceConnectionProtocol(protocol),
                            sessionId.isEmpty() ? null : new SessionId(sessionId),
                            DeviceAccessMethod.parse(accessMethod),
                            Instant.ofEpochMilli(Long.parseLong(connectedAtMillis))));
        }
        return List.copyOf(result);
    }

    /** 返回 Redis 字段文本；缺失字段由调用方决定是否允许。 */
    private static @NotNull String text(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : value.toString();
    }

    /** 返回非空 Redis 必需字段。 */
    private static @NotNull String requiredText(@Nullable Object value, @NotNull String name) {
        String result = text(value);
        if (result.isEmpty()) {
            throw new IllegalStateException("Connection Redis 数据缺少字段: " + name);
        }
        return result;
    }

    /** 返回必需 Hash 字段。 */
    private static @NotNull String string(@NotNull Map<Object, Object> fields, @NotNull String name) {
        Object result = fields.get(name);
        if (result == null) {
            throw new IllegalStateException("Connection Redis 数据缺少字段: " + name);
        }
        return result.toString();
    }

    /** 返回 Connection 主记录 key。 */
    private @NotNull Key connectionKey(@NotNull DeviceConnectionId id) {
        return connectionPrefix.part(id.value());
    }

    /** 返回 Device 到 DeviceConnectionId 的反向索引 key。 */
    private @NotNull Key deviceKey(long deviceId) {
        return devicePrefix.part(deviceId);
    }

    /** 返回连接协议到 DeviceConnectionId 的反向索引 key。 */
    private @NotNull Key protocolKey(@NotNull DeviceConnectionProtocol protocol) {
        return protocolPrefix.part(protocol.code());
    }

    /** 从 classpath 加载返回 Long 的连接状态 Redis 脚本。 */
    private static @NotNull DefaultRedisScript<Long> script(@NotNull String fileName) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/iot/connection/" + fileName));
        script.setResultType(Long.class);
        return script;
    }
}
