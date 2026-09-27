package com.sca4d.core.api.codec;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 按相邻版本步骤串联执行手工序列化迁移，不访问任何持久化介质。 */
public final class VersionedCodecMigrator<W> {

    private final @NotNull Map<Integer, @NotNull Migratable<W>> steps;

    /**
     * 创建前向迁移链。
     *
     * @param steps 每个源版本对应一个相邻版本迁移步骤
     */
    public VersionedCodecMigrator(@NotNull Collection<? extends @NotNull Migratable<W>> steps) {
        Map<Integer, Migratable<W>> indexed = new LinkedHashMap<>();
        for (Migratable<W> step : steps) {
            if (step.sourceVersion() <= 0
                    || step.targetVersion() != step.sourceVersion() + 1) {
                throw new IllegalArgumentException("迁移步骤必须只跨越一个相邻的正数版本");
            }
            if (indexed.putIfAbsent(step.sourceVersion(), step) != null) {
                throw new IllegalArgumentException("重复的迁移源版本: " + step.sourceVersion());
            }
        }
        this.steps = Map.copyOf(indexed);
    }

    /**
     * 沿完整的相邻版本链迁移外部表示。
     *
     * @param sourceVersion 源版本
     * @param sourceWire 源外部表示
     * @param targetVersion 目标版本
     * @return 目标版本外部表示
     */
    public @NotNull W migrate(
            int sourceVersion,
            @NotNull W sourceWire,
            int targetVersion) {
        if (sourceVersion <= 0 || targetVersion <= 0 || sourceVersion > targetVersion) {
            throw new IllegalArgumentException("迁移版本必须为正数且目标版本不能低于源版本");
        }
        W current = sourceWire;
        for (int version = sourceVersion; version < targetVersion; version++) {
            Migratable<W> step = steps.get(version);
            if (step == null) {
                throw new IllegalArgumentException(
                        "缺少迁移步骤: V" + version + " -> V" + (version + 1));
            }
            current = step.migrate(current);
        }
        return current;
    }
}
