package com.sca4d.core.spring.setting;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.LogicSetting;
import com.sca4d.core.api.setting.PreferenceSetting;
import com.sca4d.core.api.setting.PreferenceSettings;
import com.sca4d.core.api.setting.Setting;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.SettingReadOnlyException;
import com.sca4d.core.api.setting.SettingSchemaVersionException;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.api.setting.SettingValues;
import com.sca4d.core.api.setting.Settings;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/**
 * 每次调用都查询统一 `sys_setting`，并处理 owner、结构版本、Pipeline 和敏感值。
 * 敏感值写入使用 V2 `SecretCodec`，读取根据行中的加密用途版本选择 V1 或 V2 `SecretCodec`。
 */
public class DatabaseSettings implements Settings, PreferenceSettings {

    private static final Version EMPTY_VERSION = new Version(0);

    private final SettingCatalog catalog;

    private final SettingRepository repository;

    private final SecretKeyring keyring;

    private final VersionConditions versionConditions;

    /**
     * 创建数据库 Settings。
     *
     * @param catalog           当前应用设置目录
     * @param repository        设置持久化边界
     * @param keyring           敏感设置使用的密钥环；应用没有安装密钥环时为空
     * @param versionConditions 为 HTTP 和内部写入选择版本条件
     */
    public DatabaseSettings(
            @NotNull SettingCatalog catalog,
            @NotNull SettingRepository repository,
            @Nullable SecretKeyring keyring,
            @NotNull VersionConditions versionConditions) {
        this.catalog = Objects.requireNonNull(catalog, "设置目录不能为空");
        this.repository = Objects.requireNonNull(repository, "设置 Repository 不能为空");
        this.keyring = keyring;
        this.versionConditions = Objects.requireNonNull(versionConditions, "设置版本条件不能为空");
    }

    @Override
    @Transactional(readOnly = true)
    public <T> @NotNull T get(@NotNull SystemSetting<T> setting) {
        return inspect(setting).value();
    }

