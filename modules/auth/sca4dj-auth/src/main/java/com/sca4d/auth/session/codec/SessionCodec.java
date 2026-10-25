package com.sca4d.auth.session.codec;

import static com.sca4d.core.api.name.NameFormat.DOT;
import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.exception.AuthException;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.principalsession.LongIdSet;
import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.authentication.principalsession.ResourceCodec;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.roaringbitmap.longlong.LongIterator;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 将完整 {@link Session} 编解码为 Sa-Token 和 JWT 共用的版本化 JSON wire。
 */
public class SessionCodec implements VersionedCodec<Session, String> {

    private static final int FORMAT_VERSION = 1;

    private static final String ID_SET_ENCODING = "roaring64-portable";

    private static final int ID_SET_VERSION = 1;

    private static final @NotNull TreeCode CODE = TreeCode.of("AUTH/SESSION");

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull Map<Class<?>, PrincipalCodec<?>> principalCodecs;

    private final @NotNull Map<CodecKey, PrincipalCodec<?>> principalCodecsByType;

    private final @NotNull Map<Class<?>, ResourceCodec<?>> resourceCodecs;

    private final @NotNull Map<CodecKey, ResourceCodec<?>> resourceCodecsByType;

    /**
     * 创建 Session wire Codec，并按 Java 类型和 wire 类型登记 payload Codec。
     *
     * @param objectMapper    读写完整 Session wire 的 JSON 映射器
     * @param principalCodecs Principal payload Codec
     * @param resourceCodecs  Resource payload Codec
     * @throws IllegalArgumentException 同类 Codec 使用重复的 Java 类型或 wire 类型时抛出
     */
    public SessionCodec(
            @NotNull ObjectMapper objectMapper,
            @NotNull List<@NotNull PrincipalCodec<?>> principalCodecs,
            @NotNull List<@NotNull ResourceCodec<?>> resourceCodecs) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.principalCodecs = codecsByValueType(
                principalCodecs,
                PrincipalCodec::valueType,
                PrincipalCodec::version,
                "Principal Codec 值类型");
        this.principalCodecsByType = codecsByTypeAndVersion(
                principalCodecs,
                PrincipalCodec::type,
                PrincipalCodec::version,
                "Principal Codec 类型");
        this.resourceCodecs = codecsByValueType(
                resourceCodecs,
                ResourceCodec::valueType,
                ResourceCodec::version,
                "Resource Codec 值类型");
        this.resourceCodecsByType = codecsByTypeAndVersion(
                resourceCodecs,
                ResourceCodec::type,
                ResourceCodec::version,
                "Resource Codec 类型");
    }

    /** @return Session wire 的稳定类型编码 */
    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    /** @return 当前 Session wire 结构版本 */
    @Override
    public int version() {
        return FORMAT_VERSION;
    }

    /** @return 当前 Codec 接受的 Session 类型 */
    @Override
    public @NotNull Class<Session> valueType() {
        return Session.class;
    }

    /**
     * 把完整 Session 编码为带格式版本的 JSON wire。
     *
     * @param session 要持久化或传输的 Session
     * @return 包含身份、成功认证记录、资源和有效期的 JSON wire
     * @throws IllegalStateException 缺少具体类型的 Codec 或 JSON 编码失败时抛出
     */
    @Override
    public @NotNull String encode(@NotNull Session session) {
        Session checked = Objects.requireNonNull(session, "Session 不能为空");
        SessionData data = new SessionData();
        data.formatVersion = FORMAT_VERSION;
        data.id = checked.id().value();
        data.subject = new SubjectData(checked.subject());
        data.account = new AccountData(checked.account());
        data.principals = checked.principals().stream().map(this::encodePrincipal).toList();
        data.principalIdSets = checked.principalIdSets().stream().map(this::encodePrincipalIds).toList();
        data.authentications = checked.authentications().stream().map(AuthenticationData::new).toList();
        List<ResourceData> resources = new ArrayList<>();
        List<ResourceIdSetData> resourceIdSets = new ArrayList<>();
        for (Principal principal : checked.principals()) {
            for (Resource resource : checked.resources(principal.principalId())) {
                resources.add(new ResourceData(principal.principalId(), encodeResource(resource)));
            }
            for (ResourceIdSet resourceIdSet : checked.resourceIdSets(principal.principalId())) {
                resourceIdSets.add(
                        new ResourceIdSetData(
                                principal.principalId(),
                                resourceIdSet.resourceType().format(SLASH),
                                ID_SET_VERSION,
                                encodeIds(resourceIdSet.ids())));
            }
        }
        data.resources = List.copyOf(resources);
        data.resourceIdSets = List.copyOf(resourceIdSets);
        data.issuedAt = checked.issuedAt();
        data.expiresAt = checked.expiresAt();
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new AuthException("Session 编码失败", exception);
        }
    }

    /**
     * 从 JSON wire 恢复完整 Session。
     *
     * @param encoded Session JSON wire
     * @return 恢复的 Session
     * @throws IllegalStateException wire 损坏、版本未知、缺少 Codec 或 payload 无法解码时抛出
     */
    @Override
    public @NotNull Session decode(@NotNull String encoded) {
        // 所有解析、校验和类型恢复失败都转换为 IllegalStateException，原始异常保留为 cause；不会返回部分恢复的 Session。
        try {
            SessionData data = objectMapper.readValue(
                    Objects.requireNonNull(encoded, "Session wire 不能为空"),
                    SessionData.class);
            // 只接受当前 FORMAT_VERSION 的 wire，旧版本和未知版本都直接拒绝，不尝试兼容解析。
            if (data.formatVersion != FORMAT_VERSION) {
                throw new IllegalArgumentException("未知 Session wire 版本: " + data.formatVersion);
            }
            SubjectData subjectData = Objects.requireNonNull(data.subject, "Subject wire 不能为空");
            AccountData accountData = Objects.requireNonNull(data.account, "Account wire 不能为空");
            Subject subject = new Subject(
                    subjectData.subjectId,
                    SubjectType.valueOf(TextUtils.requireText(subjectData.type, "Subject 类型")));
            Account account = new Account(
                    accountData.accountId,
                    accountData.subjectId,
                    new RealmKey(accountData.realmKey));
            // Principal 列表是资源和 ID 集合的归属依据，缺失时整个 Session 解码失败。
            List<Principal> principals = Objects.requireNonNull(data.principals, "Principal 列表不能为空")
                    .stream()
                    .map(this::decodePrincipal)
                    .toList();
            List<PrincipalIdSet<?>> principalIdSets = data.principalIdSets == null
                    ? List.of()
                    : data.principalIdSets.stream()
                            .<PrincipalIdSet<?>>map(this::decodePrincipalIds)
                            .toList();
            List<Authentication> authentications = Objects.requireNonNull(
                    data.authentications,
                    "认证记录不能为空")
                    .stream()
                    .map(SessionCodec::decodeAuthentication)
                    .toList();

            // 先为全部 Principal 建立空资源列表，再拒绝 wire 中指向未知 Principal 的关联。
            LinkedHashMap<Long, Collection<? extends Resource>> resources = new LinkedHashMap<>();
            for (Principal principal : principals) {
                resources.put(principal.principalId(), new ArrayList<>());
            }
            for (ResourceData resource : Objects.requireNonNull(data.resources, "Resource 列表不能为空")) {
                ResourceData checkedResource = Objects.requireNonNull(resource, "Resource wire 不能为空");
                Collection<? extends Resource> values = resources.get(checkedResource.principalId);
                if (values == null) {
                    throw new IllegalArgumentException("资源包含未知 Principal: " + checkedResource.principalId);
                }
                @SuppressWarnings("unchecked")
                Collection<Resource> mutableValues = (Collection<Resource>) values;
                mutableValues.add(decodeResource(checkedResource.resource));
            }
            // 数字资源标识集合按 principalId 分组；wire 中缺少该字段时视为没有数字资源标识，结果为空列表。
            LinkedHashMap<Long, Collection<ResourceIdSet>> resourceIdSets = new LinkedHashMap<>();
            if (data.resourceIdSets != null) {
                for (ResourceIdSetData resourceIdSet : data.resourceIdSets) {
                    ResourceIdSetData checkedSet = Objects.requireNonNull(
                            resourceIdSet,
                            "数字 Resource 标识 wire 不能为空");
                    resourceIdSets.computeIfAbsent(checkedSet.principalId, ignored -> new ArrayList<>())
                            .add(
                                    new ResourceIdSet(
                                            TreeCode.of(TextUtils.requireText(checkedSet.type, "Resource 类型")),
                                            decodeIds(checkedSet.version, checkedSet.encoding, checkedSet.payload)));
                }
            }
            return new Session(
                    new SessionId(data.id),
                    subject,
                    account,
                    principals,
                    principalIdSets,
                    authentications,
                    resources,
                    resourceIdSets,
                    Objects.requireNonNull(data.issuedAt, "Session 签发时间不能为空"),
                    Objects.requireNonNull(data.expiresAt, "Session 过期时间不能为空"));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new AuthException("Session 解码失败", exception);
        }
    }

    /**
     * 使用与具体 Principal 类型精确匹配的 Codec 编码 payload。
     */
    private @NotNull TypedPayload encodePrincipal(@NotNull Principal value) {
        Principal checked = Objects.requireNonNull(value, "Principal 不能为空");
        PrincipalCodec<?> codec = principalCodecs.get(checked.getClass());
        if (codec == null) {
            throw new IllegalStateException("缺少 Session Codec: " + checked.getClass().getName());
        }
        return new TypedPayload(codec.type().format(DOT), codec.version(), encode(codec, checked));
    }

    /**
     * 使用与具体 Resource 类型精确匹配的 Codec 编码 payload。
     */
    private @NotNull TypedPayload encodeResource(@NotNull Resource value) {
        Resource checked = Objects.requireNonNull(value, "Resource 不能为空");
        ResourceCodec<?> codec = resourceCodecs.get(checked.getClass());
        if (codec == null) {
            throw new IllegalStateException("缺少 Session Codec: " + checked.getClass().getName());
        }
        return new TypedPayload(codec.type().format(DOT), codec.version(), encode(codec, checked));
    }

    /**
     * 让已经通过精确 Java 类型索引选中的 Codec 编码 Principal。
     */
    private static <P extends Principal> byte @NotNull [] encode(
            @NotNull PrincipalCodec<P> codec,
            @NotNull Principal value) {
        byte[] payload = codec.encode(codec.valueType().cast(value));
        return Objects.requireNonNull(payload, "Session Codec payload 不能为空");
    }

    /**
     * 让已经通过精确 Java 类型索引选中的 Codec 编码 Resource。
     */
    private static <R extends Resource> byte @NotNull [] encode(
            @NotNull ResourceCodec<R> codec,
            @NotNull Resource value) {
        byte[] payload = codec.encode(codec.valueType().cast(value));
        return Objects.requireNonNull(payload, "Session Codec payload 不能为空");
    }

    /**
     * 使用 wire 类型指定的 Codec 恢复并校验 Principal 精确类型。
     */
    private @NotNull Principal decodePrincipal(@NotNull TypedPayload value) {
        TypedPayload checked = Objects.requireNonNull(value, "Principal payload 不能为空");
        PrincipalCodec<?> codec = principalCodecsByType.get(
                new CodecKey(TreeCode.of(checked.type), checked.version));
        if (codec == null) {
            throw new IllegalArgumentException("未知 Session Codec 类型: " + checked.type);
        }
        Principal decoded = Objects.requireNonNull(
                codec.decode(checked.payload()),
                "Session Codec 不能返回 null");
        if (!codec.valueType().equals(decoded.getClass())) {
            throw new IllegalArgumentException("Session Codec 返回类型不匹配");
        }
        return decoded;
    }

    /**
     * 使用 Principal Codec 的 wire type 编码请求期解析的 Principal 标识集合。
     */
    private @NotNull PrincipalIdSetData encodePrincipalIds(@NotNull PrincipalIdSet<?> value) {
        PrincipalIdSet<?> checked = Objects.requireNonNull(value, "Principal 标识集合不能为空");
        PrincipalCodec<?> codec = principalCodecs.get(checked.principalType());
        if (codec == null) {
            throw new IllegalStateException("缺少 Session Codec: " + checked.principalType().getName());
        }
        return new PrincipalIdSetData(
                codec.type().format(DOT),
                codec.version(),
                encodeIds(checked.ids()));
    }

    /**
     * 使用 wire 类型对应的 Principal Codec 恢复精确 Java 类型和可信标识。
     */
    private @NotNull PrincipalIdSet<?> decodePrincipalIds(@NotNull PrincipalIdSetData value) {
        PrincipalIdSetData checked = Objects.requireNonNull(value, "Principal 标识集合 wire 不能为空");
        PrincipalCodec<?> codec = principalCodecsByType.get(
                new CodecKey(
                        TreeCode.of(TextUtils.requireText(checked.type, "Principal Codec 类型")),
                        checked.version));
        if (codec == null) {
            throw new IllegalArgumentException("未知 Session Codec 类型: " + checked.type);
        }
        return createPrincipalIdSet(codec, decodeIds(checked.version, checked.encoding, checked.payload));
    }

    /**
     * 使用已经由 wire 类型选中的 Codec 泛型创建 Principal 标识集合。
     */
    private static <P extends Principal> @NotNull PrincipalIdSet<P> createPrincipalIdSet(
            @NotNull PrincipalCodec<P> codec,
            @NotNull Collection<@NotNull Long> ids) {
        return new PrincipalIdSet<>(codec.valueType(), ids);
    }

    /**
     * 把正数标识集合编码为经过 run 优化的 Roaring64 portable payload。
     */
    private static byte @NotNull [] encodeIds(@NotNull LongIdSet ids) {
        LongIdSet checked = Objects.requireNonNull(ids, "标识集合不能为空");
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap(true);
        for (long id : checked.toArray()) {
            bitmap.addLong(id);
        }
        bitmap.runOptimize();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            bitmap.serializePortable(output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AuthException("Session 标识集合编码失败", exception);
        }
    }

    /**
     * 校验编码名称并把 Roaring64 portable payload 恢复为正数标识集合。
     */
    @SuppressWarnings("ExtractMethodRecommender") // payload、数量和正数校验属于一次完整解码，不拆成步骤方法。
    private static @NotNull List<@NotNull Long> decodeIds(
            int version,
            @NotNull String encoding,
            @NotNull String payload) {
        if (version != ID_SET_VERSION) {
            throw new IllegalArgumentException("未知 Session 标识集合版本: " + version);
        }
        String checkedEncoding = TextUtils.requireText(encoding, "标识集合编码");
        if (!ID_SET_ENCODING.equals(checkedEncoding)) {
            throw new IllegalArgumentException("未知 Session 标识集合编码: " + checkedEncoding);
        }
        byte[] bytes = Base64.getDecoder().decode(TextUtils.requireText(payload, "标识集合 payload"));
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap(true);
        try (ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes);
                DataInputStream input = new DataInputStream(inputBytes)) {
            bitmap.deserializePortable(input);
            if (inputBytes.available() != 0) {
                throw new IllegalArgumentException("Session 标识集合包含尾随数据");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Session 标识集合解码失败", exception);
        }
        long cardinality = bitmap.getLongCardinality();
        if (cardinality <= 0 || cardinality > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Session 标识集合数量不合法: " + cardinality);
        }
        ArrayList<Long> ids = new ArrayList<>((int) cardinality);
        LongIterator iterator = bitmap.getLongIterator();
        while (iterator.hasNext()) {
            long id = iterator.next();
            if (id <= 0) {
                throw new IllegalArgumentException("Session 标识必须为正数: " + id);
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    /**
     * 使用 wire 类型指定的 Codec 恢复并校验 Resource 精确类型。
     */
    private @NotNull Resource decodeResource(@NotNull TypedPayload value) {
        TypedPayload checked = Objects.requireNonNull(value, "Resource payload 不能为空");
        ResourceCodec<?> codec = resourceCodecsByType.get(
                new CodecKey(TreeCode.of(checked.type), checked.version));
        if (codec == null) {
            throw new IllegalArgumentException("未知 Session Codec 类型: " + checked.type);
        }
        Resource decoded = Objects.requireNonNull(
                codec.decode(checked.payload()),
                "Session Codec 不能返回 null");
        if (!codec.valueType().equals(decoded.getClass())) {
            throw new IllegalArgumentException("Session Codec 返回类型不匹配");
        }
        return decoded;
    }

    /**
     * 从 wire 字段恢复一次成功认证记录。
     */
    private static @NotNull Authentication decodeAuthentication(@NotNull AuthenticationData value) {
        AuthenticationData checked = Objects.requireNonNull(value, "认证记录不能为空");
        Set<AuthenticationFactorKind> factorKinds = new LinkedHashSet<>();
        for (String factorKind : Objects.requireNonNull(checked.factorKinds, "认证因素不能为空")) {
            factorKinds.add(AuthenticationFactorKind.valueOf(factorKind));
        }
        return new Authentication(
                checked.accountId,
                TreeCode.ofSlash(checked.methodCode),
                factorKinds,
                Objects.requireNonNull(checked.authenticatedAt, "认证时间不能为空"));
    }

    /**
     * 按 Codec 声明的精确 Java 类型建立唯一索引。
     */
    private static <C> @NotNull Map<Class<?>, C> codecsByValueType(
            @NotNull List<@NotNull C> codecs,
            @NotNull Function<C, Class<?>> key,
            @NotNull Function<C, Integer> version,
            @NotNull String description) {
        LinkedHashMap<Class<?>, C> result = new LinkedHashMap<>();
        for (C codec : List.copyOf(Objects.requireNonNull(codecs, description + "列表不能为空"))) {
            C checkedCodec = Objects.requireNonNull(codec, description + " Codec 不能为空");
            Class<?> checkedKey = Objects.requireNonNull(key.apply(checkedCodec), description + "不能为空");
            C previous = result.putIfAbsent(checkedKey, checkedCodec);
            if (previous != null) {
                int previousVersion = version.apply(previous);
                int checkedVersion = version.apply(checkedCodec);
                if (previousVersion == checkedVersion) {
                    throw new IllegalArgumentException("重复 " + description + ": " + checkedKey);
                }
                if (checkedVersion > previousVersion) {
                    result.put(checkedKey, checkedCodec);
                }
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 按 Codec 声明的 wire type 建立唯一索引。
     */
    private static <C> @NotNull Map<CodecKey, C> codecsByTypeAndVersion(
            @NotNull List<@NotNull C> codecs,
            @NotNull Function<C, TreeCode> key,
            @NotNull Function<C, Integer> version,
            @NotNull String description) {
        LinkedHashMap<CodecKey, C> result = new LinkedHashMap<>();
        for (C codec : List.copyOf(Objects.requireNonNull(codecs, description + "列表不能为空"))) {
            C checkedCodec = Objects.requireNonNull(codec, description + " Codec 不能为空");
            TreeCode checkedType = Objects.requireNonNull(key.apply(checkedCodec), description);
            int checkedVersion = Objects.requireNonNull(version.apply(checkedCodec), description + "版本");
            if (checkedVersion <= 0) {
                throw new IllegalArgumentException(description + "版本必须为正数");
            }
            CodecKey checkedKey = new CodecKey(checkedType, checkedVersion);
            if (result.putIfAbsent(checkedKey, checkedCodec) != null) {
                throw new IllegalArgumentException("重复 " + description + ": " + checkedKey);
            }
        }
        return Map.copyOf(result);
    }

    /** 保存一个开放类型和一个 payload 版本的联合选择键。 */
    @SuppressWarnings("ClassCanBeRecord")
    private static class CodecKey {
        private final @NotNull TreeCode type;

        private final int version;

        private CodecKey(@NotNull TreeCode type, int version) {
            this.type = Objects.requireNonNull(type, "Codec 类型不能为空");
            this.version = version;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof CodecKey key
                            && version == key.version
                            && type.equals(key.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, version);
        }

        @Override
        public String toString() {
            return type + "@" + version;
        }
    }

    /**
     * 保存完整 Session JSON wire 字段。
     */
    private static class SessionData {
        @JsonProperty("formatVersion")
        private int formatVersion;

        @JsonProperty("id")
        private String id;

        @JsonProperty("subject")
        private SubjectData subject;

        @JsonProperty("account")
        private AccountData account;

        @JsonProperty("principals")
        private List<TypedPayload> principals;

        @JsonProperty("principalIdSets")
        private List<PrincipalIdSetData> principalIdSets;

        @JsonProperty("authentications")
        private List<AuthenticationData> authentications;

        @JsonProperty("resources")
        private List<ResourceData> resources;

        @JsonProperty("resourceIdSets")
        private List<ResourceIdSetData> resourceIdSets;

        @JsonProperty("issuedAt")
        private Instant issuedAt;

        @JsonProperty("expiresAt")
        private Instant expiresAt;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private SessionData() {
        }
    }

    /**
     * 保存一种 Principal Codec 类型和 Roaring64 标识集合。
     */
    private static class PrincipalIdSetData {
        @JsonProperty("type")
        private String type;

        @JsonProperty("version")
        private int version;

        @JsonProperty("encoding")
        private String encoding;

        @JsonProperty("payload")
        private String payload;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private PrincipalIdSetData() {
        }

        private PrincipalIdSetData(
                @NotNull String type,
                int version,
                byte @NotNull [] payload) {
            this.type = TextUtils.requireText(type, "Principal Codec 类型");
            this.version = version;
            this.encoding = ID_SET_ENCODING;
            this.payload = Base64.getEncoder().encodeToString(payload);
        }
    }

    /**
     * 保存 Subject 的 Session JSON 字段。
     */
    private static class SubjectData {
        @JsonProperty("subjectId")
        private long subjectId;

        @JsonProperty("type")
        private String type;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private SubjectData() {
        }

        private SubjectData(@NotNull Subject value) {
            subjectId = value.subjectId();
            type = value.type().value();
        }
    }

    /**
     * 保存 Account 的 Session JSON 字段。
     */
    private static class AccountData {
        @JsonProperty("accountId")
        private long accountId;

        @JsonProperty("subjectId")
        private long subjectId;

        @JsonProperty("realmKey")
        private String realmKey;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private AccountData() {
        }

        private AccountData(@NotNull Account value) {
            accountId = value.accountId();
            subjectId = value.subjectId();
            realmKey = value.realmKey().value();
        }
    }

    /**
     * 保存 Codec 类型和 Base64 payload。
     */
    private static class TypedPayload {
        @JsonProperty("type")
        private String type;

        @JsonProperty("version")
        private int version;

        @JsonProperty("payload")
        private String payload;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private TypedPayload() {
        }

        private TypedPayload(
                @NotNull String type,
                int version,
                byte @NotNull [] payload) {
            this.type = TextUtils.requireText(type, "Session Codec 类型");
            this.version = version;
            this.payload = Base64.getEncoder().encodeToString(payload);
        }

        /**
         * 恢复 Codec 原始字节；Base64 无效时抛出参数异常。
         */
        private byte @NotNull [] payload() {
            return Base64.getDecoder().decode(TextUtils.requireText(payload, "Session Codec payload"));
        }
    }

    /**
     * 保存一次成功认证的 Session JSON 字段。
     */
    private static class AuthenticationData {
        @JsonProperty("accountId")
        private long accountId;

        @JsonProperty("methodCode")
        private String methodCode;

        @JsonProperty("factorKinds")
        private List<String> factorKinds;

        @JsonProperty("authenticatedAt")
        private Instant authenticatedAt;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private AuthenticationData() {
        }

        private AuthenticationData(@NotNull Authentication value) {
            accountId = value.accountId();
            methodCode = value.methodCode().format(SLASH);
            factorKinds = value.factorKinds().stream().map(Enum::name).toList();
            authenticatedAt = value.authenticatedAt();
        }
    }

    /**
     * 保存 Principal 与静态授权 Resource 的 Session JSON 关联。
     */
    private static class ResourceData {
        @JsonProperty("principalId")
        private long principalId;

        @JsonProperty("resource")
        private TypedPayload resource;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private ResourceData() {
        }

        private ResourceData(long principalId, @NotNull TypedPayload resource) {
            this.principalId = principalId;
            this.resource = resource;
        }
    }

    /**
     * 保存完整 Principal 与一种数字 Resource 标识集合的 Session JSON 关联。
     */
    private static class ResourceIdSetData {
        @JsonProperty("principalId")
        private long principalId;

        @JsonProperty("type")
        private String type;

        @JsonProperty("version")
        private int version;

        @JsonProperty("encoding")
        private String encoding;

        @JsonProperty("payload")
        private String payload;

        /**
         * 供 Jackson 创建反序列化中间对象。
         */
        @JsonCreator
        private ResourceIdSetData() {
        }

        private ResourceIdSetData(
                long principalId,
                @NotNull String type,
                int version,
                byte @NotNull [] payload) {
            this.principalId = principalId;
            this.type = TextUtils.requireText(type, "Resource 类型");
            this.version = version;
            this.encoding = ID_SET_ENCODING;
            this.payload = Base64.getEncoder().encodeToString(payload);
        }
    }
}
