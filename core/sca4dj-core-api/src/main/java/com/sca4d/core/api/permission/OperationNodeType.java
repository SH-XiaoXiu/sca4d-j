package com.sca4d.core.api.permission;

import org.jetbrains.annotations.Contract;

/** 表示 Operation 目录节点是包含子节点的分组，还是可以授予的叶子。 */
public enum OperationNodeType {
    /** 目录中的分组，直接子节点可以是子分组、叶子或两者混合，深度不限。 */
    GROUP,
    /** 可以授予 Principal 的具体 Operation，没有子节点。 */
    LEAF;

    /**
     * 返回当前节点是否可以授予 Principal。
     *
     * @return 仅 LEAF 返回 {@code true}
     */
    @Contract(pure = true)
    public boolean grantable() {
        return this == LEAF;
    }
}
