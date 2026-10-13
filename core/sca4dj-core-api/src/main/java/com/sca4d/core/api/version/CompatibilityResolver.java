package com.sca4d.core.api.version;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** 根据客户端身份、能力和显式契约选择唯一兼容规则，不依赖 HTTP。 */
public final class CompatibilityResolver {
    private final List<CompatibilityRule> rules;

    /** 创建静态矩阵并拒绝重叠范围，避免按文件顺序选择行为。 */
    public CompatibilityResolver(List<CompatibilityRule> rules) {
        this.rules = List.copyOf(rules);
        for (int i = 0; i < rules.size(); i++) {
            var a = rules.get(i);
            for (int j = i + 1; j < rules.size(); j++) {
                var b = rules.get(j);
                if (a.client().equals(b.client())
                        && a.capability().equals(b.capability())
                        && a.contract() == b.contract()
                        && CompatibilityRule.compareVersions(a.minimumVersion(), b.maximumVersion()) < 0
                        && CompatibilityRule.compareVersions(b.minimumVersion(), a.maximumVersion()) < 0)
                    throw new IllegalArgumentException("兼容矩阵包含重叠规则");
            }
        }
    }

    /** @return 用于公开查询的不可变规则 */
    public List<CompatibilityRule> rules() {
        return rules;
    }

    /**
     * 显式路由契约和请求头不一致时拒绝；未显式选择时使用客户端匹配的最高可用契约。
     * @param client 客户端构建声明
     * @param capability 应用能力标识
     * @param routeContract URL 或 Controller 声明的固定契约；同路径协商时为空
     * @return 兼容结果
     */
    public CompatibilityDecision resolve(ClientVersion client, String capability, @Nullable Integer routeContract) {
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
