package com.sca4d.common.resource;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 与具体页面路由解耦的资源类型和资源标识组合键。 */
public final class ResourceReferenceKey {

    private final String resourceType;
    private final String resourceId;

    /**
     * 创建已经去除首尾空白的资源引用键。
     *
     * @param resourceType 由业务模块贡献者注册的开放资源类型编码
     * @param resourceId 对应资源在其业务表中的主键或唯一编码
     */
    public ResourceReferenceKey(@NotNull String resourceType, @NotNull String resourceId) {
        if (resourceType.isBlank() || resourceId.isBlank()) {
            throw new IllegalArgumentException("资源类型和资源标识不能为空");
        }
        this.resourceType = resourceType.trim();
        this.resourceId = resourceId.trim();
    }

    /** @return 由具体业务模块注册的开放资源类型编码 */
    public @NotNull String getResourceType() {
        return resourceType;
    }

    /** @return 对应资源在其业务表中的主键或唯一编码 */
    public @NotNull String getResourceId() {
        return resourceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResourceReferenceKey key)) {
            return false;
        }
        return resourceType.equals(key.resourceType) && resourceId.equals(key.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, resourceId);
    }
}
