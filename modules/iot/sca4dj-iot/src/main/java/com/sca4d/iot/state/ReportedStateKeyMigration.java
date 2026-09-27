package com.sca4d.iot.state;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 在旧实例停止写入后复制非过期 reported 状态到新 Key，保留原始数据供验证和恢复。 */
public final class ReportedStateKeyMigration {

    private static final Logger LOG = LoggerFactory.getLogger(ReportedStateKeyMigration.class);

    private final StringRedisTemplate legacy;

    private final RedisTemplate<Key, String> target;

    /** 使用同一基础设施的旧字符串模板和实际安装 Key 序列化器的新模板。 */
    public ReportedStateKeyMigration(@NotNull StringRedisTemplate legacy, @NotNull RedisTemplate<Key, String> target) {
        this.legacy = Objects.requireNonNull(legacy);
        this.target = Objects.requireNonNull(target);
    }

    /**
     * 校验全部源键和目标键，返回需要复制或已经一致的键数，不修改 Redis。
     *
     * @param oldAppName 旧版本 spring.application.name 原文；所有旧实例必须先停止写入
     * @throws IllegalStateException 源数据缺失、带 TTL 或目标包含不同内容时抛出
     */
    public int verify(@NotNull String oldAppName) {
        return transfer(oldAppName, false);
    }

    /**
     * 复制完整 Hash 与 Set，保留源键；重复执行只接受已经一致的目标，禁止覆盖不同数据。
     *
     * <p>必须先 verify 并停止新旧实例写入。中途失败保留已复制键，修复冲突后可以重新执行。
     */
    public int copy(@NotNull String oldAppName) {
        verify(oldAppName);
        int count = transfer(oldAppName, true);
        LOG.info("IoT reported 名称迁移复制完成，源数据保持保留 | keyCount={}", count);
        return count;
    }

    /** 按已知旧格式扫描模块自己的非过期键，目标名称由实际 Key 序列化器产生。 */
    private int transfer(@NotNull String oldAppName, boolean copy) {
        if (!Objects.requireNonNull(oldAppName).matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("旧 App 名称不合法，不能用于 Redis 扫描");
        }
        int count = 0;
        for (boolean group : new boolean[] {false, true}) {
            String prefix = oldAppName + ":sca4dj:iot:" + (group ? "reported-group:" : "reported-groups:");
            try (Cursor<String> keys = legacy.scan(ScanOptions.scanOptions().match(prefix + '*').count(128).build())) {
                while (keys.hasNext()) {
                    String oldKey = keys.next();
                    String[] values = oldKey.substring(prefix.length()).split(":", group ? 3 : 2);
                    if (values.length != (group ? 3 : 2)) {
                        throw new IllegalStateException("旧 reported key 不符合模块格式");
                    }
                    Key key = Key.of(
                            TreeCode.of(group ? "IOT:REPORTED_GROUP" : "IOT:REPORTED_GROUPS"),
                            Long.parseLong(values[0]),
                            Long.parseLong(values[1]));
                    if (group) {
                        key = key.part(values[2]);
                    }
                    byte[] snapshot = Objects.requireNonNull(legacy.dump(oldKey), "旧 reported 数据已消失，必须停止旧实例写入");
                    if (!Long.valueOf(-1).equals(legacy.getExpire(oldKey, TimeUnit.MILLISECONDS))) {
                        throw new IllegalStateException("reported 数据应为非过期数据，请人工检查后迁移");
                    }
                    String physical = KeyRedisSerializer.keyText(target, key);
                    byte[] existing = legacy.dump(physical);
                    boolean equal = existing == null
                            || (Objects.equals(legacy.type(oldKey), legacy.type(physical))
                                    && (group
                                            ? legacy.opsForHash()
                                                    .entries(oldKey)
                                                    .equals(legacy.opsForHash().entries(physical))
                                            : Objects.equals(
                                                    legacy.opsForSet().members(oldKey),
                                                    legacy.opsForSet().members(physical))));
                    if (!equal) {
                        throw new IllegalStateException("reported 目标名称已经包含不同数据，禁止覆盖，请人工检查");
                    }
                    if (copy && existing == null) {
                        legacy.restore(physical, snapshot, 0, TimeUnit.MILLISECONDS, false);
                    }
                    count++;
                }
            }
        }
        return count;
    }
}
