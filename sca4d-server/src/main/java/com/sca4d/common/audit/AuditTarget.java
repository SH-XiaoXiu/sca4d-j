package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 被审计操作影响的固定资源类型和可选资源标识。 */
public class AuditTarget {

    private String type;
    private String id;
    private String label;

    /** 创建供 Jackson 反序列化逐字段填充的空目标。 */
    public AuditTarget() {
    }

    /**
     * 创建业务操作已经解析完成的审计目标。
     *
     * @param type 与具体路由解耦的资源类型编码
     * @param id 资源标识；创建前、批量目标或无法唯一定位时为 null
     */
    public AuditTarget(@NotNull String type, @Nullable String id) {
        this.type = type;
        this.id = id;
    }

    /** @return 固定资源类型；仅反序列化未完成对象可能为 null */
    public @Nullable String getType() {
        return type;
    }

    /** @param type 固定资源类型；供 Jackson 还原历史信封 */
    public void setType(@Nullable String type) {
        this.type = type;
    }

    /** @return 单一资源标识，操作不对应唯一资源时为 null */
    public @Nullable String getId() {
        return id;
    }

    /** @param id 单一资源标识，操作不对应唯一资源时使用 null */
    public void setId(@Nullable String id) {
        this.id = id;
    }

    /** @return 操作发生时的目标业务名称快照，历史记录未采集时为 null */
    public @Nullable String getLabel() {
        return label;
    }

    /** @param label 操作发生时的目标业务名称快照 */
    public void setLabel(@Nullable String label) {
        this.label = label;
    }
}
