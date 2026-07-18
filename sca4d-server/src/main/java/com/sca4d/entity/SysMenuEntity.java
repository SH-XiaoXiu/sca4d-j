package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 系统模块维护的导航菜单持久化实体。
 *
 * <p>菜单只保存代码路由编码和展示元数据，不保存组件名或任意前端路径。{@code permissionCode} 必须与路由目录绑定的代码权限一致。
 */
@Data
@Accessors(chain = true)
@Table("sys_menu")
public class SysMenuEntity {

    private Long id;
    private Long parentId;
    private String permissionCode;
    private String routeCode;
    private String titleLabels;
    private String iconCode;
    private Integer sortOrder;
    private Boolean visible;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
