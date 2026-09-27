package com.sca4d.iam.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationNode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 验证 Redis RBAC 权限缓存的范围隔离、空授权和修订号失效行为。 */
class RedisRbacPermissionCacheTest {

    private static final RbacDomain DOMAIN = new RbacDomain("PROJECT");

    private static final RbacScope FIRST_SCOPE = RbacScope.of(DOMAIN, 101L);

    private static final RbacScope SECOND_SCOPE = RbacScope.of(DOMAIN, 102L);

    private static final long PRINCIPAL_ID = 201L;

    private final Map<Key, String> redisValues = new ConcurrentHashMap<>();

    private List<Key> lastReadKeys = List.of();

    private RedisTemplate<Key, String> redis;

    private RedisRbacPermissionCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
    void setUp() {
        redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<Key, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(any()))
                .thenAnswer(
                        invocation -> {
                            lastReadKeys = List.copyOf(invocation.<List<Key>>getArgument(0));
                            return lastReadKeys.stream().map(redisValues::get).toList();
                        });
        doAnswer(
                invocation -> executeScript(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        Arrays.copyOfRange(
                                invocation.getArguments(),
                                2,
                                invocation.getArguments().length)))
                .when(redis)
                .execute(
                        ArgumentMatchers.<RedisScript<Long>>any(),
                        ArgumentMatchers.anyList(),
                        any(Object[].class));
        cache = new RedisRbacPermissionCache(
                redis,
                new ObjectMapper(),
                permissionSelections(TreeCode.ofSegment("READ")));
    }

    @Test
    @DisplayName("Caches empty and non-empty selectors without crossing RBAC scopes")
    void cachesEmptyAndNonEmptySelectorsByScope() {
        Map<RbacScope, List<Long>> requested = Map.of(
                FIRST_SCOPE,
                List.of(PRINCIPAL_ID),
                SECOND_SCOPE,
                List.of(PRINCIPAL_ID));
        RbacPermissionCacheLookup lookup = cache.find(requested);

        cache.put(
                lookup,
                Map.of(
                        FIRST_SCOPE,
                        Map.of(PRINCIPAL_ID, List.of(new PermissionSelector("project:read"))),
                        SECOND_SCOPE,
                        Map.of(PRINCIPAL_ID, List.of())));

        RbacPermissionCacheLookup cached = cache.find(requested);
        assertThat(cached.cachedSelectors().get(FIRST_SCOPE).get(PRINCIPAL_ID))
                .extracting(PermissionSelector::value)
                .containsExactly("PROJECT:READ");
        assertThat(cached.cachedSelectors().get(SECOND_SCOPE).get(PRINCIPAL_ID)).isEmpty();
        assertThat(lastReadKeys).hasSize(4).doesNotHaveDuplicates();
        assertThat(lastReadKeys).allSatisfy(key -> assertThat(key.isUnder(TreeCode.ofColon("IAM:RBAC"))).isTrue());
    }

    @Test
    @DisplayName("Keeps Cache Coordinates Stable When Operation Declaration Order Changes")
    void keepsCoordinatesStableAcrossOperationOrder() {
        cache = new RedisRbacPermissionCache(
                redis,
                new ObjectMapper(),
                permissionSelections(TreeCode.ofSegment("READ"), TreeCode.ofSegment("MANAGE")));
        Map<RbacScope, List<Long>> requested = Map.of(FIRST_SCOPE, List.of(PRINCIPAL_ID));
        RbacPermissionCacheLookup lookup = cache.find(requested);
        cache.put(
                lookup,
                Map.of(
                        FIRST_SCOPE,
                        Map.of(PRINCIPAL_ID, List.of(new PermissionSelector("PROJECTS:ITEMS:READ")))));
        List<Key> originalKeys = List.copyOf(lastReadKeys);

        RedisRbacPermissionCache reordered = new RedisRbacPermissionCache(
                redis,
                new ObjectMapper(),
                permissionSelections(TreeCode.ofSegment("MANAGE"), TreeCode.ofSegment("READ")));
        RbacPermissionCacheLookup cached = reordered.find(requested);

        assertThat(lastReadKeys).isEqualTo(originalKeys);
        assertThat(cached.cachedSelectors().get(FIRST_SCOPE).get(PRINCIPAL_ID))
                .extracting(PermissionSelector::value)
                .containsExactly("PROJECTS:ITEMS:READ");
    }

    @Test
    @DisplayName("Changes Cache Coordinates When Grantable Operations Change")
    void changesCoordinatesWhenGrantableOperationsChange() {
        Map<RbacScope, List<Long>> requested = Map.of(FIRST_SCOPE, List.of(PRINCIPAL_ID));
        RbacPermissionCacheLookup lookup = cache.find(requested);
        cache.put(
                lookup,
                Map.of(
                        FIRST_SCOPE,
                        Map.of(PRINCIPAL_ID, List.of(new PermissionSelector("PROJECTS:ITEMS:READ")))));
        List<Key> originalKeys = List.copyOf(lastReadKeys);

        RedisRbacPermissionCache expanded = new RedisRbacPermissionCache(
                redis,
                new ObjectMapper(),
                permissionSelections(TreeCode.ofSegment("READ"), TreeCode.ofSegment("MANAGE")));
        RbacPermissionCacheLookup cached = expanded.find(requested);

        assertThat(lastReadKeys).isNotEqualTo(originalKeys);
        assertThat(cached.cachedSelectors()).isEmpty();
    }

    @Test
    @DisplayName("Rejects repository results loaded before cache invalidation")
    void rejectsRepositoryResultsLoadedBeforeInvalidation() {
        Map<RbacScope, List<Long>> requested = Map.of(FIRST_SCOPE, List.of(PRINCIPAL_ID));
        RbacPermissionCacheLookup staleLookup = cache.find(requested);

        cache.evict(requested);
        cache.put(
                staleLookup,
                Map.of(
                        FIRST_SCOPE,
                        Map.of(PRINCIPAL_ID, List.of(new PermissionSelector("project:manage")))));

        RbacPermissionCacheLookup current = cache.find(requested);
        assertThat(current.cachedSelectors()).isEmpty();
        assertThat(current.revision(FIRST_SCOPE, PRINCIPAL_ID)).hasValue(1L);
    }

    /** 在测试内按两个生产 Lua 脚本的原子语义维护 Redis String。 */
    private Object executeScript(
            RedisScript<?> script,
            List<Key> keys,
            Object[] arguments) {
        assertThat(script.getSha1()).isNotBlank();
        if (arguments.length == 0) {
            for (int keyIndex = 0; keyIndex < keys.size(); keyIndex += 2) {
                Key revisionKey = keys.get(keyIndex);
                long revision = Long.parseLong(redisValues.getOrDefault(revisionKey, "0"));
                redisValues.put(revisionKey, Long.toString(revision + 1));
                redisValues.remove(keys.get(keyIndex + 1));
            }
            return (long) keys.size() / 2;
        }

        long written = 0;
        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex += 2) {
            int coordinateIndex = keyIndex / 2;
            int argumentIndex = coordinateIndex * 3;
            String currentRevision = redisValues.getOrDefault(keys.get(keyIndex), "0");
            if (currentRevision.equals(arguments[argumentIndex])) {
                redisValues.put(keys.get(keyIndex + 1), (String) arguments[argumentIndex + 1]);
                written++;
            }
        }
        return written;
    }

    /** 创建把指定动作全部授予测试 RBAC 领域的权限选择规则。 */
    private static PermissionSelectionRegistry permissionSelections(TreeCode... actions) {
        OperationNode.ActionDeclaration[] declarations = Arrays.stream(actions)
                .map(action -> OperationNode.action(action, "operation.projects.items.action", 100))
                .toArray(OperationNode.ActionDeclaration[]::new);
        RbacOperationModule module = new RbacOperationModule() {
            @Override
            public OperationNode.@NotNull Module operationTree() {
                return OperationNode.module(
                        TreeCode.ofSegment("PROJECTS"),
                        "operation.projects",
                        100,
                        OperationNode.resource(
                                TreeCode.ofSegment("ITEMS"),
                                "operation.projects.items",
                                100,
                                declarations));
            }

            @Override
            public @NotNull Set<@NotNull RbacDomain> domains() {
                return Set.of(DOMAIN);
            }

            @Override
            public boolean canGrant(
                    @NotNull RbacDomain domain,
                    @NotNull Operation operation) {
                return DOMAIN.equals(domain);
            }
        };
        return new PermissionSelectionRegistry(
                new OperationCatalog(List.of(module)),
                List.of(module));
    }
}
