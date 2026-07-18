package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 指定 Account 的密码证明。 */
public final class PasswordProof {

    private final String password;

    /** @param password 仅供 PasswordCredentialScheme 在当前请求中验证的明文密码 */
    public PasswordProof(@NotNull String password) {
        this.password = password;
    }

    /** @return 仅供当前验证操作使用的明文密码 */
    public @NotNull String password() {
        return password;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof PasswordProof proof && password.equals(proof.password));
    }

    @Override
    public int hashCode() {
        return Objects.hash(password);
    }
}
