package com.sca4d.core.spring.config;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

/** 配置进程执行位置和入口能力选择，不影响调用者身份或行级数据范围。 */
@ConfigurationProperties("sca4dj.instance")
public final class InstanceProperties {

    /** 进程唯一标识；默认每次启动生成，不能被当作 IAM Principal ID。 */
    private String id = UUID.randomUUID().toString();

    /** 未配置时启用所有已声明能力；空集合表示不启用任何能力。 */
    private @Nullable List<String> enabledCapabilities;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public @Nullable List<String> getEnabledCapabilities() {
        return enabledCapabilities;
    }

    public void setEnabledCapabilities(@Nullable List<String> enabledCapabilities) {
        this.enabledCapabilities = enabledCapabilities;
    }
}
