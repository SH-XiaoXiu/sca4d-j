package com.sca4d.common.access;

import com.cedarpolicy.model.Context;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 常用资源关系的类型安全 Cedar Rule 工厂。
 */
public final class Rules {

    private static final String IDENTIFIER_PATTERN = "[A-Za-z][A-Za-z0-9_]*";

    private Rules() {
    }

    /**
     * 创建普通业务使用的 Cedar Policy。
     *
     * @param name 用于 Cedar Policy ID 的稳定名称
     * @param resourceType Rule 读取的业务资源类型
     * @param schemaResource 业务授权模型的绝对 classpath 资源路径
     * @param permits 任意一条成立即可允许的条件
     * @param forbids 任意一条成立都拒绝的条件；优先级高于 permits
     * @return 已完成解析、Schema 校验和缓存的 Policy
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Policy<R> policy(
            @NotNull String name,
            @NotNull Class<R> resourceType,
            @NotNull String schemaResource,
            @NotNull List<Rule<R>> permits,
            @NotNull List<Rule<R>> forbids) {
        if (!name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Policy 名称只能使用小写字母、数字和连字符");
        }
        if (permits.isEmpty()) {
            throw new IllegalArgumentException("Policy 至少需要一条 permit Rule");
        }
        String cedarResourceType = resourceType.getSimpleName();
        if (!cedarResourceType.matches(IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("资源类型不能转换为 Cedar Entity 类型: " + cedarResourceType);
        }

        LinkedHashMap<String, Rule.Attribute<R>> attributes = new LinkedHashMap<>();
        List<String> policies = new ArrayList<>();
        List<String> policyIds = new ArrayList<>();
        boolean requestTime = false;
        int permitIndex = 0;
        for (Rule<R> rule : permits) {
            mergeAttributes(attributes, rule.attributes());
            requestTime = requestTime || rule.requestTime();
            permitIndex++;
            policyIds.add(name + "-permit-" + permitIndex);
            policies.add("permit(principal, action, resource) when { " + rule.expression() + " };");
        }
        int forbidIndex = 0;
        for (Rule<R> rule : forbids) {
            mergeAttributes(attributes, rule.attributes());
            requestTime = requestTime || rule.requestTime();
            forbidIndex++;
            policyIds.add(name + "-forbid-" + forbidIndex);
            policies.add("forbid(principal, action, resource) when { " + rule.expression() + " };");
        }

        boolean policyUsesRequestTime = requestTime;

        return Policy.generated(
                resourceType,
                schemaResource,
                policyIds,
                policies,
                (request, resource) -> {
                    Principal principal = request.requirePrincipal();
                    Map<String, Value> principalAttributes = new LinkedHashMap<>();
                    principalAttributes.put("kind", new PrimString(principal.requirePrincipalType().value()));
                    principalAttributes.put("principalId", new PrimLong(principal.requirePrincipalId()));
                    if (principal.getTenantId() != null) {
                        principalAttributes.put("tenantId", new PrimLong(principal.getTenantId()));
                    }
                    Entity principalEntity = new Entity(
                            entityType("Principal").of(principal.requirePrincipalId().toString()),
                            principalAttributes,
                            Set.of());

                    Map<String, Value> resourceAttributes = new LinkedHashMap<>();
                    for (Rule.Attribute<R> attribute : attributes.values()) {
                        Value value = attribute.value(resource);
                        if (value != null) {
                            resourceAttributes.put(attribute.name(), value);
                        } else if (!attribute.optional()) {
                            throw new IllegalStateException("Cedar 必填资源属性没有值: " + attribute.name());
                        }
                    }
                    Entity resourceEntity = new Entity(
                            entityType(cedarResourceType).of("resource"),
                            resourceAttributes,
                            Set.of());
                    Context context = new Context();
                    if (policyUsesRequestTime) {
                        OffsetDateTime startedAt = request.getStartedAt();
                        if (startedAt == null) {
                            throw new IllegalStateException("Rules.time 要求 CurrentRequest 包含 startedAt");
                        }
                        context = new Context(Map.of(
                                "requestTime", new PrimLong(startedAt.toInstant().toEpochMilli())));
                    }
                    return new CedarRequest(principalEntity, resourceEntity, context, Set.of());
                });
    }

    /**
     * 要求全部条件成立。
     *
     * @param rules 参与判断的条件
     * @return 使用 Cedar {@code &&} 组合的条件
     * @param <R> 业务资源类型
     */
    @SafeVarargs
    public static <R> @NotNull Rule<R> all(@NotNull Rule<R>... rules) {
        return combine("&&", rules);
    }

