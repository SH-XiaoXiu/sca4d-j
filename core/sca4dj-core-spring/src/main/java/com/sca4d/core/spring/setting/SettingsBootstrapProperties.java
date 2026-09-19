package com.sca4d.core.spring.setting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 控制当前节点是否负责把 Settings ENV 初始化或同步到数据库。 */
@ConfigurationProperties("sca4dj.bootstrap")
public final class SettingsBootstrapProperties {

    private boolean enabled;

    /** 创建默认关闭 Settings 初始化的配置。 */
    public SettingsBootstrapProperties() {
    }

    /** @return 当前节点负责 Settings 初始化时返回 {@code true} */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否让当前节点成为指定初始化节点 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
