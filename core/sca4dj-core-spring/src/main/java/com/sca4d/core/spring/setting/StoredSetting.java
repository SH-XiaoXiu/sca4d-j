package com.sca4d.core.spring.setting;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 保存 Repository 从 `sys_setting` 读取的 owner、结构版本、值和行版本。
 */
public class StoredSetting {

    private final Integer encryptionContextVersion;

    private final SettingOwner owner;

    private final String key;

    private final String valueKeyId;

    private final String valueText;

    private final Version version;

    private final boolean hasValue;

    private final boolean synchronizedFromEnvironment;

    private final byte[] valueCiphertext;

    private final int schemaVersion;

    /**
     * 创建数据库设置行并校验 tombstone、文本和密文形状。
     *
     * @param ownerType                   owner 类型编码，单段时按 TreeCode 段解析，否则按完整编码解析
     * @param ownerId                     owner 类型内标识
     * @param key                         设置键
     * @param schemaVersion               值结构版本
     * @param hasValue                    是否保存显式覆盖值
     * @param valueText                   普通设置文本
     * @param valueKeyId                  敏感设置密钥标识
     * @param valueCiphertext             敏感设置密文
     * @param encryptionContextVersion    密文加密用途版本
     * @param synchronizedFromEnvironment 是否由初始化节点持续同步
     * @param version                     行乐观锁版本
     */
    public StoredSetting(
            @NotNull String ownerType,
            @NotNull String ownerId,
            @NotNull String key,
            int schemaVersion,
            boolean hasValue,
            @Nullable String valueText,
            @Nullable String valueKeyId,
            byte @Nullable [] valueCiphertext,
            @Nullable Integer encryptionContextVersion,
            boolean synchronizedFromEnvironment,
            long version) {
        this.owner = new SettingOwner(
                ownerType.indexOf('/') < 0 && ownerType.indexOf(':') < 0
                        ? TreeCode.ofSegment(ownerType)
                        : TreeCode.of(ownerType),
                ownerId);
        this.key = Objects.requireNonNull(key, "设置键不能为空");
        if (schemaVersion < 1) {
            throw new IllegalStateException("数据库设置结构版本必须为正数: " + key);
        }
        this.schemaVersion = schemaVersion;
        this.hasValue = hasValue;
        this.valueText = valueText;
        this.valueKeyId = valueKeyId;
        this.valueCiphertext = valueCiphertext == null ? null : valueCiphertext.clone();
        this.encryptionContextVersion = encryptionContextVersion;
        this.synchronizedFromEnvironment = synchronizedFromEnvironment;
        this.version = new Version(version);
        validateValueShape();
    }

    /**
     * @return 当前行 owner
     */
    public @NotNull SettingOwner owner() {
        return owner;
    }

    /**
     * @return 设置键
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * @return 保存值使用的结构版本
     */
    public int schemaVersion() {
        return schemaVersion;
    }

    /**
     * @return 当前行包含显式覆盖值时返回 {@code true}
     */
    public boolean hasValue() {
        return hasValue;
    }

    /**
     * @return 非敏感设置文本；敏感值或 tombstone 返回空
     */
    public @Nullable String valueText() {
        return valueText;
    }

    /**
     * @return 敏感设置密钥标识；普通值或 tombstone 返回空
     */
    public @Nullable String valueKeyId() {
        return valueKeyId;
    }

    /**
     * @return 敏感设置密文副本；普通值或 tombstone 返回空
     */
    public byte @Nullable [] valueCiphertext() {
        return valueCiphertext == null ? null : valueCiphertext.clone();
    }

    /**
     * @return 密文使用的加密用途版本；普通值或 tombstone 返回空
     */
    public @Nullable Integer encryptionContextVersion() {
        return encryptionContextVersion;
    }

    /**
     * @return 由初始化节点持续同步时返回 {@code true}
     */
    public boolean synchronizedFromEnvironment() {
        return synchronizedFromEnvironment;
    }

    /**
     * @return 当前行乐观锁版本
     */
    public @NotNull Version version() {
        return version;
    }

    /**
     * 校验数据库行只能表达 tombstone、普通文本或完整密文。
     */
    private void validateValueShape() {
        if (!hasValue) {
            if (valueText != null
                    || valueKeyId != null
                    || valueCiphertext != null
                    || encryptionContextVersion != null
                    || synchronizedFromEnvironment) {
                throw new IllegalStateException("设置 tombstone 不能保存值或 ENV 状态: " + key);
            }
            return;
        }
        boolean text = valueText != null
                && valueKeyId == null
                && valueCiphertext == null
                && encryptionContextVersion == null;
        boolean encrypted = valueText == null
                && valueKeyId != null
                && valueCiphertext != null
                && encryptionContextVersion != null;
        if (text == encrypted) {
            throw new IllegalStateException("设置必须只保存文本或完整密文: " + key);
        }
    }
}
