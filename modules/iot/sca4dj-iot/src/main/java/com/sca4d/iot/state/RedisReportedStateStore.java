package com.sca4d.iot.state;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.query.ReportedStateSnapshot;
import com.sca4d.iot.api.state.ReportedStateStore;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.api.state.ReportedStateWriteResult;
import com.sca4d.iot.model.ThingDataJsonCodec;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 使用 Redis Hash 和 Lua 原子维护每个 Device 字段组的 latest reported 投影。 */
public class RedisReportedStateStore implements ReportedStateStore {

    private static final String VALUE_PREFIX = "value:";

    private static final DefaultRedisScript<String> UPDATE = updateScript();

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull ThingDataJsonCodec json;

    private final @NotNull Key groupPrefix;

    private final @NotNull Key indexPrefix;

    private final @NotNull RedisBatches redisBatches;

    /**
     * 创建不设置有效期的 Redis latest reported 存储。
     *
     * @param redis IoT 共享 Redis
     * @param json 物模型数据 JSON 编解码器
     * @param redisBatches Redis pipeline 批处理器
     */
    public RedisReportedStateStore(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull ThingDataJsonCodec json,
            @NotNull RedisBatches redisBatches) {
        this.redis = Objects.requireNonNull(redis, "Reported Redis 不能为空");
        this.json = Objects.requireNonNull(json, "Thing data JSON Codec 不能为空");

        this.groupPrefix = Key.of(TreeCode.ofColon("IOT:REPORTED_GROUP"));
        this.indexPrefix = Key.of(TreeCode.ofColon("IOT:REPORTED_GROUPS"));
        this.redisBatches = Objects.requireNonNull(redisBatches, "Redis 批处理器不能为空");
    }

    /** Lua 在同一 Redis 操作内完成顺序比较、重复检查、字段合并和 version 递增。 */
    @Override
    public @NotNull ReportedStateWriteResult update(
            long deviceId,
            long bindingGeneration,
            @NotNull ReportedStateUpdate update,
            @NotNull Map<String, Object> normalizedValues,
            @NotNull String orderScope,
            @NotNull String orderValue,
            @NotNull Instant receivedAt) {
        // 按固定顺序编码 Lua 参数，确保相同 update 得到相同的字段覆盖和写入顺序。
        ReportedStateUpdate input = Objects.requireNonNull(update, "reported update 不能为空");
        Map<String, Object> normalized = Map.copyOf(
                Objects.requireNonNull(normalizedValues, "reported 规范值不能为空"));
        List<String> arguments = new ArrayList<>();
        arguments.add(input.groupCode());
        arguments.add(orderScope);
        arguments.add(orderValue);
        arguments.add(input.mergeMode().name());
        arguments.add(Long.toString(input.observedAt().toEpochMilli()));
        arguments.add(Long.toString(receivedAt.toEpochMilli()));
        List<String> covered = input.coveredProperties().stream().sorted().toList();
        arguments.add(Integer.toString(covered.size()));
        arguments.addAll(covered);
        List<Map.Entry<String, Object>> values = normalized.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        arguments.add(Integer.toString(values.size()));
        values.forEach(entry -> {
            arguments.add(entry.getKey());
            arguments.add(json.encodeValue(entry.getValue()));
        });

        // Lua 原子比较消息顺序、合并字段并递增版本，Java 只解释最终写入结果。
        String result = Objects.requireNonNull(
                redis.execute(
                        UPDATE,
                        List.of(
                                groupKey(deviceId, bindingGeneration, input.groupCode()),
                                indexKey(deviceId, bindingGeneration)),
                        arguments.toArray()),
                "Reported Redis Lua 没有返回结果");
        if (result.startsWith("CONFLICT")) {
            throw new BusinessException(IotErrorCode.PROTOCOL_CONFLICT);
        }

        // 把 Lua 返回的 outcome:version 恢复成调用方使用的写入结果。
        int separator = result.indexOf(':');
        if (separator < 1) {
            throw new IllegalStateException("Reported Redis Lua 返回了无法识别的结果");
        }
        ReportedStateWriteResult.Outcome outcome = ReportedStateWriteResult.Outcome.valueOf(
                result.substring(0, separator));
        long version = Long.parseLong(result.substring(separator + 1));
        return new ReportedStateWriteResult(outcome, version);
    }

