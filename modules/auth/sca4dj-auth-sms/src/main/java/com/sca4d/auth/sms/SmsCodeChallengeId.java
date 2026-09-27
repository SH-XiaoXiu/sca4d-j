package com.sca4d.auth.sms;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 标识一个短期、单次消费的 SMS Code Challenge。 */
public final class SmsCodeChallengeId {
    private final @NotNull String value;

    /** @param value Challenge UUID 文本 */
    public SmsCodeChallengeId(@NotNull String value) {
        String checked = TextUtils.requireText(value, "SMS Code Challenge 标识");
        if (checked.length() > 64)
            throw new IllegalArgumentException("SMS Code Challenge 标识过长");
        this.value = checked;
    }

    /** @return Challenge 的稳定文本标识 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof SmsCodeChallengeId id && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public @NotNull String toString() {
        return value;
    }
}
