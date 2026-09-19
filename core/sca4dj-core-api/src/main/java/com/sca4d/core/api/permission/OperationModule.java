package com.sca4d.core.api.permission;

import org.jetbrains.annotations.NotNull;

/** 声明一个拥有唯一编码锚点和完整 Operation 目录的业务模块。 */
public interface OperationModule {

    /**
     * 返回以当前模块唯一编码为根的 Operation 目录。
     *
     * @return 已经展开完整编码的 MODULE 根节点
     */
    @NotNull
    OperationNode.Module operationTree();
}