    /** 读取索引列出的全部字段组，并忽略已经不存在的 Hash。 */
    @Override
    public @NotNull List<ReportedStateSnapshot> find(long deviceId, long bindingGeneration) {
        Set<String> groups = redis.opsForSet().members(indexKey(deviceId, bindingGeneration));
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        List<String> sortedGroups = groups.stream().sorted().toList();
        List<Object> hashes = pipelineHashes(
                sortedGroups.stream()
                        .map(groupCode -> groupKey(deviceId, bindingGeneration, groupCode))
                        .toList());
        List<ReportedStateSnapshot> result = new ArrayList<>();
        List<String> missingGroups = new ArrayList<>();

        // 按索引排序逐一恢复 pipeline 返回的 Hash；缺失 Hash 延后批量清理索引成员。
        for (int index = 0; index < sortedGroups.size(); index++) {
            String groupCode = sortedGroups.get(index);
            Map<Object, Object> fields = redisHash(hashes.get(index));
            if (fields.isEmpty()) {
                missingGroups.add(groupCode);
                continue;
            }
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            fields.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().toString().startsWith(VALUE_PREFIX))
                    .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .forEach(
                            entry -> values.put(
                                    entry.getKey().toString().substring(VALUE_PREFIX.length()),
                                    json.decodeValue(entry.getValue().toString())));
            result.add(
                    new ReportedStateSnapshot(
                            groupCode,
                            values,
                            longField(fields, "version"),
                            Instant.ofEpochMilli(longField(fields, "observedAtMillis")),
                            Instant.ofEpochMilli(longField(fields, "receivedAtMillis"))));
        }
        if (!missingGroups.isEmpty()) {
            pipelineRemoveSetMembers(indexKey(deviceId, bindingGeneration), missingGroups);
        }
        return List.copyOf(result);
    }

    /** 按指定绑定代数构造 Redis key，各批使用一次 pipeline 读取同一字段组。 */
    @Override
    public @NotNull Map<Long, ReportedStateSnapshot> find(
            @NotNull Map<Long, Long> bindingGenerations,
            @NotNull String groupCode) {
        List<Map.Entry<Long, Long>> bindings = List.copyOf(Map.copyOf(bindingGenerations).entrySet());
        Map<Long, ReportedStateSnapshot> snapshots = new LinkedHashMap<>();
        redisBatches.forEach(bindings, 1, batch -> {
            List<Object> hashes = pipelineHashes(
                    batch.stream()
                            .map(binding -> groupKey(binding.getKey(), binding.getValue(), groupCode))
                            .toList());
            for (int index = 0; index < batch.size(); index++) {
                Map<Object, Object> fields = redisHash(hashes.get(index));
                if (fields.isEmpty()) {
                    continue;
                }
                Map<String, Object> values = new LinkedHashMap<>();
                fields.entrySet()
                        .stream()
                        .filter(entry -> entry.getKey().toString().startsWith(VALUE_PREFIX))
                        .forEach(
                                entry -> values.put(
                                        entry.getKey().toString().substring(VALUE_PREFIX.length()),
                                        json.decodeValue(entry.getValue().toString())));
                snapshots.put(
                        batch.get(index).getKey(),
                        new ReportedStateSnapshot(
                                groupCode,
                                values,
                                longField(fields, "version"),
                                Instant.ofEpochMilli(longField(fields, "observedAtMillis")),
                                Instant.ofEpochMilli(longField(fields, "receivedAtMillis"))));
            }
        });
        return Map.copyOf(snapshots);
    }

    /** 删除 Device 指定旧绑定的字段组和索引。 */
    @Override
    public void delete(long deviceId, long bindingGeneration) {
        Key indexKey = indexKey(deviceId, bindingGeneration);
        Set<String> groups = redis.opsForSet().members(indexKey);
        List<Key> keys = new ArrayList<>();
        keys.add(indexKey);
        if (groups != null) {
            for (String group : groups) {
                keys.add(groupKey(deviceId, bindingGeneration, group));
            }
        }
        pipelineDelete(keys);
    }

    /** 使用一次 Redis pipeline 读取当前批次全部 Hash。 */
    private @NotNull List<Object> pipelineHashes(@NotNull List<Key> keys) {
        return redisBatches.query(keys, 1, batch -> redis.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                for (Key key : batch) {
                    operations.opsForHash().entries((K) key);
                }
                return null;
            }
        }));
    }

    /** 使用一次 Redis pipeline 清理已经没有对应 Hash 的字段组索引成员。 */
    private void pipelineRemoveSetMembers(@NotNull Key key, @NotNull List<String> members) {
        redisBatches.forEach(members, 1, batch -> redis.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                operations.opsForSet().remove((K) key, batch.toArray());
                return null;
            }
        }));
    }

    /** 使用一次 Redis pipeline 删除旧绑定索引和全部字段组 Hash。 */
    private void pipelineDelete(@NotNull List<Key> keys) {
        redisBatches.forEach(keys, 1, batch -> redis.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
                operations.delete(batch.stream().map(value -> (K) value).toList());
                return null;
            }
        }));
    }

    /** 把 pipeline 返回值恢复为字符串 Hash。 */
    @SuppressWarnings("unchecked")
    private static @NotNull Map<Object, Object> redisHash(@NotNull Object value) {
        if (!(value instanceof Map<?, ?> fields)) {
            throw new IllegalStateException("Reported Redis pipeline 返回了无法识别的 Hash");
        }
        return (Map<Object, Object>) fields;
    }

    /** 返回必需 Redis Hash 字段的 long 值。 */
    private static long longField(@NotNull Map<Object, Object> fields, @NotNull String name) {
        Object value = fields.get(name);
        if (value == null) {
            throw new IllegalStateException("Reported Redis 数据缺少字段: " + name);
        }
        return Long.parseLong(value.toString());
    }

    /** 返回一个字段组的 Redis Hash key。 */
    private @NotNull Key groupKey(long deviceId, long bindingGeneration, @NotNull String groupCode) {
        return groupPrefix.part(deviceId).part(bindingGeneration).part(groupCode);
    }

    /** 返回一个 Device 绑定下的字段组索引 key。 */
    private @NotNull Key indexKey(long deviceId, long bindingGeneration) {
        return indexPrefix.part(deviceId).part(bindingGeneration);
    }

    /** 从 classpath 加载原子更新 latest reported 的 Lua 脚本。 */
    private static @NotNull DefaultRedisScript<String> updateScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/iot/reported/update.lua"));
        script.setResultType(String.class);
        return script;
    }
}
