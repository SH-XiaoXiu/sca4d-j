package com.sca4d.core.spring.setting;

import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * 由指定初始化节点按每个设置的 INIT 或 SYNC 标记更新数据库。
 */
public class SettingBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(SettingBootstrap.class);

    /**
     * 区分设置初始化标记未声明、启用和明确禁用三种状态。
     */
    private enum BootstrapFlag {
        /**
         * 环境中没有声明标记。
         */
        ABSENT,
        /**
         * 环境标记值为 {@code true}。
         */
        ENABLED,
        /**
         * 环境标记值为 {@code false}。
         */
        DISABLED
    }

    private final SettingCatalog catalog;

    private final SettingRepository repository;

    private final SecretKeyring keyring;

    private final Environment environment;

    /**
     * 创建 Settings ENV 初始化器。
     */
    public SettingBootstrap(
            @NotNull SettingCatalog catalog,
            @NotNull SettingRepository repository,
            @Nullable SecretKeyring keyring,
            @NotNull Environment environment) {
        this.catalog = Objects.requireNonNull(catalog, "设置目录不能为空");
        this.repository = Objects.requireNonNull(repository, "设置 Repository 不能为空");
        this.keyring = keyring;
        this.environment = Objects.requireNonNull(environment, "Spring Environment 不能为空");
    }

    /**
     * 在一个事务中取得互斥锁并应用全部 Settings ENV；任何标记或值非法时全部回滚。
     */
    @Transactional
    public void initialize() {
        repository.lockBootstrap();
        int initialized = 0;
        int synchronizedCount = 0;
        int released = 0;
        for (SystemSetting<?> setting : catalog.systemSettings()) {
            String variable = setting.environmentVariable();
            String rawValue = environment.getProperty(variable);
            BootstrapFlag initialize = flag(variable + "_INIT");
            BootstrapFlag synchronize = flag(variable + "_SYNC");

            if (initialize == BootstrapFlag.ENABLED && synchronize != BootstrapFlag.ABSENT) {
                throw new IllegalStateException("设置 INIT 不能与 SYNC 同时声明: " + variable);
            }
            if (initialize == BootstrapFlag.DISABLED) {
                throw new IllegalStateException("设置 INIT 只接受 true；不初始化时应删除标记: " + variable);
            }
            if (initialize == BootstrapFlag.ENABLED) {
                DatabaseSettings.EncodedSetting encoded = encoded(setting, requiredValue(variable, rawValue));
                repository.initializeSystem(
                        setting.key(),
                        setting.schemaVersion(),
                        encoded.text(),
                        encoded.keyId(),
                        encoded.ciphertext(),
                        encoded.encryptionContextVersion());
                initialized++;
                continue;
            }
            if (synchronize == BootstrapFlag.ENABLED) {
                DatabaseSettings.EncodedSetting encoded = encoded(setting, requiredValue(variable, rawValue));
                repository.synchronizeSystem(
                        setting.key(),
                        setting.schemaVersion(),
                        encoded.text(),
                        encoded.keyId(),
                        encoded.ciphertext(),
                        encoded.encryptionContextVersion());
                synchronizedCount++;
                continue;
            }
            if (synchronize == BootstrapFlag.DISABLED) {
                repository.releaseSystemSynchronization(setting.key());
                released++;
                continue;
            }
            if (rawValue != null) {
                throw new IllegalStateException("设置 ENV 缺少 _INIT=true 或 _SYNC=true 标记: " + variable);
            }
        }
        LOG.info(
                "设置初始化完成 | initialized={} synchronized={} released={}",
                initialized,
                synchronizedCount,
                released);
    }

    /**
     * 读取严格布尔标记；未声明时返回 {@link BootstrapFlag#ABSENT}。
     */
    private @NotNull BootstrapFlag flag(@NotNull String name) {
        String value = environment.getProperty(name);
        if (value == null) {
            return BootstrapFlag.ABSENT;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> BootstrapFlag.ENABLED;
            case "false" -> BootstrapFlag.DISABLED;
            default -> throw new IllegalStateException("Settings ENV 标记只接受 true 或 false: " + name);
        };
    }

    /**
     * 返回标记要求的非空设置文本。
     */
    private static @NotNull String requiredValue(@NotNull String variable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Settings ENV 标记缺少值: " + variable);
        }
        return value;
    }

    /**
     * 使用设置编解码器校验 ENV，并为敏感值生成 V2 持久化密文。
     */
    private <T> DatabaseSettings.@NotNull EncodedSetting encoded(
            @NotNull SystemSetting<T> setting,
            @NotNull String rawValue) {
        T value;
        try {
            value = setting.codec().decode(rawValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Settings ENV 值不合法: " + setting.environmentVariable(), exception);
        }
        String text = setting.codec().encode(setting.pipeline().run(value));
        if (!setting.secret()) {
            return new DatabaseSettings.EncodedSetting(text, null, null, null);
        }
        if (keyring == null) {
            throw new IllegalStateException("初始化节点声明了敏感设置但没有 SecretKeyring: " + setting.key());
        }
        SecretCipher cipher = keyring.activeCipher();
        SettingSecretCodecCatalog codecs = new SettingSecretCodecCatalog(
                cipher,
                SettingOwner.system(),
                setting.key());
        EncryptedSecret encrypted = codecs.current().encode(text.getBytes(StandardCharsets.UTF_8));
        return new DatabaseSettings.EncodedSetting(
                null,
                encrypted.keyId(),
                encrypted.ciphertext(),
                codecs.current().version());
    }
}
