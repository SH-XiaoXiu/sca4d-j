package com.sca4d.core.api.permission;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.Contract;

/**
 * 表示一次允许判定可以作用的业务资源子集（Resource Scope）。
 *
 * <p>具体范围由拥有资源的业务模块定义，例如设备模块可以定义可读取的组织和设备集合。范围只保存业务条件，不保存 SQL、ORM
 * 对象或分页参数。资源范围属于权限判定（Permission Decision，{@link PermissionDecision}）的组成部分，不构成独立的权限体系。
 *
 * @see Permission
 * @see PermissionDecision
 */
public interface ResourceScope {

    /**
     * 返回当前范围约束的资源类型。
     *
     * @return 与 {@link Resource#type()} 一致的资源类型
     */
    @Contract(pure = true)
    TreeCode resourceType();
}
