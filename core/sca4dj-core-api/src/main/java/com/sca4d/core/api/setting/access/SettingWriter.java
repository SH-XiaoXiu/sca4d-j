package com.sca4d.core.api.setting.access;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;

/** 按条件写入明确所属的设置，不决定读取优先级，也不反向修改继承值。 */
public interface SettingWriter<W> {
    /** 返回与对应读取器一致的稳定诊断编码。 */
    @NotNull
    TreeCode code();

    /** 原子校验存储版本并保存完整值；codecVersion 来自实际编码器，secret 要求加密保存。 */
    void replace(
            @NotNull TreeCode setting,
            @NotNull SettingOwner owner,
            @NotNull W value,
            int codecVersion,
            boolean secret,
            @NotNull Version expected);

    /** 清除当前 owner 的覆盖值并保留递增版本，不清除继承 owner。 */
    void reset(@NotNull TreeCode setting, @NotNull SettingOwner owner, int codecVersion, @NotNull Version expected);

    /** owner 生命周期结束时物理清理；普通恢复默认值使用 reset。 */
    void erase(@NotNull TreeCode setting, @NotNull SettingOwner owner);
}
