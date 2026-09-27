package com.sca4d.auth.password;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 只在一次认证方法调用中存在的 Password 认证材料。 */
public class PasswordProof {

    private final String identifier;

    private final String password;

    /**
     * 创建 Password 认证材料。
     *
     * @param identifier 登录时用于发现 Account 的用户名、邮箱等标识；已知 Account 的 STEP_UP 可以为 {@code null}
     * @param password 原始密码
     */
    public PasswordProof(@Nullable String identifier, @NotNull String password) {
        this.identifier = identifier;
        this.password = Objects.requireNonNull(password, "密码不能为空");
    }

    @Nullable
    String identifier() {
        return identifier;
    }

    @NotNull
    String password() {
        return password;
    }

    @Override
    public @NotNull String toString() {
        return "PasswordProof[identifier=" + identifier + ", password=protected]";
    }
}
