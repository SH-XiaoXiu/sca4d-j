package com.sca4d.iam.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** 保存 SYSTEM Subject ID、代码声明的系统身份与人工指定的全局 Principal ID。 */
@ConfigurationProperties("sca4dj.iam")
public class SystemIdentityProperties {
    /** 整个业务系统的 SYSTEM Subject ID；所有实例必须使用同一值。 */
    private long systemSubjectId = 1L;

    /** MAIN 默认使用 1；其他系统身份必须显式指定正数 ID。 */
    private @NotNull Map<String, Long> systemPrincipalIds = new LinkedHashMap<>(Map.of("MAIN", 1L));

    public long getSystemSubjectId() {
        return systemSubjectId;
    }

    /** SYSTEM Subject ID 必须为正数。 */
    public void setSystemSubjectId(long systemSubjectId) {
        if (systemSubjectId <= 0)
            throw new IllegalArgumentException("SYSTEM Subject ID 必须为正数");
        this.systemSubjectId = systemSubjectId;
    }

    public @NotNull @Unmodifiable Map<String, Long> getSystemPrincipalIds() {
        return Map.copyOf(systemPrincipalIds);
    }

    public void setSystemPrincipalIds(@NotNull Map<String, Long> systemPrincipalIds) {
        this.systemPrincipalIds = new LinkedHashMap<>(systemPrincipalIds);
    }
}
