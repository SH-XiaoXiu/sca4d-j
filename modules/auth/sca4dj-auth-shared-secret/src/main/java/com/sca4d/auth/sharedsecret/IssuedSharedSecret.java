package com.sca4d.auth.sharedsecret;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回创建或轮换后的 Shared Secret 凭据和仅本次可见的原始值。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class IssuedSharedSecret {

    private final @NotNull SharedSecretCredential credential;

    private final @NotNull String secret;

    /** 创建一次性签发结果。 */
    public IssuedSharedSecret(
            @NotNull SharedSecretCredential credential,
            @NotNull String secret) {
        this.credential = Objects.requireNonNull(credential, "Shared Secret 凭据不能为空");
        this.secret = Objects.requireNonNull(secret, "Shared Secret 原始值不能为空");
    }

    /** @return 已写入数据库的凭据状态 */
    public @NotNull SharedSecretCredential credential() {
        return credential;
    }

    /** @return 仅允许本次调用返回给客户端的原始值 */
    public @NotNull String secret() {
        return secret;
    }
}
