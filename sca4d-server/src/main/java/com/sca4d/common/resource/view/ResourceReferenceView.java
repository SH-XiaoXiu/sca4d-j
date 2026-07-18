package com.sca4d.common.resource.view;

import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 管理端共用跨模块资源引用响应。 */
@Data
@Accessors(chain = true)
public class ResourceReferenceView {

    private String resourceType;
    private String resourceId;
    private String label;
    private Long tenantId;
    private ResourceNavigationState navigationState;
    private String path;
    private String navigationMessage;

    /**
     * 将权限解析完成的业务模块引用转换为响应结构。
     *
     * @param reference 业务模块资源引用
     * @return 不包含持久化细节的固定响应
     */
    public static @NotNull ResourceReferenceView from(@NotNull ResourceReference reference) {
        return new ResourceReferenceView()
                .setResourceType(reference.getKey().getResourceType())
                .setResourceId(reference.getKey().getResourceId())
                .setLabel(reference.getLabel())
                .setTenantId(reference.getTenantId())
                .setNavigationState(reference.getNavigationState())
                .setPath(reference.getPath())
                .setNavigationMessage(reference.getNavigationMessage());
    }
}
