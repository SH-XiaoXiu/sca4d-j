package com.sca4d.core.api.permission;

import org.jetbrains.annotations.Contract;

/** 表示 Operation 目录节点在固定三层结构中的职责。 */
public enum OperationNodeType {
    /** Operation 目录的顶层业务模块。 */
    MODULE,
    /** 模块内被操作的业务资源分组。 */
    RESOURCE,
    /** 可以授予 Principal 的具体动作。 */
    ACTION;

    /**
     * 返回当前节点是否可以授予 Principal。
     *
     * @return 仅 ACTION 返回 {@code true}
     */
    @Contract(pure = true)
    public boolean grantable() {
        return this == ACTION;
    }
}
