package com.sca4d.core.api.isolation;

import com.sca4d.core.api.name.Name;
import org.jetbrains.annotations.NotNull;

/** 从全局 App 名获取名称隔离依据；实例 ID 不参与隔离。 */
public interface AppIsolation<T> extends Isolation<T> {

    /** 返回配置加载后固定的 App 隔离名称。 */
    default @NotNull Name appName() {
        return AppName.current();
    }
}
