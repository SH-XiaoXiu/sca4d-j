package com.sca4d.core.api.authentication;

import com.sca4d.core.api.authentication.principalsession.LongIdSet;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** 保存 Principal、静态授权资源和认证记录的不可变 Session 快照。 */
public final class Session {

    private final SessionId id;

    private final Subject subject;

    private final Account account;

    private final List<Principal> principals;

    private final Map<Long, Principal> principalsById;

    private final List<PrincipalIdSet<?>> principalIdSets;

    private final Map<Class<? extends Principal>, LongIdSet> principalIdsByType;

    private final List<Authentication> authentications;

    private final Map<Long, List<Resource>> resourcesByPrincipal;

    private final Map<Long, List<ResourceIdSet>> resourceIdSetsByPrincipal;

    private final Map<Long, Map<String, LongIdSet>> resourceIdsByPrincipal;

    private final Instant issuedAt;

    private final Instant expiresAt;

    /**
     * 创建完整的 Session 快照，并校验身份、认证、Principal、资源和有效时间之间的关系。
     *
     * @param id Session 标识
     * @param subject 完成认证的 Subject
     * @param account 完成认证且属于 {@code subject} 的 Account
     * @param principals 当前 Account 可以直接选择的完整 Principal
     * @param principalIdSets 当前 Account 可以在请求期解析的 Principal 类型和标识
     * @param authentications 当前 Account 的非空成功认证列表
     * @param resourcesByPrincipal 每个完整 Principal 对应的授权资源
     * @param resourceIdSetsByPrincipal 每个完整 Principal 对应的数字授权 Resource 标识
     * @param issuedAt Session 签发时间
     * @param expiresAt 晚于 {@code issuedAt} 的过期时间
     * @throws NullPointerException 任一必需值、集合元素或资源值为 {@code null} 时抛出
     * @throws IllegalArgumentException 身份关系、认证归属、Principal、资源或有效时间不合法时抛出
     */
    public Session(
            SessionId id,
            Subject subject,
            Account account,
            Collection<? extends Principal> principals,
            Collection<? extends PrincipalIdSet<?>> principalIdSets,
            List<Authentication> authentications,
            Map<Long, ? extends Collection<? extends Resource>> resourcesByPrincipal,
            Map<Long, ? extends Collection<ResourceIdSet>> resourceIdSetsByPrincipal,
            Instant issuedAt,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "Session 标识不能为空");
        this.subject = Objects.requireNonNull(subject, "Subject 不能为空");
        this.account = Objects.requireNonNull(account, "Account 不能为空");
        this.authentications = List.copyOf(Objects.requireNonNull(authentications, "成功认证列表不能为空"));
        this.issuedAt = Objects.requireNonNull(issuedAt, "Session 签发时间不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Session 过期时间不能为空");
        if (this.subject.subjectId() != this.account.subjectId()) {
            throw new IllegalArgumentException("Session Account 不属于当前 Subject");
        }
        if (this.authentications.isEmpty()) {
            throw new IllegalArgumentException("Session 必须包含至少一次成功认证");
        }
        if (this.authentications.stream().anyMatch(item -> this.account.accountId() != item.accountId())) {
            throw new IllegalArgumentException("成功认证不属于 Session Account");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Session 过期时间必须晚于签发时间");
        }

