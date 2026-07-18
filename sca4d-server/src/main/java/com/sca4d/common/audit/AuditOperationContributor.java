package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 由具体业务模块提供其全部可审计模块和动作的目录扩展点。 */
public interface AuditOperationContributor {

    /**
     * 返回当前业务模块拥有的审计动作定义。
     *
     * <p>每个动作在整个应用中必须以 {@code moduleCode + actionCode} 唯一；返回顺序不作为展示顺序，目录使用定义中的排序字段。
     *
     * @return 当前业务模块完整、不可变的审计动作集合
     */
    @NotNull
    List<AuditOperationDefinition> auditOperations();
}
