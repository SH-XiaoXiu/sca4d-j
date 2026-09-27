package com.sca4d.iam.rbac;

import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 使用稳定 Code 选择 RBAC Repository，并保留独立领域类型。 */
public final class RbacDomain extends Code {

    /** 从代码声明或配置边界解析固定领域编码，禁止通配符。 */
    public RbacDomain(@NotNull String name) {
        super(TreeCode.ofSegment(name));
        if (nodes().contains("*")) {
            throw new IllegalArgumentException("RbacDomain 不能包含通配符");
        }
    }
}