    @Override
    @Transactional(readOnly = true)
    public <T> @NotNull SettingValue<T> inspect(@NotNull SystemSetting<T> setting) {
        return inspectValue(registered(setting), SettingOwner.system());
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull SettingValues get(@NotNull Collection<? extends SystemSetting<?>> settings) {
        LinkedHashSet<SystemSetting<?>> requested = new LinkedHashSet<>();
        for (SystemSetting<?> setting : Objects.requireNonNull(settings, "设置定义不能为空")) {
            catalog.assertRegistered(setting);
            requested.add(setting);
        }
        Map<String, StoredSetting> stored = repository.findAllSystem(
                requested.stream().map(Setting::key).toList());
        Collection<SettingValue<?>> values = new ArrayList<>(requested.size());
        for (SystemSetting<?> setting : requested) {
            StoredSetting storedValue = stored.get(setting.key());
            values.add(
                    storedValue == null
                            ? defaultValue(setting, SettingOwner.system(), EMPTY_VERSION)
                            : value(setting, SettingOwner.system(), storedValue));
        }
        return new SettingValues(values);
    }

    @Override
    @Transactional(readOnly = true)
    public <T> @NotNull T get(@NotNull LogicSetting<T> setting, @NotNull SettingOwner owner) {
        return inspect(setting, owner).value();
    }

    @Override
    @Transactional(readOnly = true)
    public <T> @NotNull SettingValue<T> inspect(
            @NotNull LogicSetting<T> setting,
            @NotNull SettingOwner owner) {
        return inspectValue(registered(setting), owner);
    }

    @Override
    @Transactional(readOnly = true)
    public <T> @NotNull SettingValue<T> inspect(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner) {
        return inspectValue(registered(setting), owner);
    }

    @Override
    @Transactional
    public <T> @NotNull SettingValue<T> replace(
            @NotNull SystemSetting<T> setting,
            @NotNull T value) {
        return replaceValue(registered(setting), SettingOwner.system(), value);
    }

    @Override
    @Transactional
    public <T> @NotNull SettingValue<T> replace(
            @NotNull LogicSetting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull T value) {
        return replaceValue(registered(setting), owner, value);
    }

    @Override
    @Transactional
    public <T> @NotNull SettingValue<T> replace(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull T value) {
        return replaceValue(registered(setting), owner, value);
    }

    @Override
    @Transactional
    public @NotNull SettingValue<?> reset(@NotNull SystemSetting<?> setting) {
        return resetValue(registered(setting), SettingOwner.system());
    }

    @Override
    @Transactional
    public @NotNull SettingValue<?> reset(
            @NotNull LogicSetting<?> setting,
            @NotNull SettingOwner owner) {
        return resetValue(registered(setting), owner);
    }

    @Override
    @Transactional
    public @NotNull SettingValue<?> reset(
            @NotNull PreferenceSetting<?> setting,
            @NotNull SettingOwner owner) {
        return resetValue(registered(setting), owner);
    }

    @Override
    @Transactional
    public void erase(@NotNull LogicSetting<?> setting, @NotNull SettingOwner owner) {
        repository.erase(Objects.requireNonNull(owner, "设置 owner 不能为空"), registered(setting).key());
    }

    @Override
    @Transactional
    public void erase(@NotNull PreferenceSetting<?> setting, @NotNull SettingOwner owner) {
        repository.erase(Objects.requireNonNull(owner, "设置 owner 不能为空"), registered(setting).key());
    }

    /**
     * 把规范化后的设置值编码为数据库文本或绑定 owner 的 V2 密文。
     */
    EncodedSetting encode(@NotNull Setting<?> setting, @NotNull SettingOwner owner, @NotNull Object value) {
        String text = encodeValue(setting, value);
        if (!setting.secret()) {
            return new EncodedSetting(text, null, null, null);
        }
        SecretCipher cipher = keyring(setting.key()).activeCipher();
        SettingSecretCodecCatalog codecs = new SettingSecretCodecCatalog(cipher, owner, setting.key());
        EncryptedSecret encrypted = codecs.current().encode(text.getBytes(StandardCharsets.UTF_8));
        return new EncodedSetting(
                null,
                encrypted.keyId(),
                encrypted.ciphertext(),
                codecs.current().version());
    }

    /**
     * 完整替换一个 owner 设置并回读新版本。
     */
    private <T> @NotNull SettingValue<T> replaceValue(
            @NotNull Setting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull T value) {
        SettingOwner checkedOwner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        SettingValue<T> current = inspectValue(setting, checkedOwner);
        VersionCondition condition = versionConditions.resolve(current.version());
        if (current.synchronizedFromEnvironment()) {
            throw new SettingReadOnlyException(setting.key());
        }
        EncodedSetting encoded = encode(
                setting,
                checkedOwner,
                Objects.requireNonNull(value, "设置值不能为空"));
        repository.replace(
                checkedOwner,
                setting.key(),
                setting.schemaVersion(),
                encoded.text(),
                encoded.keyId(),
                encoded.ciphertext(),
                encoded.encryptionContextVersion(),
                condition);
        return inspectValue(setting, checkedOwner);
    }

    /**
     * 将一个 owner 的已有设置行改为 tombstone 并回读默认值。
     */
    private <T> @NotNull SettingValue<T> resetValue(
            @NotNull Setting<T> setting,
            @NotNull SettingOwner owner) {
        SettingOwner checkedOwner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        SettingValue<T> current = inspectValue(setting, checkedOwner);
        VersionCondition condition = versionConditions.resolve(current.version());
        if (current.synchronizedFromEnvironment()) {
            throw new SettingReadOnlyException(setting.key());
        }
        if (current.version().equals(EMPTY_VERSION)) {
            if (!condition.expected().equals(EMPTY_VERSION)) {
                AffectedRowCounts.requireSingle(0, condition.whenZero(), "恢复设置默认值 " + setting.key());
            }
            return current;
        }
        repository.reset(checkedOwner, setting.key(), setting.schemaVersion(), condition);
        return inspectValue(setting, checkedOwner);
    }

    /**
     * 读取数据库行或构造没有行的默认值。
     */
    private <T> @NotNull SettingValue<T> inspectValue(
            @NotNull Setting<T> setting,
            @NotNull SettingOwner owner) {
        SettingOwner checkedOwner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        return repository.find(checkedOwner, setting.key())
                .map(stored -> value(setting, checkedOwner, stored))
                .orElseGet(() -> defaultValue(setting, checkedOwner, EMPTY_VERSION));
    }

    /**
     * 把数据库文本、密文或 tombstone 转换为类型化设置值。
     */
    private <T> @NotNull SettingValue<T> value(
            @NotNull Setting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull StoredSetting stored) {
        if (stored.schemaVersion() != setting.schemaVersion()) {
            throw new SettingSchemaVersionException(
                    setting.key(),
                    stored.schemaVersion(),
                    setting.schemaVersion());
        }
        if (!stored.hasValue()) {
            return defaultValue(setting, owner, stored.version());
        }
        String text;
        if (setting.secret()) {
            text = decrypt(setting, owner, stored);
        } else {
            text = stored.valueText();
            if (text == null || stored.valueKeyId() != null || stored.valueCiphertext() != null) {
                throw new IllegalStateException("普通设置没有保存文本值: " + setting.key());
            }
        }
        try {
            T decoded = setting.codec().decode(text);
            T normalized = setting.pipeline().run(decoded);
            return new SettingValue<>(
                    setting,
                    owner,
                    normalized,
                    true,
                    stored.synchronizedFromEnvironment(),
                    stored.version());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("数据库设置值不合法: " + setting.key(), exception);
        }
    }

    /**
     * 按数据库保存的加密用途版本解密敏感设置；V1 只用于读取历史密文。
     */
    private <T> @NotNull String decrypt(
            @NotNull Setting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull StoredSetting stored) {
        String keyId = stored.valueKeyId();
        byte[] ciphertext = stored.valueCiphertext();
        Integer contextVersion = stored.encryptionContextVersion();
        if (keyId == null || ciphertext == null || contextVersion == null || stored.valueText() != null) {
            throw new IllegalStateException("敏感设置没有保存完整密文: " + setting.key());
        }
        SecretCipher cipher = keyring(setting.key()).findCipher(keyId)
                .orElseThrow(() -> new IllegalStateException("敏感设置引用未知密钥: " + setting.key() + "." + keyId));
        SettingSecretCodecCatalog codecs = new SettingSecretCodecCatalog(cipher, owner, setting.key());
        return new String(
                codecs.select(contextVersion).decode(new EncryptedSecret(keyId, ciphertext)),
                StandardCharsets.UTF_8);
    }

    /**
     * 创建数据库不存在或 tombstone 使用的默认值。
     */
    private static <T> @NotNull SettingValue<T> defaultValue(
            @NotNull Setting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull Version version) {
        return new SettingValue<>(setting, owner, setting.defaultValue(), false, false, version);
    }

    /**
     * 使用设置自身 Pipeline 和 Codec 校验并编码未知泛型值。
     */
    @SuppressWarnings("unchecked")
    private static <T> @NotNull String encodeValue(@NotNull Setting<T> setting, @NotNull Object value) {
        try {
            T normalized = setting.pipeline().run((T) value);
            return setting.codec().encode(normalized);
        } catch (ClassCastException exception) {
            throw new IllegalArgumentException("设置值类型不匹配: " + setting.key(), exception);
        }
    }

    /**
     * 校验设置属于当前应用并恢复泛型。
     */
    private <T, S extends Setting<T>> @NotNull S registered(@NotNull S setting) {
        catalog.assertRegistered(setting);
        return setting;
    }

    /**
     * 返回敏感设置所需密钥环。
     */
    private @NotNull SecretKeyring keyring(@NotNull String key) {
        if (keyring == null) {
            throw new IllegalStateException("应用注册了敏感设置但没有 SecretKeyring: " + key);
        }
        return keyring;
    }

    /**
     * 保存一个设置编码后互斥的文本或密文字段。
     */
    @SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
    static final class EncodedSetting {
        private final String text;

        private final String keyId;

        private final byte[] ciphertext;

        private final Integer encryptionContextVersion;

        /**
         * 创建已编码设置。
         */
        EncodedSetting(
                @Nullable String text,
                @Nullable String keyId,
                byte @Nullable [] ciphertext,
                @Nullable Integer encryptionContextVersion) {
            this.text = text;
            this.keyId = keyId;
            this.ciphertext = ciphertext == null ? null : ciphertext.clone();
            this.encryptionContextVersion = encryptionContextVersion;
            boolean plain = text != null && keyId == null && ciphertext == null && encryptionContextVersion == null;
            boolean encrypted = text == null && keyId != null && ciphertext != null && encryptionContextVersion != null;
            if (plain == encrypted) {
                throw new IllegalArgumentException("设置编码必须只包含文本或完整密文");
            }
        }

        /**
         * @return 普通设置文本
         */
        @Nullable
        String text() {
            return text;
        }

        /**
         * @return 敏感设置密钥标识
         */
        @Nullable
        String keyId() {
            return keyId;
        }

        /**
         * @return 敏感设置密文副本
         */
        byte @Nullable [] ciphertext() {
            return ciphertext == null ? null : ciphertext.clone();
        }

        /**
         * @return 敏感设置加密用途版本
         */
        @Nullable
        Integer encryptionContextVersion() {
            return encryptionContextVersion;
        }
    }
}
