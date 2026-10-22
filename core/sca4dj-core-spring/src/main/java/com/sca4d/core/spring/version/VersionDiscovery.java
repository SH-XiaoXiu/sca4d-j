package com.sca4d.core.spring.version;

import java.lang.annotation.*;

/** 允许版本不兼容的客户端仍查询升级和兼容信息。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionDiscovery {
}
