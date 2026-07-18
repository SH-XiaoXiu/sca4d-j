package com.sca4d.common.resource;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/** 当前应用跨模块资源引用的共用批量解析入口。 */
public interface ResourceReferenceResolver {

    /**
     * 按业务模块贡献者批量解析资源引用。
     *
     * @param keys 当前响应需要展示的全部资源键
     * @return 每个输入键对应的业务引用
     */
    @NotNull
    Map<ResourceReferenceKey, ResourceReference> resolve(@NotNull Collection<ResourceReferenceKey> keys);
}
