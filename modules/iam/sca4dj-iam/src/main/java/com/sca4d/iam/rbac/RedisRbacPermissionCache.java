package com.sca4d.iam.rbac;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/** 使用应用基础设施 Redis 按权限目录指纹批量缓存 RBAC 权限选择符。 */
public final class RedisRbacPermissionCache implements RbacPermissionCache {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRbacPermissionCache.class);

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final DefaultRedisScript<Long> CACHE_SELECTORS = script("cache-selectors.lua");

    private static final DefaultRedisScript<Long> INVALIDATE_SELECTORS = script("invalidate-selectors.lua");

    private final @NotNull StringRedisTemplate redis;

    private final @NotNull RuntimeNamespace namespace;

    private final @NotNull String redisSlot;

    private final @NotNull Map<@NotNull RbacDomain, @NotNull String> catalogFingerprints;

    private final @NotNull ObjectMapper objectMapper;

    /**
     * 创建 Redis RBAC 权限缓存。
     *
     * @param redis 脚手架字符串 Redis 模板
     * @param namespace 当前应用运行时命名空间
     * @param objectMapper 脚手架 JSON 映射器
     * @param permissionSelections 应用安装的 RBAC 权限选择规则
     */
    public RedisRbacPermissionCache(
            @NotNull StringRedisTemplate redis,
            @NotNull RuntimeNamespace namespace,
            @NotNull ObjectMapper objectMapper,
            @NotNull PermissionSelectionRegistry permissionSelections) {
        this.redis = Objects.requireNonNull(redis, "Redis 模板不能为空");
        this.namespace = Objects.requireNonNull(namespace, "运行时命名空间不能为空");
        this.redisSlot = "{" + encoded(namespace.applicationId()) + "-iam-rbac}";
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.catalogFingerprints = catalogFingerprints(permissionSelections);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull RbacPermissionCacheLookup find(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
        List<RbacScope> scopes = new ArrayList<>();
        List<Long> principalIds = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        flatten(principalIdsByScope, scopes, principalIds, keys);
        if (keys.isEmpty()) {
            return new RbacPermissionCacheLookup(Map.of(), Map.of());
        }

        // 一次 MGET 原子读取修订号和值，确保每个命中值都属于同时读取的修订号。
        try {
            List<String> values = redis.opsForValue().multiGet(keys);
            if (values == null || values.size() != keys.size()) {
                LOG.warn(
                        "Redis RBAC 批量读取返回了非法数量，跳过本次缓存回填 | expectedCount={} actualCount={}",
                        keys.size(),
                        values == null ? null : values.size());
                return new RbacPermissionCacheLookup(Map.of(), Map.of());
            }
            LinkedHashMap<RbacScope, Map<Long, List<PermissionSelector>>> cached = new LinkedHashMap<>();
            LinkedHashMap<RbacScope, Map<Long, Long>> revisions = new LinkedHashMap<>();
            for (int index = 0; index < scopes.size(); index++) {
                RbacScope scope = scopes.get(index);
                Long principalId = principalIds.get(index);
                String revisionValue = values.get(index * 2);
                long revision = revisionValue == null ? 0 : Long.parseLong(revisionValue);
                if (revision < 0) {
                    throw new IllegalStateException("Redis RBAC 缓存修订号不能为负数");
                }
                revisions.computeIfAbsent(scope, ignored -> new LinkedHashMap<>())
                        .put(principalId, revision);

                String selectorValue = values.get(index * 2 + 1);
                if (selectorValue == null) {
                    continue;
                }
                try {
                    List<PermissionSelector> selectors = objectMapper.readValue(selectorValue, STRING_LIST)
                            .stream()
                            .map(PermissionSelector::new)
                            .toList();
                    cached.computeIfAbsent(scope, ignored -> new LinkedHashMap<>())
                            .put(principalId, selectors);
                } catch (JsonProcessingException | IllegalArgumentException exception) {
                    LOG.warn(
                            "Redis RBAC 缓存值无效，改为读取 Repository | scope={} principalId={}",
                            scope,
                            principalId,
                            exception);
                }
            }
            return new RbacPermissionCacheLookup(cached, revisions);
        } catch (RuntimeException exception) {
            LOG.warn("Redis RBAC 批量读取失败，跳过本次缓存回填", exception);
            return new RbacPermissionCacheLookup(Map.of(), Map.of());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void put(
            @NotNull RbacPermissionCacheLookup lookup,
            @NotNull Map<@NotNull RbacScope, ? extends Map<@NotNull Long, ? extends List<@NotNull PermissionSelector>>> selectorsByScope) {
        RbacPermissionCacheLookup checkedLookup = Objects.requireNonNull(lookup, "缓存读取结果不能为空");
        List<String> keys = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        int requestedCount = 0;
        try {
            for (Map.Entry<RbacScope, ? extends Map<Long, ? extends List<PermissionSelector>>> scopeEntry : Objects
                    .requireNonNull(selectorsByScope, "待缓存权限选择符不能为空")
                    .entrySet()) {
                RbacScope scope = Objects.requireNonNull(scopeEntry.getKey(), "RBAC 范围不能为空");
                for (Map.Entry<Long, ? extends List<PermissionSelector>> principalEntry : Objects
                        .requireNonNull(scopeEntry.getValue(), "Principal 权限选择符不能为空")
                        .entrySet()) {
                    Long principalId = Objects.requireNonNull(principalEntry.getKey(), "Principal 标识不能为空");
                    OptionalLong revision = checkedLookup.revision(scope, principalId);
                    if (revision.isEmpty()) {
                        continue;
                    }
                    List<String> selectors = List.copyOf(
                            Objects.requireNonNull(
                                    principalEntry.getValue(),
                                    "权限选择符列表不能为空"))
                            .stream()
                            .map(PermissionSelector::value)
                            .toList();
                    keys.add(revisionKey(scope, principalId));
                    keys.add(selectorsKey(scope, principalId));
                    arguments.add(Long.toString(revision.getAsLong()));
                    arguments.add(objectMapper.writeValueAsString(selectors));
                    arguments.add(Long.toString(TTL.toMillis()));
                    requestedCount++;
                }
            }
            if (keys.isEmpty()) {
                return;
            }

            // Lua 比较读取时修订号；权限变化已经增加修订号时拒绝旧 Repository 结果回填。
            Long written = redis.execute(CACHE_SELECTORS, keys, arguments.toArray());
            LOG.trace(
                    "Redis RBAC 批量回填完成 | requestedCount={} writtenCount={}",
                    requestedCount,
                    written);
        } catch (JsonProcessingException | RuntimeException exception) {
            LOG.warn("Redis RBAC 批量写入失败，后续请求将读取 Repository", exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void evict(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope) {
        List<RbacScope> scopes = new ArrayList<>();
        List<Long> principalIds = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        flatten(principalIdsByScope, scopes, principalIds, keys);
        if (keys.isEmpty()) {
            return;
        }
        try {
            // 修订号和缓存值在一个 Lua 调用中改变，已经开始的旧读取无法再次写回。
            redis.execute(INVALIDATE_SELECTORS, keys);
        } catch (RuntimeException exception) {
            LOG.warn("Redis RBAC 缓存批量失效失败，旧值将在 TTL 后失效", exception);
        }
    }

    /** 把嵌套批量输入按稳定迭代顺序转换为缓存坐标和 Redis key。 */
    private void flatten(
            @NotNull Map<@NotNull RbacScope, ? extends Collection<@NotNull Long>> principalIdsByScope,
            @NotNull List<RbacScope> scopes,
            @NotNull List<Long> principalIds,
            @NotNull List<String> keys) {
        for (Map.Entry<RbacScope, ? extends Collection<Long>> entry : Objects
                .requireNonNull(principalIdsByScope, "RBAC 批量缓存输入不能为空")
                .entrySet()) {
            RbacScope scope = Objects.requireNonNull(entry.getKey(), "RBAC 范围不能为空");
            for (Long principalId : Objects.requireNonNull(entry.getValue(), "Principal 标识集合不能为空")) {
                Long checkedPrincipalId = Objects.requireNonNull(principalId, "Principal 标识不能为空");
                scopes.add(scope);
                principalIds.add(checkedPrincipalId);
                keys.add(revisionKey(scope, checkedPrincipalId));
                keys.add(selectorsKey(scope, checkedPrincipalId));
            }
        }
    }

    /** @return 指定缓存坐标的修订号 key */
    private @NotNull String revisionKey(@NotNull RbacScope scope, @NotNull Long principalId) {
        return coordinateKey(scope, principalId) + ":revision";
    }

    /** @return 指定缓存坐标的权限选择符 key */
    private @NotNull String selectorsKey(@NotNull RbacScope scope, @NotNull Long principalId) {
        return coordinateKey(scope, principalId) + ":selectors";
    }

    /** @return 包含应用、RBAC 领域、具体范围和 Principal 的稳定缓存坐标 */
    private @NotNull String coordinateKey(@NotNull RbacScope scope, @NotNull Long principalId) {
        String catalogFingerprint = catalogFingerprints.get(scope.domain());
        if (catalogFingerprint == null) {
            throw new IllegalArgumentException("应用没有安装对应的 RBAC 权限选择规则: " + scope.domain());
        }
        return namespace.sca4dj(
                "iam",
                "rbac",
                redisSlot,
                "permissions",
                scope.domain().value(),
                catalogFingerprint,
                scope.global() ? "GLOBAL" : Long.toString(scope.businessId()),
                Objects.requireNonNull(principalId, "Principal 标识不能为空").toString());
    }

    /** @return 每个 RBAC 领域按 Operation 编码生成的稳定目录指纹 */
    private static @NotNull Map<@NotNull RbacDomain, @NotNull String> catalogFingerprints(
            @NotNull PermissionSelectionRegistry permissionSelections) {
        PermissionSelectionRegistry registry = Objects.requireNonNull(
                permissionSelections,
                "RBAC 权限选择规则不能为空");
        LinkedHashMap<RbacDomain, String> fingerprints = new LinkedHashMap<>();
        registry.domains()
                .stream()
                .sorted(Comparator.comparing(RbacDomain::value))
                .forEach(
                        domain -> fingerprints.put(
                                domain,
                                catalogFingerprint(registry.selection(domain).operations())));
        return Map.copyOf(fingerprints);
    }

    /** @return 排序后 Operation 编码的 SHA-256 Base64URL 指纹 */
    private static @NotNull String catalogFingerprint(
            @NotNull Collection<@NotNull Operation> operations) {
        StringBuilder canonical = new StringBuilder();
        Objects.requireNonNull(operations, "RBAC Operation 集合不能为空")
                .stream()
                .map(operation -> Objects.requireNonNull(operation, "RBAC Operation 不能为空").value())
                .sorted()
                .forEach(value -> canonical.append(value).append('\0'));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(DigestUtil.sha256(canonical.toString()));
    }

    /** @return UTF-8 文本的无填充 Base64URL 编码 */
    private static @NotNull String encoded(@NotNull String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        Objects.requireNonNull(value, "命名值不能为空").getBytes(StandardCharsets.UTF_8));
    }

    /** 从 classpath 加载返回 Long 的 RBAC 权限缓存 Redis 脚本。 */
    private static @NotNull DefaultRedisScript<Long> script(@NotNull String fileName) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/iam/rbac/" + fileName));
        script.setResultType(Long.class);
        return script;
    }
}
