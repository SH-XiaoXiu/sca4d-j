package com.sca4d.core.api.setting.access;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;

/** 读取明确所属的一项设置；返回外部表示，不执行领域解码、继承或降级。 */
public interface SettingReader<W> {
    /** 返回读写实现的稳定诊断编码。 */
    @NotNull
    TreeCode code();

    /** 返回本次读取的内容、所属和版本；读取失败不能伪装成没有覆盖值。 */
    @NotNull
    SettingValue<W> read(@NotNull TreeCode setting, @NotNull SettingOwner owner);
}
