package com.sca4d.auth.email;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** 表示不包含验证码或邮箱的 Email Code Challenge 标识。 */
public class EmailCodeChallengeId {

    private final @NotNull String value;

    /** @param value 标准 UUID 文本 */
    public EmailCodeChallengeId(@NotNull String value) {
        this.value = UUID.fromString(Objects.requireNonNull(value, "Email Code Challenge 标识不能为空")).toString();
    }

    /** @return 标准 UUID 文本 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof EmailCodeChallengeId id && value.equals(id.value);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
