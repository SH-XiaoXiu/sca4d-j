package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 使用稳定锁资源编码和动态标识组成跨实例互斥键，物理名称使用 App 隔离。 */
public final class LockKey extends Key {

    /** 构造固定锁键。 */
    public LockKey(@NotNull Code code) {
        super(Key.of(TreeCode.of("LOCK").append(code)));
    }

    /** 在稳定锁编码之后保存动态文本，保持原始大小写及字符区别。 */
    public LockKey(@NotNull Code code, String @NotNull... values) {
        super(Key.of(TreeCode.of("LOCK").append(code), values));
    }

    /** 在稳定锁编码之后保存动态数字。 */
    public LockKey(@NotNull Code code, long @NotNull... values) {
        super(Key.of(TreeCode.of("LOCK").append(code), values));
    }

    /** 保存已校验的锁键节点，避免追加时重复添加 LOCK。 */
    private LockKey(@NotNull List<String> segments) {
        super(segments);
    }

    @Override
    protected @NotNull LockKey withSegments(@NotNull List<String> segments) {
        return new LockKey(segments);
    }

    @Override
    public @NotNull LockKey part(@NotNull String value) {
        return (LockKey) super.part(value);
    }

    @Override
    public @NotNull LockKey part(long value) {
        return (LockKey) super.part(value);
    }

    @Override
    public @NotNull LockKey append(@NotNull Code code) {
        return (LockKey) super.append(code);
    }
}
