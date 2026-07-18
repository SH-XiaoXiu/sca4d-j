package com.sca4d.common.core;

/**
 * Spring Profile 常量。
 *
 * <p>业务代码使用常量表达环境名，避免字符串散落。
 */
public final class Profiles {

    /** 本地开发环境。 */
    public static final String LOCAL = "local";

    /** 开发联调环境。 */
    public static final String DEV = "dev";

    /** 测试环境。 */
    public static final String TEST = "test";

    /** 预发布环境。 */
    public static final String STAGING = "staging";

    /** 生产环境。 */
    public static final String PROD = "prod";

    private Profiles() {
    }
}
