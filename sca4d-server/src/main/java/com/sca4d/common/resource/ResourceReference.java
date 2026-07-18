package com.sca4d.common.resource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 已按当前请求权限解析完成的跨模块资源业务引用。 */
public class ResourceReference {

    private final ResourceReferenceKey key;
    private final String label;
    private final Long tenantId;
    private final ResourceNavigationState navigationState;
    private final String path;
    private final String navigationMessage;

    /**
     * 创建完整资源引用。
     *
     * @param key 资源类型与资源标识组成的查找键
     * @param label 当前请求允许展示的业务名称；名称未知或不可公开时为 null，不得退化为裸数据库 ID 或状态文案
     * @param tenantId 资源明确属于租户时的租户主键
     * @param navigationState 当前请求下的导航状态
     * @param path 可导航状态对应的管理端详情路径
     * @param navigationMessage 不可导航时已经本地化的文字原因
     */
    public ResourceReference(
            @NotNull ResourceReferenceKey key,
            @Nullable String label,
            @Nullable Long tenantId,
            @NotNull ResourceNavigationState navigationState,
            @Nullable String path,
            @Nullable String navigationMessage) {
        this.key = key;
        this.label = label;
        this.tenantId = tenantId;
        this.navigationState = navigationState;
        this.path = path;
        this.navigationMessage = navigationMessage;
    }

    /** @return 资源类型与资源标识组成的查找键 */
    public @NotNull ResourceReferenceKey getKey() {
        return key;
    }

    /** @return 当前请求允许展示的业务名称；名称未知或不可公开时为 null */
    public @Nullable String getLabel() {
        return label;
    }

    /** @return 资源所属租户；平台资源为 null */
    public @Nullable Long getTenantId() {
        return tenantId;
    }

    /** @return 当前请求下的导航状态 */
    public @NotNull ResourceNavigationState getNavigationState() {
        return navigationState;
    }

    /** @return 可导航时的管理端详情路径 */
    public @Nullable String getPath() {
        return path;
    }

    /** @return 不可导航时的本地化文字原因 */
    public @Nullable String getNavigationMessage() {
        return navigationMessage;
    }
}
