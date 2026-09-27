package com.sca4d.auth.sharedsecret;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 只在一次认证方法调用中存在的 Shared Secret 明文材料。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class SharedSecretProof {

    private final @NotNull String secret;

    /** @param secret 客户端通过受保护传输提交的当前 Shared Secret */
    public SharedSecretProof(@NotNull String secret) {
        this.secret = Objects.requireNonNull(secret, "Shared Secret 不能为空");
    }

    /** @return 仅供 Shared Secret 认证方法校验的原始值 */
    public @NotNull String secret() {
        return secret;
    }

    @Override
    public @NotNull String toString() {
        return "SharedSecretProof[secret=protected]";
    }
}
