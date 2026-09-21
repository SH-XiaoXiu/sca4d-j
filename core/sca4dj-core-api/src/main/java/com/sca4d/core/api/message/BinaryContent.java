package com.sca4d.core.api.message;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/** 保存只适合当前进程同步处理的二进制内容。 */
public final class BinaryContent implements ContentBody {

    private final byte @NotNull [] value;

    public BinaryContent(byte @NotNull [] value) {
        byte[] checked = Objects.requireNonNull(value, "二进制内容不能为空");
        if (checked.length == 0) {
            throw new IllegalArgumentException("二进制内容不能为空");
        }
        this.value = Arrays.copyOf(checked, checked.length);
    }

    public byte @NotNull [] value() {
        return Arrays.copyOf(value, value.length);
    }
}
