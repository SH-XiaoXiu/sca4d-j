package com.sca4d.core.spring.config;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

/** 配置进程标识和启用的功能入口，不影响调用者身份或行级数据范围。 */
@ConfigurationProperties("sca4dj.instance")
public class InstanceProperties {

    /** 进程唯一标识；默认每次启动生成，不能被当作 IAM Principal ID。 */
    private String id = UUID.randomUUID().toString();

    /** 配置键 `sca4dj.instance.capabilities`；为 null 时启用全部已声明功能，空列表表示不启用任何功能。 */
    private @Nullable List<String> capabilities;

    /** @return 当前进程的实例标识，格式由 InstanceAutoConfiguration 校验 */
    public String getId() {
        return id;
    }

    /** @param id 当前进程的实例标识，必须是 1 到 128 位字母、数字、点、下划线或连字符 */
    public void setId(String id) {
        this.id = id;
    }

    /** @return 配置的功能编码副本；null 表示启用全部已声明功能，空列表表示不启用任何功能 */
    public @Nullable @Unmodifiable List<String> getCapabilities() {
        return capabilities;
    }

    /** @param capabilities 配置的功能编码；null 表示启用全部已声明功能，复制后保存为不可修改列表 */
    public void setCapabilities(@Nullable List<String> capabilities) {
        this.capabilities = capabilities == null ? null : List.copyOf(capabilities);
    }
}
