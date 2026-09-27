package com.sca4d.iam.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** 保存代码声明的系统身份与人工指定的全局 Principal ID。 */
@ConfigurationProperties("sca4dj.iam")
public final class SystemIdentityProperties {

    /** MAIN 默认使用 1；其他系统身份必须显式指定正数 ID。 */
    private @NotNull Map<String, Long> systemPrincipalIds = new LinkedHashMap<>(Map.of("MAIN", 1L));

    public @NotNull Map<String, Long> getSystemPrincipalIds() {
        return systemPrincipalIds;
    }

    public void setSystemPrincipalIds(@NotNull Map<String, Long> systemPrincipalIds) {
        this.systemPrincipalIds = new LinkedHashMap<>(systemPrincipalIds);
    }
}
