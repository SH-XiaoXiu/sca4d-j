package com.sca4d.common.boot;

import com.sca4d.common.core.Profiles;

import java.util.Arrays;

/** 应用运行环境。 */
public enum BootEnvironment {
    /** 本地开发环境。 */
    LOCAL(Profiles.LOCAL),
    /** 开发联调环境。 */
    DEV(Profiles.DEV),
    /** 测试环境。 */
    TEST(Profiles.TEST),
    /** 预发布环境。 */
    STAGING(Profiles.STAGING),
    /** 生产环境。 */
    PROD(Profiles.PROD);

    private final String profile;

    BootEnvironment(String profile) {
        this.profile = profile;
    }

    /**
     * 从当前活跃 Profile 中解析唯一的运行环境。
     *
     * @param profiles Spring 活跃 Profile
     * @return 当前运行环境
     */
    public static BootEnvironment resolve(String... profiles) {
        BootEnvironment[] matches = Arrays.stream(values())
                .filter(environment -> Arrays.asList(profiles).contains(environment.profile))
                .toArray(BootEnvironment[]::new);
        if (matches.length != 1) {
            throw new IllegalStateException("必须且只能激活一个环境 Profile，当前为: " + String.join(",", profiles));
        }
        return matches[0];
    }

    /**
     * 判断是否为预发布或生产环境。
     *
     * @return 预发布或生产环境时返回 {@code true}
     */
    public boolean isProductionLike() {
        return this == STAGING || this == PROD;
    }
}
