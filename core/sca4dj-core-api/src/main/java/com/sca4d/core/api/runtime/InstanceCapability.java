package com.sca4d.core.api.runtime;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 声明实例可启用的入口和工作线程能力、自主执行身份及 HTTP 路径前缀。 */
public final class InstanceCapability {

    private final @NotNull TreeCode code;

    private final @NotNull TreeCode systemIdentity;

    private final @NotNull List<String> paths;

    /** 创建能力声明；能力可用性不授予调用者业务权限。 */
    public InstanceCapability(
            @NotNull TreeCode code,
            @NotNull TreeCode systemIdentity,
            @NotNull List<String> paths) {
        this.code = Objects.requireNonNull(code, "能力编码不能为空");
        this.systemIdentity = Objects.requireNonNull(systemIdentity, "领域身份编码不能为空");
        this.paths = List.copyOf(Objects.requireNonNull(paths, "能力路径不能为空"));
        if (code.hasNode(TreeCode.ofSegment("*"))) {
            throw new IllegalArgumentException("能力必须使用不带通配符的冒号编码");
        }
        for (String prefix : this.paths) {
            if (!prefix.startsWith("/")
                    || prefix.contains("?")
                    || prefix.contains("#")
                    || prefix.contains("*")
                    || prefix.length() > 1 && prefix.endsWith("/")) {
                throw new IllegalArgumentException("能力路径必须是完整路径前缀: " + prefix);
            }
        }
    }

    /** 返回固定能力编码。 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** 返回系统自主工作使用的领域身份编码。 */
    public @NotNull TreeCode systemIdentity() {
        return systemIdentity;
    }

    /** 返回声明该能力的全部 HTTP 路径前缀。 */
    public @NotNull @Unmodifiable List<String> paths() {
        return paths;
    }
}
