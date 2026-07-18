package com.sca4d.common.resource;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/** 由资源所属业务模块实现的批量业务名称和详情导航解析扩展点。 */
public interface ResourceReferenceContributor {

    /**
     * 返回本贡献者唯一拥有的开放资源类型。
     *
     * @return 非空、不可变的资源类型编码集合
     */
    @NotNull
    Set<String> resourceTypes();

    /**
     * 批量解析属于本业务模块的资源键。
     *
     * <p>每个实际出现的资源类型最多执行一条批量查询；不得按键逐项查询，也不得通过并行隐藏 N+1。
     *
     * @param keys 已按贡献者支持类型分组的去重资源键
     * @param context 当前 Session 权限数据
     * @return 可找到资源和明确删除状态的引用映射
     */
    @NotNull
    Map<ResourceReferenceKey, ResourceReference> resolve(
            @NotNull Set<ResourceReferenceKey> keys, @NotNull ResourceReferenceContext context);
}
