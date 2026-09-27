package com.sca4d.core.api.runtime;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保存当前实例全部能力声明与启用选择；能力不参与业务权限判定。 */
public class InstanceCapabilities {

    private final @NotNull Map<TreeCode, InstanceCapability> definitions;

    private final @NotNull Set<TreeCode> enabled;

    /** 未指定启用集合时启用所有已声明能力；未知或重复声明拒绝启动。 */
    public InstanceCapabilities(
            @NotNull Collection<InstanceCapability> definitions,
            @Nullable Collection<TreeCode> enabled) {
        Map<TreeCode, InstanceCapability> values = new LinkedHashMap<>();
        for (InstanceCapability definition : definitions) {
            InstanceCapability checked = Objects.requireNonNull(definition);
            if (values.putIfAbsent(checked.code(), checked) != null) {
                throw new IllegalStateException("实例能力重复声明: " + checked.code());
            }
        }
        this.definitions = Map.copyOf(values);
        this.enabled = enabled == null ? Set.copyOf(values.keySet()) : Set.copyOf(enabled);
        for (TreeCode code : this.enabled) {
            if (!this.definitions.containsKey(code)) {
                throw new IllegalStateException("实例能力未声明: " + code);
            }
        }
    }

    /** 返回当前实例是否启用了已声明能力。 */
    public boolean enabled(@NotNull TreeCode code) {
        return enabled.contains(code);
    }

    /** 返回全部能力声明；停用能力的路由要求仍须向 Gateway 声明。 */
    public @NotNull @Unmodifiable Map<TreeCode, InstanceCapability> definitions() {
        return definitions;
    }

    /** 返回已经启用的稳定能力编码。 */
    public @NotNull @Unmodifiable Set<TreeCode> enabledCodes() {
        return enabled;
    }

    /** 返回服务器路径前缀所要求的能力，保留停用能力防止 Gateway 错投流量。 */
    public @NotNull @Unmodifiable Map<String, Set<TreeCode>> apiRequirements() {
        Map<String, Set<TreeCode>> requirements = new LinkedHashMap<>();
        definitions.values()
                .forEach(
                        definition -> definition.paths()
                                .forEach(
                                        path -> requirements.computeIfAbsent(path, ignored -> new LinkedHashSet<>())
                                                .add(definition.code())));
        requirements.replaceAll((path, codes) -> Set.copyOf(codes));
        return Map.copyOf(requirements);
    }
}