    /**
     * 要求至少一个条件成立。
     *
     * @param rules 参与判断的条件
     * @return 使用 Cedar {@code ||} 组合的条件
     * @param <R> 业务资源类型
     */
    @SafeVarargs
    public static <R> @NotNull Rule<R> any(@NotNull Rule<R>... rules) {
        return combine("||", rules);
    }

    /**
     * 反转一个条件。
     *
     * @param rule 被反转的条件
     * @return 使用 Cedar {@code !} 生成的条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> not(@NotNull Rule<R> rule) {
        return new Rule<>(
                "!(" + rule.expression() + ")", rule.attributes(), rule.requestTime());
    }

    /**
     * 判断当前 Principal 类型。
     *
     * @param type 允许的 Principal 类型
     * @return 比较当前 Principal 类型的条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> principal(@NotNull PrincipalType type) {
        return new Rule<>("principal.kind == \"" + type.value() + "\"", Map.of());
    }

    /**
     * 判断 TENANT Principal 是否访问自己的租户资源。
     *
     * @param tenantId 读取资源所属租户；集合资源可以返回 {@code null}
     * @param collection 判断资源是否表示经过 Criteria 过滤的集合
     * @return 租户相等或集合读取成立的条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> tenant(
            @NotNull Function<R, Long> tenantId,
            @NotNull Predicate<R> collection) {
        Rule.Attribute<R> tenantAttribute = new Rule.Attribute<>(
                "tenantId", "Long", true, resource -> {
                    Long value = tenantId.apply(resource);
                    return value == null ? null : new PrimLong(value);
                });
        Rule.Attribute<R> collectionAttribute = new Rule.Attribute<>(
                "collection", "Bool", false, resource -> new PrimBool(collection.test(resource)));
        return new Rule<>(
                "principal.kind == \"" + PrincipalType.TENANT.value()
                        + "\" && (resource.collection || (principal has tenantId && resource has tenantId"
                        + " && resource.tenantId == principal.tenantId))",
                Map.of(
                        tenantAttribute.name(), tenantAttribute,
                        collectionAttribute.name(), collectionAttribute));
    }

    /**
     * 判断资源是否属于当前 Principal。
     *
     * @param ownerPrincipalId 读取资源所有者 Principal 主键
     * @return 比较资源所有者与当前 Principal 的条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> owner(
            @NotNull Function<R, Long> ownerPrincipalId) {
        Rule.Attribute<R> attribute = new Rule.Attribute<>(
                "ownerPrincipalId", "Long", false, resource -> {
                    Long value = ownerPrincipalId.apply(resource);
                    return value == null ? null : new PrimLong(value);
                });
        return new Rule<>(
                "resource.ownerPrincipalId == principal.principalId",
                Map.of(attribute.name(), attribute));
    }

    /**
     * 使用业务模块已经读取的成员关系参与 Cedar 判断。
     *
     * @param member 当前 Principal 是否为资源成员
     * @return 要求成员关系成立的条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> member(@NotNull Predicate<R> member) {
        return flag("member", member);
    }

    /**
     * 使用业务模块已经读取的显式授权关系参与 Cedar 判断。
     *
     * @param granted 当前 Principal 是否获得资源授权
     * @return 要求显式授权成立的条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> grant(@NotNull Predicate<R> granted) {
        return flag("granted", granted);
    }

    /**
     * 判断字符串资源属性是否等于固定值。
     *
     * @param name Cedar Resource 属性名
     * @param value 从业务资源读取属性值的函数
     * @param expected 规则要求的固定值
     * @return 字符串属性相等条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> attribute(
            @NotNull String name,
            @NotNull Function<R, String> value,
            @NotNull String expected) {
        requireIdentifier(name);
        Rule.Attribute<R> attribute = new Rule.Attribute<>(
                name, "String", false, resource -> {
                    String actual = value.apply(resource);
                    return actual == null ? null : new PrimString(actual);
                });
        return new Rule<>(
                "resource." + name + " == " + new PrimString(expected).toCedarExpr(),
                Map.of(name, attribute));
    }

    /**
     * 判断请求开始时间是否位于资源允许的半开时间区间内。
     *
     * <p>开始时间为空表示不限制下界，结束时间为空表示不限制上界；结束时刻本身不再允许访问。
     *
     * @param activeFrom 读取资源生效时间的函数
     * @param activeUntil 读取资源失效时间的函数
     * @return 使用 CurrentRequest.startedAt 判断有效期的条件
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Rule<R> time(
            @NotNull Function<R, OffsetDateTime> activeFrom,
            @NotNull Function<R, OffsetDateTime> activeUntil) {
        Rule.Attribute<R> fromAttribute = new Rule.Attribute<>(
                "activeFrom", "Long", true, resource -> {
                    OffsetDateTime value = activeFrom.apply(resource);
                    return value == null ? null : new PrimLong(value.toInstant().toEpochMilli());
                });
        Rule.Attribute<R> untilAttribute = new Rule.Attribute<>(
                "activeUntil", "Long", true, resource -> {
                    OffsetDateTime value = activeUntil.apply(resource);
                    return value == null ? null : new PrimLong(value.toInstant().toEpochMilli());
                });
        return new Rule<>(
                "(if resource has activeFrom then context.requestTime >= resource.activeFrom else true)"
                        + " && (if resource has activeUntil then context.requestTime < resource.activeUntil else true)",
                Map.of(
                        fromAttribute.name(), fromAttribute,
                        untilAttribute.name(), untilAttribute),
                true);
    }

    /**
     * 合并多个条件并拒绝空组合。
     *
     * @param operator Cedar 布尔运算符
     * @param rules 待组合条件
     * @return 带括号的组合条件
     * @param <R> 业务资源类型
     */
    @SafeVarargs
    private static <R> @NotNull Rule<R> combine(
            @NotNull String operator, @NotNull Rule<R>... rules) {
        if (rules.length == 0) {
            throw new IllegalArgumentException("Rule 组合不能为空");
        }
        LinkedHashMap<String, Rule.Attribute<R>> attributes = new LinkedHashMap<>();
        List<String> expressions = new ArrayList<>();
        boolean requestTime = false;
        for (Rule<R> rule : rules) {
            mergeAttributes(attributes, rule.attributes());
            expressions.add("(" + rule.expression() + ")");
            requestTime = requestTime || rule.requestTime();
        }
        return new Rule<>(
                String.join(" " + operator + " ", expressions), attributes, requestTime);
    }