        LinkedHashMap<Long, Principal> principalIndex = new LinkedHashMap<>();
        LinkedHashSet<Long> principalIds = new LinkedHashSet<>();
        for (Principal principal : Objects.requireNonNull(principals, "Principal 列表不能为空")) {
            Principal checked = Objects.requireNonNull(principal, "Principal 不能为空");
            requirePositivePrincipalId(checked.principalId());
            if (principalIndex.putIfAbsent(checked.principalId(), checked) != null) {
                throw new IllegalArgumentException("Session 包含重复 Principal: " + checked.principalId());
            }
            principalIds.add(checked.principalId());
        }
        this.principalsById = Map.copyOf(principalIndex);
        this.principals = List.copyOf(principalIndex.values());
        this.principalIdSets = List.copyOf(Objects.requireNonNull(principalIdSets, "Principal 标识集合不能为空"));
        this.principalIdsByType = normalizePrincipalIds(this.principalIdSets, principalIds);
        this.resourcesByPrincipal = normalizeResources(resourcesByPrincipal, principalIndex.keySet());
        this.resourceIdSetsByPrincipal = normalizeResourceIds(
                resourceIdSetsByPrincipal,
                principalIndex.keySet(),
                this.resourcesByPrincipal);
        this.resourceIdsByPrincipal = indexResourceIds(this.resourceIdSetsByPrincipal);
    }

    /** 校验紧凑 Principal 标识的类型唯一性，以及与其他 Principal 标识的全局唯一性。 */
    private static Map<Class<? extends Principal>, LongIdSet> normalizePrincipalIds(
            Collection<? extends PrincipalIdSet<?>> principalIdSets,
            Set<Long> principalIds) {
        LinkedHashMap<Class<? extends Principal>, LongIdSet> normalized = new LinkedHashMap<>();
        for (PrincipalIdSet<?> principalIdSet : principalIdSets) {
            PrincipalIdSet<?> checked = Objects.requireNonNull(principalIdSet, "Principal 标识集合不能为空");
            if (normalized.putIfAbsent(checked.principalType(), checked.ids()) != null) {
                throw new IllegalArgumentException(
                        "Session 包含重复 Principal 标识类型: "
                                + checked.principalType().getName());
            }
            for (long principalId : checked.ids().toArray()) {
                if (!principalIds.add(principalId)) {
                    throw new IllegalArgumentException("Session 包含重复 Principal: " + principalId);
                }
            }
        }
        return Map.copyOf(normalized);
    }

    /**
     * 校验资源索引完整覆盖全部完整 Principal，并复制为不可修改列表。
     *
     * @param resourcesByPrincipal 待校验的 Principal 资源索引
     * @param principalIds 当前 Session 完整装载的 Principal 标识
     * @return 仅包含已校验资源副本的不可修改索引
     */
    private static Map<Long, List<Resource>> normalizeResources(
            Map<Long, ? extends Collection<? extends Resource>> resourcesByPrincipal,
            Set<Long> principalIds) {
        Map<Long, ? extends Collection<? extends Resource>> checked = Objects
                .requireNonNull(resourcesByPrincipal, "Session 授权资源不能为空");
        if (!checked.keySet().equals(principalIds)) {
            throw new IllegalArgumentException("Session 授权资源与可选择 Principal 不完整匹配");
        }
        LinkedHashMap<Long, List<Resource>> normalized = new LinkedHashMap<>();
        for (Long principalId : principalIds) {
            LinkedHashMap<String, Resource> resources = new LinkedHashMap<>();
            for (Resource resource : Objects.requireNonNull(checked.get(principalId), "Principal 授权资源不能为空")) {
                Resource checkedResource = Objects.requireNonNull(resource, "Session 授权资源不能为空");
                String type = TextUtils.requireText(checkedResource.type(), "资源类型");
                String resourceId = TextUtils.requireText(checkedResource.id(), "资源标识");
                String key = type + "\u0000" + resourceId;
                if (resources.putIfAbsent(key, checkedResource) != null) {
                    throw new IllegalArgumentException("Session 包含重复资源: " + type + "/" + resourceId);
                }
            }
            normalized.put(principalId, List.copyOf(resources.values()));
        }
        return Map.copyOf(normalized);
    }

    /** 校验数字 Resource 标识只属于完整 Principal，并拒绝重复类型和重复资源。 */
    private static Map<Long, List<ResourceIdSet>> normalizeResourceIds(
            Map<Long, ? extends Collection<ResourceIdSet>> resourceIdSetsByPrincipal,
            Set<Long> principalIds,
            Map<Long, List<Resource>> resourcesByPrincipal) {
        Map<Long, ? extends Collection<ResourceIdSet>> checked = Objects
                .requireNonNull(resourceIdSetsByPrincipal, "Session 数字 Resource 标识不能为空");
        if (!principalIds.containsAll(checked.keySet())) {
            throw new IllegalArgumentException("Session 数字 Resource 标识包含未知完整 Principal");
        }
        LinkedHashMap<Long, List<ResourceIdSet>> normalized = new LinkedHashMap<>();
        for (Long principalId : principalIds) {
            LinkedHashMap<String, ResourceIdSet> byType = new LinkedHashMap<>();
            Collection<ResourceIdSet> contributed = checked.containsKey(principalId)
                    ? Objects.requireNonNull(checked.get(principalId), "Principal 数字 Resource 标识不能为空")
                    : List.of();
            for (ResourceIdSet resourceIdSet : contributed) {
                ResourceIdSet checkedSet = Objects.requireNonNull(
                        resourceIdSet,
                        "数字 Resource 标识集合不能为空");
                if (byType.putIfAbsent(checkedSet.resourceType(), checkedSet) != null) {
                    throw new IllegalArgumentException(
                            "Session 包含重复数字 Resource 类型: " + checkedSet.resourceType());
                }
                for (Resource resource : resourcesByPrincipal.get(principalId)) {
                    if (!checkedSet.resourceType().equals(resource.type())) {
                        continue;
                    }
                    OptionalLong resourceId = parseCanonicalPositiveId(resource.id());
                    if (resourceId.isPresent() && checkedSet.ids().contains(resourceId.getAsLong())) {
                        throw new IllegalArgumentException(
                                "Session 包含重复资源: " + resource.type() + "/" + resource.id());
                    }
                }
            }
            normalized.put(principalId, List.copyOf(byType.values()));
        }
        return Map.copyOf(normalized);
    }

    /** 按完整 Principal 和 Resource 类型建立数字标识查询索引。 */
    private static Map<Long, Map<String, LongIdSet>> indexResourceIds(
            Map<Long, List<ResourceIdSet>> resourceIdSetsByPrincipal) {
        LinkedHashMap<Long, Map<String, LongIdSet>> indexed = new LinkedHashMap<>();
        for (Map.Entry<Long, List<ResourceIdSet>> entry : resourceIdSetsByPrincipal.entrySet()) {
            LinkedHashMap<String, LongIdSet> byType = new LinkedHashMap<>();
            for (ResourceIdSet resourceIdSet : entry.getValue()) {
                byType.put(resourceIdSet.resourceType(), resourceIdSet.ids());
            }
            indexed.put(entry.getKey(), Map.copyOf(byType));
        }
        return Map.copyOf(indexed);
    }

    /**
     * 读取当前会话的标识。
     *
     * @return Session 标识
     */
    public SessionId id() {
        return id;
    }

    /**
     * 读取建立当前 Session 的接入者。
     *
     * @return 完成认证的 Subject
     */
    public Subject subject() {
        return subject;
    }

    /**
     * 读取建立当前 Session 的登录账号。
     *
     * @return 完成认证且属于当前 Subject 的 Account
     */
    public Account account() {
        return account;
    }

    /**
     * 读取建立 Session 时完整保存的可选业务主体快照。
     *
     * @return 当前 Account 可直接使用的不可修改完整 Principal 列表
     */
    public @Unmodifiable List<Principal> principals() {
        return principals;
    }

    /**
     * 读取建立 Session 时只保存标识的可选 Principal。
     *
     * @return 按装载顺序保存的不可修改 Principal 标识集合
     */
    public @Unmodifiable List<PrincipalIdSet<?>> principalIdSets() {
        return principalIdSets;
    }

    /**
     * 查找当前 Session 中完整装载的 Principal。
     *
     * @param principalId Principal 标识
     * @return 对应的完整 Principal；只存在于紧凑标识集合或不属于当前 Session 时返回空结果
     */
    public Optional<Principal> principal(long principalId) {
        return Optional.ofNullable(principalsById.get(requirePositivePrincipalId(principalId)));
    }

    /**
     * 返回当前 Session 中完整装载的 Principal。
     *
     * @param principalId Principal 标识
     * @return 对应的 Principal
     * @throws IllegalArgumentException Principal 只存在于紧凑标识集合或不属于当前 Session 时抛出
     */
    public Principal principalById(long principalId) {
        return principal(principalId)
                .orElseThrow(() -> new IllegalArgumentException("Principal 不属于当前 Session: " + principalId));
    }

    /**
     * 校验请求期解析的候选 Principal 是否属于当前 Session。
     *
     * <p>完整 Principal 返回 Session 中保存的对象；只保存标识的 Principal 必须同时精确匹配 Java 类型和标识，校验通过后返回候选对象。
     *
     * @param candidate 业务选择器在当前请求中解析的 Principal
     * @return Session 中的完整 Principal，或通过类型和标识校验的候选 Principal
    * @throws NullPointerException 候选 Principal 为 {@code null} 时抛出
    * @throws IllegalArgumentException 类型或标识不属于当前 Session 时抛出
    */
    public Principal principal(Principal candidate) {
        Principal checked = Objects.requireNonNull(candidate, "候选 Principal 不能为空");
        long principalId = requirePositivePrincipalId(checked.principalId());
        Principal materialized = principalsById.get(principalId);
        if (materialized != null) {
            if (!materialized.getClass().equals(checked.getClass())) {
                throw new IllegalArgumentException(
                        "Principal 类型与当前 Session 不匹配: "
                                + checked.getClass().getName());
            }
            return materialized;
        }
        LongIdSet ids = principalIdsByType.get(checked.getClass());
        if (ids != null && ids.contains(principalId)) {
            return checked;
        }
        throw new IllegalArgumentException("Principal 不属于当前 Session: " + principalId);
    }

    /**
     * 判断指定精确 Java 类型和标识是否属于当前 Session。
     *
     * @param principalType Principal 精确 Java 类型
     * @param principalId Principal 标识
     * @return 完整对象或紧凑标识集合同时匹配类型和标识时返回 {@code true}
     */
    public boolean containsPrincipal(
            Class<? extends Principal> principalType,
            long principalId) {
        Class<? extends Principal> checkedType = Objects.requireNonNull(principalType, "Principal 类型不能为空");
        if (principalId <= 0) {
            return false;
        }
        Principal materialized = principalsById.get(principalId);
        if (materialized != null) {
            return materialized.getClass().equals(checkedType);
        }
        LongIdSet ids = principalIdsByType.get(checkedType);
        return ids != null && ids.contains(principalId);
    }

    /**
     * 读取建立 Session 时用于满足认证要求的记录。
     *
     * @return 按输入顺序保存的不可修改成功认证列表
     */
    public @Unmodifiable List<Authentication> authentications() {
        return authentications;
    }

    /**
     * 返回指定 Principal 的静态授权资源快照。
     *
     * @param principalId Principal 标识
     * @return 不可修改的资源列表
     * @throws IllegalArgumentException Principal 不属于当前 Session 时抛出
     */
    public @Unmodifiable List<Resource> resources(long principalId) {
        long checkedPrincipalId = requirePositivePrincipalId(principalId);
        List<Resource> resources = resourcesByPrincipal.get(checkedPrincipalId);
        if (resources == null) {
            if (containsCompactPrincipalId(checkedPrincipalId)) {
                return List.of();
            }
            throw new IllegalArgumentException("Principal 不属于当前 Session: " + checkedPrincipalId);
        }
        return resources;
    }

    /**
     * 返回指定 Principal 的数字静态授权 Resource 标识。
     *
     * @param principalId Principal 标识
     * @return 完整 Principal 对应的不可修改数字 Resource 标识集合；紧凑 Principal 返回空列表
     * @throws IllegalArgumentException Principal 不属于当前 Session 时抛出
     */
    public @Unmodifiable List<ResourceIdSet> resourceIdSets(long principalId) {
        long checkedPrincipalId = requirePositivePrincipalId(principalId);
        List<ResourceIdSet> resourceIds = resourceIdSetsByPrincipal.get(checkedPrincipalId);
        if (resourceIds != null) {
            return resourceIds;
        }
        if (containsCompactPrincipalId(checkedPrincipalId)) {
            return List.of();
        }
        throw new IllegalArgumentException("Principal 不属于当前 Session: " + checkedPrincipalId);
    }

    /**
     * 判断指定 Principal 是否拥有类型和标识均匹配的资源。
     *
     * @param principalId Principal 标识
     * @param resource 待匹配的资源
     * @return 存在匹配资源时为 {@code true}
     * @throws IllegalArgumentException Principal 不属于当前 Session 时抛出
     */
    public boolean grants(long principalId, Resource resource) {
        long checkedPrincipalId = requirePositivePrincipalId(principalId);
        if (!principalsById.containsKey(checkedPrincipalId)
                && !containsCompactPrincipalId(checkedPrincipalId)) {
            throw new IllegalArgumentException("Principal 不属于当前 Session: " + checkedPrincipalId);
        }
        Resource checked = Objects.requireNonNull(resource, "资源不能为空");
        List<Resource> resources = resourcesByPrincipal.get(checkedPrincipalId);
        if (resources != null
                && resources.stream()
                        .anyMatch(item -> item.type().equals(checked.type()) && item.id().equals(checked.id()))) {
            return true;
        }
        Map<String, LongIdSet> resourceIds = resourceIdsByPrincipal.get(checkedPrincipalId);
        if (resourceIds == null) {
            return false;
        }
        LongIdSet ids = resourceIds.get(checked.type());
        if (ids == null) {
            return false;
        }
        OptionalLong parsed = parseCanonicalPositiveId(checked.id());
        return parsed.isPresent() && ids.contains(parsed.getAsLong());
    }

    /** 在不建立每个 Principal 对象索引的情况下查询紧凑 Principal 标识。 */
    private boolean containsCompactPrincipalId(long principalId) {
        return principalIdsByType.values().stream().anyMatch(ids -> ids.contains(principalId));
    }

    /** 把规范的正十进制 Resource 标识转换为紧凑索引使用的 long。 */
    private static OptionalLong parseCanonicalPositiveId(String resourceId) {
        try {
            long parsed = Long.parseLong(Objects.requireNonNull(resourceId, "Resource 标识不能为空"));
            if (parsed > 0 && Long.toString(parsed).equals(resourceId)) {
                return OptionalLong.of(parsed);
            }
            return OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /** 校验调用方传入的 Principal 标识。 */
    private static long requirePositivePrincipalId(long principalId) {
        if (principalId <= 0) {
            throw new IllegalArgumentException("Principal 标识必须为正数");
        }
        return principalId;
    }

    /**
     * 读取当前 Session 开始生效的时刻。
     *
     * @return Session 签发时间
     */
    public Instant issuedAt() {
        return issuedAt;
    }

    /**
     * 读取当前 Session 不再有效的时刻。
     *
     * @return 晚于签发时间的 Session 过期时间
     */
    public Instant expiresAt() {
        return expiresAt;
    }

}
