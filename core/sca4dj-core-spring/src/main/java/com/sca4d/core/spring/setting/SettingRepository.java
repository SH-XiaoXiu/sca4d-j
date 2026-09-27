package com.sca4d.core.spring.setting;

import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 隔离 Settings 对 MyBatis、影响行数、条件写入和初始化锁 SQL 的依赖。
 */
public class SettingRepository {

    private final SettingMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    /**
     * 创建设置 Repository。
     */
    public SettingRepository(@NotNull SettingMapper mapper, @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "设置 Mapper 不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /**
     * 按键批量读取运行时 `Subject.SYSTEM` 当前设置行。
     */
    @CheckReturnValue
    public @NotNull Map<@NotNull String, @NotNull StoredSetting> findAllSystem(
            @NotNull Collection<@NotNull String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, StoredSetting> values = new LinkedHashMap<>();
        List<String> uniqueKeys = keys.stream().distinct().toList();
        SettingOwner owner = SettingOwner.system();
        for (StoredSetting value : sqlBatches.query(
                uniqueKeys,
                1,
                2,
                batch -> mapper.select(owner.type(), owner.id(), batch))) {
            if (values.putIfAbsent(value.key(), value) != null) {
                throw new IllegalStateException("数据库返回重复 SystemSetting: " + value.key());
            }
        }
        return Map.copyOf(values);
    }

    /**
     * 按 owner 和键读取一个设置行。
     */
    @CheckReturnValue
    public @NotNull Optional<StoredSetting> find(
            @NotNull SettingOwner owner,
            @NotNull String key) {
        SettingOwner checkedOwner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        return mapper.select(
                checkedOwner.type(),
                checkedOwner.id(),
                List.of(Objects.requireNonNull(key, "设置键不能为空"))).stream().findFirst();
    }

    /**
     * 只在 SystemSetting 不存在时写入初始化值。
     */
    public void initializeSystem(
            @NotNull String key,
            int schemaVersion,
            @Nullable String valueText,
            @Nullable String valueKeyId,
            byte @Nullable [] valueCiphertext,
            @Nullable Integer encryptionContextVersion) {
        AffectedRowCounts.requireSingle(
                mapper.insertManaged(
                        SettingOwner.system().type(),
                        SettingOwner.system().id(),
                        key,
                        schemaVersion,
                        valueText,
                        valueKeyId,
                        valueCiphertext,
                        encryptionContextVersion),
                ZeroRowPolicy.ALLOW,
                "初始化系统设置");
    }

    /**
     * 写入并标记初始化节点持续同步的 SystemSetting。
     */
    public void synchronizeSystem(
            @NotNull String key,
            int schemaVersion,
            @Nullable String valueText,
            @Nullable String valueKeyId,
            byte @Nullable [] valueCiphertext,
            @Nullable Integer encryptionContextVersion) {
        AffectedRowCounts.requireSingle(
                mapper.synchronizeSystem(
                        SettingOwner.system().type(),
                        SettingOwner.system().id(),
                        key,
                        schemaVersion,
                        valueText == null ? Patch.clear() : Patch.set(valueText),
                        valueKeyId == null ? Patch.clear() : Patch.set(valueKeyId),
                        valueCiphertext == null ? Patch.clear() : Patch.set(valueCiphertext),
                        encryptionContextVersion == null ? Patch.clear() : Patch.set(encryptionContextVersion)),
                ZeroRowPolicy.ERROR,
                "同步系统设置");
    }

    /**
     * 解除 SystemSetting 的 ENV 同步状态并保留最后一次值。
     */
    public void releaseSystemSynchronization(@NotNull String key) {
        AffectedRowCounts.requireSingle(
                mapper.releaseSystemSynchronization(SettingOwner.system().type(), SettingOwner.system().id(), key),
                ZeroRowPolicy.ALLOW,
                "解除系统设置同步");
    }

    /**
     * 按版本条件写入一个 owner 的完整覆盖值。
     */
    public void replace(
            @NotNull SettingOwner owner,
            @NotNull String key,
            int schemaVersion,
            @Nullable String valueText,
            @Nullable String valueKeyId,
            byte @Nullable [] valueCiphertext,
            @Nullable Integer encryptionContextVersion,
            @NotNull VersionCondition condition) {
        SettingOwner checkedOwner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        VersionCondition checkedCondition = Objects.requireNonNull(condition, "设置版本条件不能为空");
        int changed = checkedCondition.expected().value() == 0
                ? mapper.insertManaged(
                        checkedOwner.type(),
                        checkedOwner.id(),
                        key,
                        schemaVersion,
                        valueText,
                        valueKeyId,
                        valueCiphertext,
                        encryptionContextVersion)
                : mapper.updateManaged(
                        checkedOwner.type(),
                        checkedOwner.id(),
                        key,
                        Patch.set(schemaVersion),
                        Patch.set(true),
                        valueText == null ? Patch.clear() : Patch.set(valueText),
                        valueKeyId == null ? Patch.clear() : Patch.set(valueKeyId),
                        valueCiphertext == null ? Patch.clear() : Patch.set(valueCiphertext),
                        encryptionContextVersion == null ? Patch.clear() : Patch.set(encryptionContextVersion),
                        checkedCondition.expected().value());
        AffectedRowCounts.requireSingle(changed, checkedCondition.whenZero(), "替换设置 " + key);
    }

    /**
     * 按版本条件将已有 owner 设置行改为 tombstone。
     */
    public void reset(
            @NotNull SettingOwner owner,
            @NotNull String key,
            int schemaVersion,
            @NotNull VersionCondition condition) {
        SettingOwner checkedOwner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        VersionCondition checkedCondition = Objects.requireNonNull(condition, "设置版本条件不能为空");
        AffectedRowCounts.requireSingle(
                mapper.updateManaged(
                        checkedOwner.type(),
                        checkedOwner.id(),
                        key,
                        Patch.set(schemaVersion),
                        Patch.set(false),
                        Patch.clear(),
                        Patch.clear(),
                        Patch.clear(),
                        Patch.clear(),
                        checkedCondition.expected().value()),
                checkedCondition.whenZero(),
                "恢复设置默认值 " + key);
    }

    /**
     * owner 生命周期结束时物理删除指定设置行。
     */
    @AffectedRows(target = "删除 owner 设置", whenZero = ZeroRowPolicy.ALLOW)
    public void erase(@NotNull SettingOwner owner, @NotNull String key) {
        SettingOwner checkedOwner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        mapper.erase(checkedOwner.type(), checkedOwner.id(), Objects.requireNonNull(key, "设置键不能为空"));
    }

    /**
     * 取得当前事务持有的初始化互斥锁。
     */
    public void lockBootstrap() {
        if (!mapper.tryLockBootstrap()) {
            throw new IllegalStateException("已有其他初始化节点正在同步 Settings");
        }
    }

    /** 拒绝旧编码和归一冲突；只有确实不存在任何对应记录时才返回空。 */
    public @NotNull Optional<StoredSetting> findCanonical(@NotNull SettingOwner owner, @NotNull String code) {
        List<StoredSetting> values = mapper.selectCanonicalOrLegacy(owner.type(), owner.id(), code);
        if (values.size() > 1 || !values.isEmpty() && !values.getFirst().key().equals(code)) {
            throw new IllegalStateException("设置编码需要显式迁移或处理冲突: " + code);
        }
        return values.stream().findFirst();
    }

}
