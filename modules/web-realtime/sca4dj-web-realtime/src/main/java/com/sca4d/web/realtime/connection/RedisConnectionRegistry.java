package com.sca4d.web.realtime.connection;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.connection.Connection;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.connection.ConnectionProtocol;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.web.realtime.config.WebRealtimeInfrastructureNames;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 使用 Redis Hash、Set 和按租约到期时间排序的 SET 保存所有 Web Realtime 共享索引。 */
@ApiStatus.Internal
public class RedisConnectionRegistry implements ConnectionRegistry {

    private static final ConnectionProtocol WEB_REALTIME = new ConnectionProtocol("web-realtime");

    private static final DefaultRedisScript<Long> REGISTER = script("registry-register.lua");

    private static final DefaultRedisScript<Long> RENEW = script("registry-renew.lua");

    private static final DefaultRedisScript<Long> UNREGISTER = script("registry-unregister.lua");

    private static final DefaultRedisScript<Long> SUBSCRIBE = script("registry-subscribe.lua");

    private static final DefaultRedisScript<Long> UNSUBSCRIBE = script("registry-unsubscribe.lua");

    private final @NotNull WebRealtimeProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull RedisBatches redisBatches;

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull WebRealtimeInfrastructureNames names;

    /** 创建基于 Redis 共享索引的 Web Realtime 连接注册表。 */
    public RedisConnectionRegistry(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull WebRealtimeInfrastructureNames names,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock,
            @NotNull RedisBatches redisBatches) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.names = Objects.requireNonNull(names, "Web Realtime 基础设施名称不能为空");
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        this.clock = Objects.requireNonNull(clock, "Web Realtime 时钟不能为空");
        this.redisBatches = Objects.requireNonNull(redisBatches, "Redis 批处理器不能为空");
    }

    /** 原子登记连接元数据和自动身份目标，并设置共同租约。 */
    public void register(
            @NotNull ConnectionId connectionId,
            @NotNull String nodeId,
            @NotNull Session session) {
        // 从权威 Session 生成连接自动拥有的全部身份目标。
        Session checkedSession = Objects.requireNonNull(session, "Session 不能为空");
        Set<WebRealtimeTarget> targets = new LinkedHashSet<>();
        targets.add(WebRealtimeTarget.account(checkedSession.account().accountId()));
        targets.add(WebRealtimeTarget.subject(checkedSession.subject().subjectId()));
        checkedSession.principals()
                .stream()
                .map(Principal::principalId)
                .map(WebRealtimeTarget::principal)
                .forEach(targets::add);
        targets.add(WebRealtimeTarget.session(checkedSession.id()));
        targets.add(WebRealtimeTarget.connection(connectionId));

        // 使用同一 Redis Cluster 槽位中的 Lua 原子写入连接、反向目标和目标成员租约。
        List<Key> keys = new ArrayList<>();
        keys.add(connectionKey(connectionId));
        keys.add(connectionTargetsKey(connectionId));
        keys.add(connectionSubscriptionsKey(connectionId));
        targets.stream().map(this::targetKey).forEach(keys::add);
        Instant expiresAt = clock.instant().plus(properties.getLeaseDuration());
        redis.execute(
                REGISTER,
                keys,
                connectionId.value(),
                nodeId,
                checkedSession.id().value(),
                Long.toString(checkedSession.account().accountId()),
                Long.toString(checkedSession.subject().subjectId()),
                Long.toString(expiresAt.toEpochMilli()),
                Long.toString(properties.getLeaseDuration().toMillis()));
    }

    /** 登记不包含身份展开信息的公共连接信息。 */
    @Override
    public void register(@NotNull Connection connection) {
        Connection checked = Objects.requireNonNull(connection, "Connection 不能为空");
        Instant expiresAt = clock.instant().plus(properties.getLeaseDuration());
        Key connectionKey = connectionKey(checked.id());
        redis.opsForHash()
                .putAll(
                        connectionKey,
                        Map.of(
                                "node",
                                checked.nodeId(),
                                "session",
                                checked.sessionId().map(SessionId::value).orElse(""),
                                "protocol",
                                checked.protocol().code(),
                                "connectedAtMillis",
                                Long.toString(checked.connectedAt().toEpochMilli()),
                                "lastActivityAtMillis",
                                Long.toString(checked.lastActivityAt().toEpochMilli())));
        redis.expire(connectionKey, properties.getLeaseDuration());
        checked.sessionId().ifPresent(sessionId -> {
            Key target = targetKey(WebRealtimeTarget.session(sessionId));
            redis.opsForZSet().add(target, checked.id().value(), expiresAt.toEpochMilli());
            redis.expire(target, properties.getLeaseDuration().multipliedBy(2));
        });
    }

    /** 更新公共连接信息的最近活动时间。 */
    @Override
    public void touch(
            @NotNull ConnectionId connectionId,
            @NotNull Instant lastActivityAt) {
        redis.opsForHash()
                .put(
                        connectionKey(connectionId),
                        "lastActivityAtMillis",
                        Long.toString(Objects.requireNonNull(lastActivityAt, "Connection 活动时间不能为空").toEpochMilli()));
    }

    /** 删除公共连接信息。 */
    @Override
    public void remove(@NotNull ConnectionId connectionId) {
        unregister(connectionId);
    }

    /** 读取当前 Session 关联的公共连接信息。 */
    @Override
    public @NotNull List<Connection> findBySession(@NotNull SessionId sessionId) {
        Map<String, List<ConnectionId>> grouped = resolve(
                WebRealtimeTarget.session(Objects.requireNonNull(sessionId, "SessionId 不能为空")));
        List<Connection> result = new ArrayList<>();
        List<ConnectionId> ids = grouped.values().stream().flatMap(List::stream).toList();
        redisBatches.forEach(ids, 1, batch -> {
            List<Object> values = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (ConnectionId id : batch) {
                    connection.hashCommands().hGetAll(KeyRedisSerializer.keyBytes(redis, connectionKey(id)));
                }
                return null;
            });
            if (values.size() != batch.size()) {
                throw new IllegalStateException("Redis 未完整返回连接元数据");
            }
            for (int index = 0; index < values.size(); index++) {
                if (!(values.get(index) instanceof Map<?, ?> fields)) {
                    throw new IllegalStateException("Redis 连接元数据格式错误");
                }
                Object node = fields.get("node");
                if (node == null) {
                    continue;
                }
                Object protocol = fields.get("protocol");
                result.add(
                        new Connection(
                                batch.get(index),
                                new ConnectionProtocol(protocol == null ? WEB_REALTIME.code() : protocol.toString()),
                                sessionId,
                                instant(fields.get("connectedAtMillis")),
                                instant(fields.get("lastActivityAtMillis")),
                                node.toString()));
            }
        });
        return List.copyOf(result);
    }

    /** 删除当前 Session 的全部共享连接信息。 */
    @Override
    public void terminateBySession(@NotNull SessionId sessionId, @NotNull String reason) {
        findBySession(sessionId).forEach(connection -> remove(connection.id()));
    }

    /** 延长连接元数据和当前全部目标成员的租约。 */
    public boolean renew(@NotNull ConnectionId connectionId) {
        Instant expiresAt = clock.instant().plus(properties.getLeaseDuration());
        Long result = redis.execute(
                RENEW,
                List.of(
                        connectionKey(connectionId),
                        connectionTargetsKey(connectionId),
                        connectionSubscriptionsKey(connectionId)),
                connectionId.value(),
                Long.toString(expiresAt.toEpochMilli()),
                Long.toString(properties.getLeaseDuration().toMillis()));
        return Long.valueOf(1).equals(result);
    }

    /** 从全部目标 SET 和连接元数据中删除指定连接。 */
    public void unregister(@NotNull ConnectionId connectionId) {
        redis.execute(
                UNREGISTER,
                List.of(
                        connectionKey(connectionId),
                        connectionTargetsKey(connectionId),
                        connectionSubscriptionsKey(connectionId)),
                connectionId.value());
    }

    /** 为 STOMP subscription ID 登记已经由具体应用授权的 Resource 或 Topic 目标。 */
    public boolean subscribe(
            @NotNull ConnectionId connectionId,
            @NotNull String subscriptionId,
            @NotNull WebRealtimeTarget target) {
        WebRealtimeTarget checkedTarget = Objects.requireNonNull(target, "订阅目标不能为空");
        if (checkedTarget.type() != WebRealtimeTarget.Type.RESOURCE
                && checkedTarget.type() != WebRealtimeTarget.Type.TOPIC) {
            throw new IllegalArgumentException("动态订阅只接受 Resource 或 Topic 目标");
        }
        Instant expiresAt = clock.instant().plus(properties.getLeaseDuration());
        Long result = redis.execute(
                SUBSCRIBE,
                List.of(
                        connectionKey(connectionId),
                        connectionTargetsKey(connectionId),
                        targetKey(checkedTarget),
                        connectionSubscriptionsKey(connectionId)),
                connectionId.value(),
                Long.toString(expiresAt.toEpochMilli()),
                Long.toString(properties.getLeaseDuration().toMillis()),
                Objects.requireNonNull(subscriptionId, "STOMP subscription ID 不能为空"));
        if (Long.valueOf(-1).equals(result)) {
            throw new IllegalStateException("STOMP subscription ID 已经登记");
        }
        return Long.valueOf(1).equals(result);
    }

    /** 删除一个 STOMP subscription ID 对应的动态 Resource 或 Topic 订阅。 */
    public void unsubscribe(@NotNull ConnectionId connectionId, @NotNull String subscriptionId) {
        redis.execute(
                UNSUBSCRIBE,
                List.of(connectionTargetsKey(connectionId), connectionSubscriptionsKey(connectionId)),
                connectionId.value(),
                Objects.requireNonNull(subscriptionId, "STOMP subscription ID 不能为空"));
    }

    /** 删除到期成员，并把仍有连接元数据的 ConnectionId 按持有节点分组。 */
    @NotNull
    public Map<@NotNull String, @NotNull List<@NotNull ConnectionId>> resolve(@NotNull WebRealtimeTarget target) {
        Key targetKey = targetKey(Objects.requireNonNull(target, "Web Realtime 目标不能为空"));
        double now = clock.instant().toEpochMilli();
        redis.opsForZSet().removeRangeByScore(targetKey, Double.NEGATIVE_INFINITY, now);
        Set<String> ids = redis.opsForZSet().rangeByScore(targetKey, now, Double.POSITIVE_INFINITY);
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<ConnectionId>> result = new LinkedHashMap<>();
        List<String> ordered = List.copyOf(ids);
        redisBatches.forEach(ordered, 1, batch -> {
            List<Object> nodes = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (String id : batch) {
                    connection.hashCommands()
                            .hGet(
                                    KeyRedisSerializer.keyBytes(redis, connectionKey(new ConnectionId(id))),
                                    "node".getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            if (nodes.size() != batch.size()) {
                throw new IllegalStateException("Redis 未完整返回连接归属节点");
            }
            List<String> missing = new ArrayList<>();
            for (int index = 0; index < nodes.size(); index++) {
                Object node = nodes.get(index);
                String id = batch.get(index);
                if (node == null) {
                    missing.add(id);
                } else {
                    result.computeIfAbsent(node.toString(), ignored -> new ArrayList<>()).add(new ConnectionId(id));
                }
            }
            if (!missing.isEmpty()) {
                redis.opsForZSet().remove(targetKey, missing.toArray());
            }
        });
        result.replaceAll((nodeId, connections) -> List.copyOf(connections));
        return Map.copyOf(result);
    }

    /** @return Connection Hash 键 */
    private @NotNull Key connectionKey(@NotNull ConnectionId id) {
        return names.connection(id);
    }

    /** @return Connection 当前全部目标索引键的 Set 键 */
    private @NotNull Key connectionTargetsKey(@NotNull ConnectionId id) {
        return names.connectionTargets(id);
    }

    /** @return Connection 的 STOMP subscription ID 到目标 Redis 键映射 Hash */
    private @NotNull Key connectionSubscriptionsKey(@NotNull ConnectionId id) {
        return names.connectionSubscriptions(id);
    }

    /** @return 不暴露 Resource、Topic 或身份原文的目标 SET 键 */
    private @NotNull Key targetKey(@NotNull WebRealtimeTarget target) {
        return names.target(target);
    }

    /** 从 Redis Hash 读取连接时间；旧 Web Realtime 记录缺少字段时使用当前时间。 */
    private @NotNull Instant instant(Object value) {
        return value == null ? clock.instant() : Instant.ofEpochMilli(Long.parseLong(value.toString()));
    }

    /** 从 classpath 加载一个返回 Long 的 Redis 原子脚本。 */
    private static @NotNull DefaultRedisScript<Long> script(@NotNull String fileName) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/web-realtime/" + fileName));
        script.setResultType(Long.class);
        return script;
    }
}
