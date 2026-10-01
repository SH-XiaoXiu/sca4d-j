package com.sca4d.auth.hmac;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回创建或轮换后的 MAC Credential 和仅本次可见的 Key。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class IssuedHmacKey {

    private final @NotNull HmacCredential credential;

    private final @NotNull String key;

    public IssuedHmacKey(@NotNull HmacCredential credential, @NotNull String key) {
        this.credential = Objects.requireNonNull(credential, "HMAC Credential 不能为空");
        this.key = Objects.requireNonNull(key, "HMAC Key 不能为空");
    }

    /** @return 已写入数据库的 Credential */
    public @NotNull HmacCredential credential() {
        return credential;
    }

    /** @return 仅允许本次调用返回给客户端的原始 Key */
    public @NotNull String key() {
        return key;
    }
}
