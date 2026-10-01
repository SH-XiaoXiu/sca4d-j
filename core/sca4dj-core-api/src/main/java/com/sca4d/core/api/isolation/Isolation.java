package com.sca4d.core.api.isolation;

import org.jetbrains.annotations.NotNull;

/** 将对象转换为应用隔离后的值；实现负责保留原对象并避免重复应用同一隔离。 */
public interface Isolation<T> {

    /** 返回已应用隔离的值，不修改原对象。 */
    @NotNull
    T isolate();
}
