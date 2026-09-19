package com.sca4d.core.api.concurrent;

/** 用于乐观并发控制的非负版本。 */
public final class Version {

    private final long value;

    /**
     * 创建乐观版本。
     *
     * @param value 非负版本值
     */
    public Version(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("版本不能为负数");
        }
        this.value = value;
    }

    /**
     * 返回版本值。
     *
     * @return 版本值
     */
    public long value() {
        return value;
    }

    /**
     * 返回下一版本。
     *
     * @return 下一版本
     */
    public Version next() {
        return new Version(Math.incrementExact(value));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Version version && value == version.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
