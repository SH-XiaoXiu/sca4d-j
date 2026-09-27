package com.sca4d.core.spring.setting;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.exception.SettingReadOnlyException;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 按 owner 和规范编码读写 sys_setting，保留来源版本及敏感值用途绑定。 */
public class DatabaseSettingStore implements SettingReader<String>, SettingWriter<String> {
    private final SettingRepository repository;

    private final SecretKeyring keyring;

    private final VersionConditions versionConditions;

    /** 创建数据库读写实现，HTTP 条件版本由已有 VersionConditions 解释。 */
    public DatabaseSettingStore(
            @NotNull SettingRepository repository,
            @Nullable SecretKeyring keyring,
            @NotNull VersionConditions versionConditions) {
        this.repository = Objects.requireNonNull(repository);
        this.keyring = keyring;
        this.versionConditions = Objects.requireNonNull(versionConditions);
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("SETTING:DATABASE");
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull SettingValue<String> read(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
        String key = setting.format(NameFormat.COLON);
        StoredSetting stored = repository.findCanonical(owner, key).orElse(null);
        if (stored == null) {
            return SettingValue.read(code(), setting, owner, null, 0, new Version(0), false);
        }
        String value = null;
        if (stored.hasValue()) {
            value = stored.valueCiphertext() == null ? stored.valueText() : decrypt(owner, stored);
            if (value == null || stored.valueText() != null && stored.valueCiphertext() != null) {
                throw new IllegalStateException("设置持久化内容不完整或冲突: " + key);
            }
        }
        return SettingValue.read(
                code(),
                setting,
                owner,
                value,
                stored.schemaVersion(),
                stored.version(),
                stored.synchronizedFromEnvironment());
    }

    @Override
    @Transactional
    public void replace(
            @NotNull TreeCode setting,
            @NotNull SettingOwner owner,
            @NotNull String value,
            int codecVersion,
            boolean secret,
            @NotNull Version expected) {
        SettingValue<String> current = read(setting, owner);
        if (current.locked())
            throw new SettingReadOnlyException(setting.toString());
        VersionCondition condition = versionConditions.resolve(expected);
        EncodedSetting encoded = encode(setting, owner, value, secret);
        repository.replace(
                owner,
                setting.format(NameFormat.COLON),
                codecVersion,
                encoded.text(),
                encoded.keyId(),
                encoded.ciphertext(),
                encoded.encryptionContextVersion(),
                condition);
    }

    @Override
    @Transactional
    public void reset(
            @NotNull TreeCode setting,
            @NotNull SettingOwner owner,
            int codecVersion,
            @NotNull Version expected) {
        SettingValue<String> current = read(setting, owner);
        if (current.locked())
            throw new SettingReadOnlyException(setting.toString());
        VersionCondition condition = versionConditions.resolve(expected);
        if (expected.value() == 0) {
            if (current.revision().orElseThrow().value() != 0 || condition.expected().value() != 0) {
                AffectedRowCounts.requireSingle(0, condition.whenZero(), "清除设置覆盖值");
            }
            return;
        }
        repository.reset(owner, setting.format(NameFormat.COLON), codecVersion, condition);
    }

    @Override
    @Transactional
    public void erase(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
        repository.erase(owner, setting.format(NameFormat.COLON));
    }

    /** 用明确的设置身份编码敏感文本；格式转换已由领域 Codec 完成。 */
    EncodedSetting encode(TreeCode setting, SettingOwner owner, String text, boolean secret) {
        if (!secret)
            return new EncodedSetting(text, null, null, null);
        if (keyring == null)
            throw new IllegalStateException("敏感设置缺少 SecretKeyring");
        SecretCipher cipher = keyring.activeCipher();
        SettingSecretCodecCatalog codecs = new SettingSecretCodecCatalog(
                cipher,
                owner,
                setting.format(NameFormat.COLON));
        EncryptedSecret encrypted = codecs.current().encode(text.getBytes(StandardCharsets.UTF_8));
        return new EncodedSetting(null, encrypted.keyId(), encrypted.ciphertext(), codecs.current().version());
    }

    /** 使用数据库保存的原始编码和加密上下文版本解密，不借用当前 Codec 结构版本。 */
    String decrypt(SettingOwner owner, StoredSetting stored) {
        if (keyring == null || stored.valueKeyId() == null || stored.encryptionContextVersion() == null) {
            throw new IllegalStateException("敏感设置缺少解密信息");
        }
        SecretCipher cipher = keyring.findCipher(stored.valueKeyId())
                .orElseThrow(() -> new IllegalStateException("敏感设置引用未知密钥"));
        return new String(
                new SettingSecretCodecCatalog(cipher, owner, stored.key()).select(stored.encryptionContextVersion())
                        .decode(new EncryptedSecret(stored.valueKeyId(), stored.valueCiphertext())),
                StandardCharsets.UTF_8);
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
