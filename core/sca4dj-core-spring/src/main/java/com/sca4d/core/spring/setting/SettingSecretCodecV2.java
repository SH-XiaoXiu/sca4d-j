package com.sca4d.core.spring.setting;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.core.api.codec.SecretCodec;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.SettingOwner;
import org.jetbrains.annotations.NotNull;

/** 使用 owner、Setting key 和长度分隔上下文读写当前版本二敏感设置密文。 */
public final class SettingSecretCodecV2 implements SecretCodec<byte[]> {

    private static final @NotNull TreeCode CODE = TreeCode.of("SETTING/SECRET");

    private final @NotNull SecretCipher cipher;

    private final @NotNull SettingOwner owner;

    private final @NotNull String key;

    /** 创建绑定 owner、Setting key 和密钥的 V2 Secret Codec。 */
    public SettingSecretCodecV2(
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
        return 2;
    }

    @Override
    public @NotNull Class<byte[]> valueType() {
        return byte[].class;
    }

    @Override
    public @NotNull EncryptedSecret encode(byte @NotNull [] value) {
        return cipher.encrypt(purpose(), copy(value));
    }

    @Override
    public byte @NotNull [] decode(@NotNull EncryptedSecret wire) {
        return cipher.decrypt(purpose(), wire);
    }

    private @NotNull String purpose() {
        String type = owner.type().format(SLASH);
        String id = owner.id();
        return "setting:v2:" + type.length() + ":" + type + ":" + id.length() + ":" + id + ":" + key;
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
