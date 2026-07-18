package com.sca4d.common.access;

import com.cedarpolicy.model.Context;
import com.cedarpolicy.model.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一次 Cedar 判断使用的 Principal、Resource、Context 和补充 Entity。
 */
public class CedarRequest {

    private final Entity principal;
    private final Entity resource;
    private final Context context;
    private final Set<Entity> entities;

    /**
     * 创建 Cedar 请求数据。
     *
     * <p>Principal 和 Resource 会自动加入 Entity 集合，调用方只需传入关系判断额外引用的 Entity。
     *
     * @param principal 当前请求选择的执行主体
     * @param resource 当前请求访问的资源
     * @param context 当前请求参与规则判断的值
     * @param entities Principal 与 Resource 之外的关系实体
     */
    public CedarRequest(
            @NotNull Entity principal,
            @NotNull Entity resource,
            @NotNull Context context,
            @NotNull Set<Entity> entities) {
        this.principal = principal;
        this.resource = resource;
        this.context = context;
        LinkedHashSet<Entity> requestEntities = new LinkedHashSet<>(entities);
        requestEntities.add(principal);
        requestEntities.add(resource);
        this.entities = Set.copyOf(requestEntities);
    }

    /** @return Cedar 请求中的 Principal Entity */
    public @NotNull Entity getPrincipal() {
        return principal;
    }

    /** @return Cedar 请求中的 Resource Entity */
    public @NotNull Entity getResource() {
        return resource;
    }

    /** @return Cedar 请求中的 Context */
    public @NotNull Context getContext() {
        return context;
    }

    /** @return 包含 Principal 与 Resource 的完整 Entity 集合 */
    public @NotNull Set<Entity> getEntities() {
        return entities;
    }
}
