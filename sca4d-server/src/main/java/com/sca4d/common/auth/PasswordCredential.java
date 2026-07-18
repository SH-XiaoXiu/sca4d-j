package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * PASSWORD Credential 绑定或替换使用的秘密材料。
 *
 * <p>登录标识属于 AccountIdentifier，不进入本对象。密码摘要和 Credential 状态由 PasswordCredentialScheme
 * 负责，调用方不得把本对象作为持久化对象保存。
 */
public final class PasswordCredential {

    /** 仅在绑定或替换过程中使用的明文密码。 */
    private final String password;

    /**
     * 创建 PASSWORD Credential 秘密材料。
     *
     * @param password 明文密码，只能在当前绑定或替换操作中消费
     */
    public PasswordCredential(@NotNull String password) {
        this.password = password;
    }

    /**
     * 返回本次操作提交的明文密码。
     *
     * <p>调用方只能将该值交给密码 CredentialScheme 哈希或验证，不得记录日志、序列化到响应或保存为业务状态。
     *
     * @return 当前认证操作使用的明文密码
     */
    public @NotNull String password() {
        return password;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PasswordCredential credential)) {
            return false;
        }
        return password.equals(credential.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(password);
    }
}
