package com.sca4d.common.access;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.AuthorizationSuccessResponse;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.CacheException;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityTypeName;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 已解析并缓存的 Cedar 资源策略。
 *
 * <p>普通业务通过 {@link Rules#policy(String, Class, String, List, List)} 创建；需要直接使用 Cedar 文件时通过
 * {@link #cedar(CedarPolicy)} 创建。Schema 和 Policy 在实例初始化时完成读取、解析和缓存；每次判断只构造请求
 * Entity，不执行资源 IO，也不重复解析。
 *
 * @param <R> 该策略接受的业务资源类型
 */
public final class Policy<R> {

    private static final AuthorizationEngine ENGINE = new BasicAuthorizationEngine();
    private static final EntityTypeName ACTION_TYPE = EntityTypeName.parse("Action")
            .orElseThrow(() -> new IllegalStateException("Cedar Action 类型初始化失败"));

    private final Class<R> resourceType;
    private final Schema schema;
    private final PolicySet policySet;
    private final RequestFactory<R> requests;

    /**
     * 创建并缓存已经准备完成的 Cedar 策略。
     *
     * @param resourceType 业务资源类型
     * @param schema 已解析的 Cedar Schema
     * @param policySet 已解析的 Cedar PolicySet
     * @param requests 每次访问使用的 Entity 构造函数
     */
    private Policy(
            @NotNull Class<R> resourceType,
            @NotNull Schema schema,
            @NotNull PolicySet policySet,
            @NotNull RequestFactory<R> requests) {
        try {
            ValidationResponse validation = ENGINE.validate(new ValidationRequest(schema, policySet));
            if (!validation.validationPassed()) {
                throw new IllegalArgumentException("Cedar Policy 未通过 Schema 校验: " + validation);
            }
            schema.cache();
            policySet.cache();
        } catch (AuthException | CacheException exception) {
            throw new IllegalArgumentException("Cedar Policy 初始化失败", exception);
        }
        this.resourceType = resourceType;
        this.schema = schema;
        this.policySet = policySet;
        this.requests = requests;
    }

    /**
     * 从独立 Cedar 文件创建高级策略。
     *
     * <p>Schema、Policy 只读取一次。Policy 文件内每条静态策略必须使用 {@code @id} 声明稳定 ID；重复或
     * 缺失 ID 时应用启动失败。
     *
     * @param definition Cedar 文件位置和 Entity 构造契约
     * @return 已完成解析、校验和缓存的策略
     * @param <R> 业务资源类型
     */
    public static <R> @NotNull Policy<R> cedar(@NotNull CedarPolicy<R> definition) {
        try {
            String schemaSource = readResource(definition.getClass(), definition.schemaResource());
            String policySource = readResource(definition.getClass(), definition.policyResource());
            Schema schema = Schema.parse(Schema.JsonOrCedar.Cedar, schemaSource);
            PolicySet parsed = PolicySet.parsePolicies(policySource);
            LinkedHashSet<com.cedarpolicy.model.policy.Policy> policies = new LinkedHashSet<>();
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (com.cedarpolicy.model.policy.Policy policy : parsed.policies) {
                String id = policy.getAnnotation("id");
                if (id == null || id.isBlank() || !ids.add(id)) {
                    throw new IllegalArgumentException("Cedar 静态策略必须声明唯一且非空的 @id: "
                            + definition.policyResource());
                }
                policies.add(new com.cedarpolicy.model.policy.Policy(policy.getSource(), id));
            }
            if (policies.isEmpty()) {
                throw new IllegalArgumentException("Cedar Policy 文件没有静态策略: "
                        + definition.policyResource());
            }
            if (!parsed.templates.isEmpty() || !parsed.templateLinks.isEmpty()) {
                throw new IllegalArgumentException("CedarPolicy 首版只接受静态策略: "
                        + definition.policyResource());
            }
            return new Policy<>(
                    definition.resourceType(),
                    schema,
                    new PolicySet(policies),
                    definition::request);
        } catch (IOException | InternalException exception) {
            throw new IllegalArgumentException("Cedar 文件解析失败", exception);
        }
    }

    /** @return 该 Policy 接受的业务资源类型 */
    public @NotNull Class<R> resourceType() {
        return resourceType;
    }

    /**
     * 判断当前 Principal 是否可以按指定等级访问资源。
     *
     * <p>Cedar 正常返回 Deny 时返回 false；JNI、Entity 或请求校验错误会作为服务端错误继续抛出，不能伪装成
     * 普通无权限结果。
     *
     * @param request 已完成 Principal 选择和 RBAC 判断的当前请求
     * @param action 当前接口声明的资源访问等级
     * @param resource 业务模块已经读取的资源
     * @return Cedar 最终 Decision 为 Allow 时返回 true
     */
    public boolean allows(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @NotNull R resource) {
        if (!resourceType.isInstance(resource)) {
            throw new IllegalArgumentException("Policy 收到错误资源类型: " + resource.getClass().getName());
        }
        CedarRequest cedar = requests.create(request, action, resource);
        AuthorizationRequest authorization = new AuthorizationRequest(
                cedar.getPrincipal().getEUID(),
                ACTION_TYPE.of(action.value()),
                cedar.getResource().getEUID(),
                cedar.getContext(),
                Optional.of(schema),
                true);
        try {
            AuthorizationResponse response = ENGINE.isAuthorized(
                    authorization, policySet, cedar.getEntities());
            if (response.type != AuthorizationResponse.SuccessOrFailure.Success
                    || response.success.isEmpty()) {
                throw new IllegalStateException("Cedar 授权请求失败: " + response);
            }
            AuthorizationSuccessResponse success = response.success.orElseThrow();
            if (!success.getErrors().isEmpty()) {
                throw new IllegalStateException("Cedar Policy 执行错误: " + success.getErrors());
            }
            return success.isAllowed();
        } catch (AuthException exception) {
            throw new IllegalStateException("Cedar 授权执行失败", exception);
        }
    }

    /**
     * 创建 Rules 生成的 Cedar 策略。
     *
     * @param resourceType 业务资源类型
     * @param schemaResource 业务授权模型的绝对 classpath 资源路径
     * @param policyIds 每条策略的稳定 ID
     * @param policySources 与 ID 顺序一致的 Cedar 策略源码
     * @param requests 每次访问使用的 Entity 构造函数
     * @return 已完成解析、校验和缓存的策略
     * @param <R> 业务资源类型
     */
    static <R> @NotNull Policy<R> generated(
            @NotNull Class<R> resourceType,
            @NotNull String schemaResource,
            @NotNull List<String> policyIds,
            @NotNull List<String> policySources,
            @NotNull BiFunction<CurrentRequest, R, CedarRequest> requests) {
        if (policyIds.size() != policySources.size()) {
            throw new IllegalArgumentException("Cedar Policy ID 与源码数量不一致");
        }
        if (!schemaResource.startsWith("/")) {
            throw new IllegalArgumentException("Cedar Schema 必须使用绝对 classpath 路径: " + schemaResource);
        }
        try {
            String schemaSource = readResource(Policy.class, schemaResource);
            Schema schema = Schema.parse(Schema.JsonOrCedar.Cedar, schemaSource);
            Set<com.cedarpolicy.model.policy.Policy> policies = new LinkedHashSet<>();
            for (int index = 0; index < policyIds.size(); index++) {
                policies.add(new com.cedarpolicy.model.policy.Policy(
                        policySources.get(index), policyIds.get(index)));
            }
            return new Policy<>(
                    resourceType,
                    schema,
                    new PolicySet(policies),
                    (request, action, resource) -> requests.apply(request, resource));
        } catch (IOException | InternalException exception) {
            throw new IllegalArgumentException("Cedar Schema 资源或 DSL Policy 无效: " + schemaResource, exception);
        }
    }

    /**
     * 读取 CedarPolicy 实现类引用的 classpath 文件。
     *
     * @param owner 用于解析相对 classpath 路径的实现类
     * @param location 相对实现类或以 {@code /} 开始的绝对 classpath 路径
     * @return UTF-8 文件内容
     * @throws IOException 文件不存在或读取失败时抛出
     */
    private static @NotNull String readResource(
            @NotNull Class<?> owner, @NotNull String location) throws IOException {
        try (InputStream input = owner.getResourceAsStream(location)) {
            if (input == null) {
                throw new IOException("classpath 文件不存在: " + location);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 把当前请求和业务资源转换为一次 Cedar 请求。
     *
     * @param <R> 业务资源类型
     */
    @FunctionalInterface
    private interface RequestFactory<R> {

        /**
         * @param request 已完成 Principal 选择和 RBAC 判断的当前请求
         * @param action 当前资源访问等级
         * @param resource 业务模块资源
         * @return Cedar 判断所需的 Entity 和 Context
         */
        @NotNull
        CedarRequest create(
                @NotNull CurrentRequest request,
                @NotNull AccessAction action,
                @NotNull R resource);
    }
}
