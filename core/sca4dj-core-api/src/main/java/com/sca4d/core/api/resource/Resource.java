package com.sca4d.core.api.resource;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/**
 * 表示权限判断所保护的业务资源（Resource）。
 *
 * <p>资源由类型和标识共同确定。接口、任务等固定入口也属于资源，参见
 * {@link com.sca4d.core.api.permission.Operation}。
 *
 * @see com.sca4d.core.api.permission.Permission
 */
public interface Resource {

    /**
     * 返回资源类型。
     *
     * @return 非空白资源类型
     */
    @NotNull
    TreeCode type();

    /**
     * 返回资源标识。
     *
     * @return 非空白业务标识
     */
    @NotNull
    String id();
}
