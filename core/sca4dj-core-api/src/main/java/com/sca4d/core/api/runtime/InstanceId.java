package com.sca4d.core.api.runtime;

import com.sca4d.core.api.name.Name;
import org.jetbrains.annotations.NotNull;

/** 标识一次进程配置中的实例名称；进程重启不改变配置名称。 */
public final class InstanceId extends Name {

    /** 使用与 sca4dj.instance.id 一致的格式创建实例标识。 */
    public InstanceId(@NotNull String value) {
        super(value);
        if (!value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("实例标识必须为 1 到 128 位字母、数字、点、下划线或连字符");
        }
    }
}
