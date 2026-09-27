package com.sca4d.core.spring.setting;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;

import java.util.Objects;
import java.util.function.Function;

/** 从显式 ENV 名称读取 System 所属文本，不隐式覆盖其他 owner 或写入数据库。 */
public final class EnvSettingReader implements SettingReader<String> {
    private final Environment environment;

    private final Function<TreeCode, String> variable;

    /** 保存 ENV 来源和设置到变量名的明确映射。 */
    public EnvSettingReader(@NotNull Environment environment, @NotNull Function<TreeCode, String> variable) {
        this.environment = Objects.requireNonNull(environment);
        this.variable = Objects.requireNonNull(variable);
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("SETTING:ENV");
    }

    @Override
    public @NotNull SettingValue<String> read(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
        if (!SettingOwner.system().equals(owner))
            throw new IllegalArgumentException("ENV Source 必须读取 System owner，其他 owner 应明确声明继承");
        return SettingValue
                .read(code(), setting, owner, environment.getProperty(variable.apply(setting)), 0, null, false);
    }
}
