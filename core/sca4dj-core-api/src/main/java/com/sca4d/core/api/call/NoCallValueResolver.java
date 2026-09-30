package com.sca4d.core.api.call;

import com.sca4d.core.api.resource.Resource;

import java.util.Collection;

/** 注解用于表示未声明动态值解析器的占位类型，不应被实际调用。 */
public class NoCallValueResolver implements CallValueResolver<Collection<Resource>> {

    public NoCallValueResolver() {
    }

    /** 始终拒绝调用。 */
    @Override
    public Collection<Resource> resolve(CallExecution execution) {
        throw new IllegalStateException("未声明动态值解析器");
    }
}
