package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 使用稳定限流编码和动态维度值组成跨实例限流键。 */
public class RateLimitKey extends Key {

    /** 构造固定限流键。 */
    public RateLimitKey(@NotNull Code code) {
        super(Key.of(TreeCode.of("RATE_LIMIT").append(code)));
    }

    /** 在稳定限流编码之后保存动态文本。 */
    public RateLimitKey(@NotNull Code code, String @NotNull... values) {
        super(Key.of(TreeCode.of("RATE_LIMIT").append(code), values));
    }

    /** 在稳定限流编码之后保存动态数字。 */
    public RateLimitKey(@NotNull Code code, long @NotNull... values) {
        super(Key.of(TreeCode.of("RATE_LIMIT").append(code), values));
    }

    private RateLimitKey(@NotNull List<String> segments) {
        super(segments);
    }

    @Override
    protected @NotNull RateLimitKey withSegments(@NotNull List<String> segments) {
        return new RateLimitKey(segments);
    }

    @Override
    public @NotNull RateLimitKey part(@NotNull String value) {
        return (RateLimitKey) super.part(value);
    }

    @Override
    public @NotNull RateLimitKey part(long value) {
        return (RateLimitKey) super.part(value);
    }

    @Override
    public @NotNull RateLimitKey append(@NotNull Code code) {
        return (RateLimitKey) super.append(code);
    }
}
