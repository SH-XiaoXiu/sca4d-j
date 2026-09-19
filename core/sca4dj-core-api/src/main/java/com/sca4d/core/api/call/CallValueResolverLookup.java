package com.sca4d.core.api.call;

/** 按类型从外围容器取得调用值解析器。 */
public interface CallValueResolverLookup {

    /**
     * 取得指定类型的解析器实例。
     *
     * @param resolverType 注解声明的解析器类型
     * @return 由外围容器管理的解析器实例
     */
    CallValueResolver<?> resolver(Class<? extends CallValueResolver<?>> resolverType);

}
