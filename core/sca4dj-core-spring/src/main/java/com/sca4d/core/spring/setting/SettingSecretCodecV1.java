package com.sca4d.core.spring.setting;

import com.sca4d.core.api.codec.SecretCodec;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;

/** 使用旧的 Setting key 上下文读取版本一敏感设置密文，供历史数据和显式迁移使用。 */
public final class SettingSecretCodecV1 implements SecretCodec<byte[]> {

    private static final @NotNull TreeCode CODE = TreeCode.of("SETTING/SECRET");

    private final @NotNull SecretCipher cipher;

    private final @NotNull SettingOwner owner;

    private final @NotNull String key;

    /** 创建绑定 owner、Setting key 和密钥的 V1 Secret Codec。 */
    public SettingSecretCodecV1(
            @NotNull SecretCipher cipher,
            @NotNull SettingOwner owner,
            @NotNull String key) {
        this.cipher = cipher;
        this.owner = owner;
        this.key = text(key);
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public @NotNull Class<byte[]> valueType() {
        return byte[].class;
    }

    @Override
    public @NotNull EncryptedSecret encode(byte @NotNull [] value) {
        return cipher.encrypt("setting:" + key, copy(value));
    }

    @Override
    public byte @NotNull [] decode(@NotNull EncryptedSecret wire) {
        return cipher.decrypt("setting:" + key, wire);
    }

    /** @return 当前绑定的 owner；V1 purpose 不使用该字段，但迁移时需要保留上下文 */
    public @NotNull SettingOwner owner() {
        return owner;
    }

    private static @NotNull String text(@NotNull String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Setting key 不能为空白");
        }
        return value;
    }

    private static byte @NotNull [] copy(byte @NotNull [] value) {
        return value.clone();
    }
}
