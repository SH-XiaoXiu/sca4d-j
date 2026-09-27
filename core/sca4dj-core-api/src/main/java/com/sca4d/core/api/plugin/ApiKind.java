package com.sca4d.core.api.plugin;

/** 区分宿主提供的服务与插件提供的扩展。 */
public enum ApiKind {
    /** 宿主服务。 */
    SERVICE,
    /** 插件扩展。 */
    EXTENSION
}
