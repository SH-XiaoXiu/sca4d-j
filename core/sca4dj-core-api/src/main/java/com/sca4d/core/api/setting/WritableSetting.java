package com.sca4d.core.api.setting;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.exception.SettingReadOnlyException;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** 直接在明确目标中替换或清除当前 owner 的覆盖值，不修改继承 owner。 */
public class WritableSetting<T> extends Setting<T> {
    private final Function<SettingOwner, SettingValue<?>> target;

    private final BiConsumer<SettingOwner, T> replace;

    private final Consumer<SettingOwner> reset;

    private final Consumer<SettingOwner> erase;

    /** 捕获同一种外部表示的读取器、写入器和编码函数，避免擦除类型后强制转换。 */
    protected <D, W> WritableSetting(
            @NotNull Builder<T, D> builder,
            @NotNull SettingReader<W> reader,
            @NotNull SettingWriter<W> writer,
            @NotNull Function<D, W> format) {
        super(builder);
        if (reader != writer && !reader.code().equals(writer.code()))
            throw new IllegalArgumentException("写入目标与版本读取器不一致");
        target = owner -> reader.read(code(), owner);
        replace = (owner, value) -> {
            Version expected = targetVersion(owner);
            writer.replace(
                    code(),
                    owner,
                    format.apply(builder.current().encode(validate(value))),
                    builder.current().version(),
                    secret(),
                    expected);
        };
        reset = owner -> writer.reset(code(), owner, builder.current().version(), targetVersion(owner));
        erase = owner -> writer.erase(code(), owner);
    }

    /** 保存固定 owner 的完整值。 */
    public @NotNull SettingValue<T> replace(@NotNull T value) {
        return replace(requireOwner(), value);
    }

    /** 校验并条件保存完整值，返回写入后的有效读取结果。 */
    public @NotNull SettingValue<T> replace(@NotNull SettingOwner owner, @NotNull T value) {
        checkOwner(owner);
        replace.accept(owner, value);
        return inspect(owner);
    }

    /** 清除固定 owner 的目标覆盖值。 */
    public @NotNull SettingValue<T> reset() {
        return reset(requireOwner());
    }

    /** 清除目标覆盖并重新计算继承或默认值，保留目标版本。 */
    public @NotNull SettingValue<T> reset(@NotNull SettingOwner owner) {
        checkOwner(owner);
        reset.accept(owner);
        return inspect(owner);
    }

    /** owner 已删除时物理清理；不用于普通恢复默认值。 */
    public void erase(@NotNull SettingOwner owner) {
        checkOwner(owner);
        erase.accept(owner);
    }

    /** 返回写入目标的实际存储状态，管理接口使用其版本而不是组合读取版本。 */
    public @NotNull SettingValue<?> inspectTarget(@NotNull SettingOwner owner) {
        checkOwner(owner);
        return target.apply(owner);
    }

    /** 明确要求目标支持条件版本，并在同步锁定时拒绝写入。 */
    private Version targetVersion(SettingOwner owner) {
        SettingValue<?> current = target.apply(owner);
        if (current.locked())
            throw new SettingReadOnlyException(code().toString());
        return current.revision().orElseThrow(() -> new IllegalStateException("写入目标缺少条件版本"));
    }
}
