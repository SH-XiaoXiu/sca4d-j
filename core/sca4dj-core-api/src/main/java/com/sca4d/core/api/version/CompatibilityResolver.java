package com.sca4d.core.api.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;

/** 根据客户端身份、能力和显式契约选择唯一兼容规则，不依赖 HTTP。 */
public final class CompatibilityResolver {
    private final @NotNull List<CompatibilityRule> rules;

    /** 创建静态矩阵并拒绝重叠范围，避免按文件顺序选择行为。 */
    public CompatibilityResolver(@NotNull List<CompatibilityRule> rules) {
        List<CompatibilityRule> checkedRules = List.copyOf(rules);
        this.rules = checkedRules;
        for (int i = 0; i < checkedRules.size(); i++) {
            var a = checkedRules.get(i);
            for (int j = i + 1; j < checkedRules.size(); j++) {
                var b = checkedRules.get(j);
                String aMinimum = a.minimumVersion();
                String bMinimum = b.minimumVersion();
                String aMaximum = a.maximumVersion();
                String bMaximum = b.maximumVersion();
                if (a.client().equals(b.client())
                        && a.capability().equals(b.capability())
                        && a.contract() == b.contract()
                        && (aMinimum == null
                                || bMinimum == null
                                || aMaximum == null
                                || bMaximum == null
                                || (CompatibilityRule.compareVersions(aMinimum, bMaximum) < 0
                                        && CompatibilityRule.compareVersions(bMinimum, aMaximum) < 0)))
                    throw new IllegalArgumentException("兼容矩阵包含重叠规则");
            }
        }
    }

    /** @return 用于公开查询的不可变规则 */
    public @NotNull @Unmodifiable List<CompatibilityRule> rules() {
        return rules;
    }

    /**
     * 显式路由契约和请求头不一致时拒绝；未显式选择时使用客户端匹配的最高可用契约。
     * @param client 客户端构建声明
     * @param capability 应用能力标识
     * @param routeContract URL 或 Controller 声明的固定契约；同路径协商时为空
     * @return 兼容结果
     */
    public @NotNull CompatibilityDecision resolve(
            @NotNull ClientVersion client,
            @NotNull String capability,
            @Nullable Integer routeContract) {
        if (routeContract != null && client.contract() != null && !routeContract.equals(client.contract()))
            return new CompatibilityDecision(CompatibilityStatus.BLOCKED, null, "contract-conflict");
        Integer selected = routeContract == null ? client.contract() : routeContract;
        var matches = rules.stream()
                .filter(rule -> rule.matches(client, capability))
                .filter(
                        rule -> selected == null
                                ? rule.status() != CompatibilityStatus.BLOCKED
                                : rule.contract() == selected)
                .max(Comparator.comparingInt(CompatibilityRule::contract));
        return matches.map(rule -> new CompatibilityDecision(rule.status(), rule, "matched"))
                .orElseGet(() -> new CompatibilityDecision(CompatibilityStatus.BLOCKED, null, "unsupported"));
    }
}
