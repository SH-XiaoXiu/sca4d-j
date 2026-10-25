package com.sca4d.core.spring.setting;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.core.api.codec.SecretCodec;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;

/** 加密和解密 V2 版敏感设置密文，加密用途绑定 owner、Setting key 和长度分隔的上下文。 */
public class SettingSecretCodecV2 implements SecretCodec<byte[]> {

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
        if (key.isBlank()) {
            throw new IllegalArgumentException("Setting key 不能为空白");
        }
        this.key = key;
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
        return cipher.encrypt(purpose(), value.clone());
    }

    @Override
    public byte @NotNull [] decode(@NotNull EncryptedSecret wire) {
        return cipher.decrypt(purpose(), wire);
    }

    /** 生成加密用途字符串：owner 类型和 ID 按长度前缀拼接，Setting key 放在最后，避免不同 owner 产生相同上下文。 */
    private @NotNull String purpose() {
        String type = owner.type().format(SLASH);
        String id = owner.id();
        return "setting:v2:" + type.length() + ":" + type + ":" + id.length() + ":" + id + ":" + key;
    }
}