    /**
     * 创建一个由业务布尔值投影的 Cedar 条件。
     *
     * @param name Cedar Resource 属性名
     * @param value 从业务资源读取布尔值的函数
     * @return 要求该属性为 true 的条件
     * @param <R> 业务资源类型
     */
    private static <R> @NotNull Rule<R> flag(
            @NotNull String name, @NotNull Predicate<R> value) {
        Rule.Attribute<R> attribute = new Rule.Attribute<>(
                name, "Bool", false, resource -> new PrimBool(value.test(resource)));
        return new Rule<>("resource." + name, Map.of(name, attribute));
    }

    /**
     * 合并条件引用的资源属性并拒绝同名异构定义。
     *
     * @param target Policy 已收集的属性
     * @param source 当前 Rule 引用的属性
     * @param <R> 业务资源类型
     */
    private static <R> void mergeAttributes(
            @NotNull Map<String, Rule.Attribute<R>> target,
            @NotNull Map<String, Rule.Attribute<R>> source) {
        for (Rule.Attribute<R> attribute : source.values()) {
            Rule.Attribute<R> existing = target.get(attribute.name());
            if (existing != null
                    && (!existing.type().equals(attribute.type())
                            || existing.optional() != attribute.optional())) {
                throw new IllegalArgumentException("Cedar 资源属性存在冲突定义: " + attribute.name());
            }
            target.putIfAbsent(attribute.name(), attribute);
        }
    }

    /**
     * 校验业务属性名可以安全写入 Cedar Schema。
     *
     * @param name 待校验属性名
     */
    private static void requireIdentifier(@NotNull String name) {
        if (!name.matches(IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("非法 Cedar 属性名: " + name);
        }
    }

    /**
     * 解析固定 Cedar Entity 类型名。
     *
     * @param name 已通过项目生成规则校验的类型名
     * @return Cedar Entity 类型
     */
    private static @NotNull EntityTypeName entityType(@NotNull String name) {
        return EntityTypeName.parse(name)
                .orElseThrow(() -> new IllegalArgumentException("非法 Cedar Entity 类型: " + name));
    }
}
