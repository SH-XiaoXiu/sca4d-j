package com.sca4d.core.spring.security;

import com.sca4d.core.api.security.SecretHasher;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/** 使用 Spring Security 的 Argon2id 参数生成并校验秘密值摘要。 */
public final class Argon2SecretHasher implements SecretHasher {

    private final @NotNull PasswordEncoder encoder;

    /** 创建使用 Spring Security 5.8 推荐 Argon2id 参数的摘要实现。 */
    public Argon2SecretHasher() {
        this(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    }

    /** @param encoder 只在当前实现内部使用的 Spring Security 密码编码器 */
    Argon2SecretHasher(@NotNull PasswordEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder, "秘密值摘要编码器不能为空");
    }

    @Override
    public @NotNull String hash(@NotNull String secret) {
        return encoder.encode(secret);
    }

    @Override
    public boolean matches(@NotNull String secret, @NotNull String secretHash) {
        return encoder.matches(secret, secretHash);
    }
}
