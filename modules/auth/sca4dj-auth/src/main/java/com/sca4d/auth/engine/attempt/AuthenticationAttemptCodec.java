package com.sca4d.auth.engine.attempt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.data.DataAssertionException;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 以显式结构编解码不含秘密的动作认证尝试。 */
final class AuthenticationAttemptCodec {

    private final @NotNull ObjectMapper objectMapper;

    /** @param objectMapper 用于读写稳定 JSON 结构的映射器 */
    AuthenticationAttemptCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "认证尝试 JSON 映射器不能为空");
    }

    /** 把认证尝试编码为不包含 Proof、Credential 或 Secret 的 JSON。 */
    @NotNull
    String encode(@NotNull AuthenticationAttemptState state) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", 1);
        root.put("attemptId", state.id().value());
        root.put("accountId", state.account().accountId());
        root.put("subjectId", state.account().subjectId());
        root.put("realmKey", state.account().realmKey().value());
        root.put("actionCode", state.actionCode().value());
        root.put("expiresAt", state.expiresAt().toString());
        root.put("satisfied", state.satisfied());

        ArrayNode reusable = root.putArray("reusableAuthentications");
        state.reusableAuthentications()
                .forEach(authentication -> encodeAuthentication(reusable.addObject(), authentication));

        ArrayNode verifications = root.putArray("verifications");
        for (VerifiedAuthentication verification : state.verifications()) {
            ObjectNode item = verifications.addObject();
            encodeAuthentication(item, verification.authentication());
            item.put("reference", verification.reference());
            item.put("credentialVersion", verification.version().value());
            verification.validUntil().ifPresent(value -> item.put("validUntil", value.toString()));
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("认证尝试编码失败", exception);
        }
    }

    /** 从稳定 JSON 结构恢复受约束的认证尝试。 */
    @NotNull
    AuthenticationAttemptState decode(@NotNull String encoded) {
        try {
            JsonNode root = objectMapper.readTree(encoded);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("认证尝试必须是 JSON 对象");
            }
            JsonNode version = root.required("version");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1) {
                throw new IllegalArgumentException("认证尝试版本不受支持");
            }

            JsonNode accountIdNode = root.required("accountId");
            if (!accountIdNode.isIntegralNumber()
                    || !accountIdNode.canConvertToLong()
                    || accountIdNode.longValue() <= 0) {
                throw new IllegalArgumentException("认证尝试字段必须为正整数: accountId");
            }
            JsonNode subjectIdNode = root.required("subjectId");
            if (!subjectIdNode.isIntegralNumber()
                    || !subjectIdNode.canConvertToLong()
                    || subjectIdNode.longValue() <= 0) {
                throw new IllegalArgumentException("认证尝试字段必须为正整数: subjectId");
            }
            Account account = new Account(
                    accountIdNode.longValue(),
                    subjectIdNode.longValue(),
                    new RealmKey(DataOps.s(root.required("realmKey").textValue()).req("realmKey").get()));

            JsonNode satisfied = root.required("satisfied");
            if (!satisfied.isBoolean()) {
                throw new IllegalArgumentException("认证尝试字段必须为布尔值: satisfied");
            }
            return new AuthenticationAttemptState(
                    new AuthenticationAttemptId(
                            DataOps.s(root.required("attemptId").textValue()).req("attemptId").get()),
                    account,
                    new AuthenticationActionCode(
                            DataOps.s(root.required("actionCode").textValue()).req("actionCode").get()),
                    decodeAuthentications(root.required("reusableAuthentications"), account),
                    decodeVerifications(root.required("verifications"), account),
                    Instant.parse(DataOps.s(root.required("expiresAt").textValue()).req("expiresAt").get()),
                    satisfied.booleanValue());
        } catch (JsonProcessingException
                | DataAssertionException
                | DateTimeParseException
                | IllegalArgumentException exception) {
            throw new IllegalStateException("认证尝试内容无效", exception);
        }
    }

    /** 把认证记录写入指定 JSON 对象，并稳定排序因素类别。 */
    private static void encodeAuthentication(
            @NotNull ObjectNode target,
            @NotNull Authentication authentication) {
        target.put("methodCode", authentication.methodCode().value());
        ArrayNode kinds = target.putArray("factorKinds");
        authentication.factorKinds().stream().map(Enum::name).sorted().forEach(kinds::add);
        target.put("authenticatedAt", authentication.authenticatedAt().toString());
    }

    /** 解码调用方提供且可能被策略复用的认证记录。 */
    private static @NotNull @Unmodifiable List<Authentication> decodeAuthentications(
            @NotNull JsonNode values,
            @NotNull Account account) {
        if (!values.isArray()) {
            throw new IllegalArgumentException("可复用认证记录不是数组");
        }
        List<Authentication> result = new ArrayList<>();
        values.forEach(value -> result.add(decodeAuthentication(value, account)));
        return List.copyOf(result);
    }

    /** 解码本次尝试新完成的认证和非秘密证据引用。 */
    private static @NotNull @Unmodifiable List<VerifiedAuthentication> decodeVerifications(
            @NotNull JsonNode values,
            @NotNull Account account) {
        if (!values.isArray()) {
            throw new IllegalArgumentException("认证验证结果不是数组");
        }
        List<VerifiedAuthentication> result = new ArrayList<>();
        for (JsonNode value : values) {
            Authentication authentication = decodeAuthentication(value, account);
            JsonNode credentialVersion = value.required("credentialVersion");
            if (!credentialVersion.isIntegralNumber()
                    || !credentialVersion.canConvertToLong()
                    || credentialVersion.longValue() < 0) {
                throw new IllegalArgumentException("认证尝试字段必须为非负整数: credentialVersion");
            }
            result.add(
                    new VerifiedAuthentication(
                            account,
                            authentication,
                            DataOps.s(value.required("reference").textValue()).req("reference").get(),
                            new Version(credentialVersion.longValue()),
                            optionalInstant(value, "validUntil")));
        }
        return List.copyOf(result);
    }

    /** 解码可选 ISO-8601 时间字段。 */
    private static @Nullable Instant optionalInstant(JsonNode value, String fieldName) {
        JsonNode field = value.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return Instant.parse(DataOps.s(field.textValue()).req(fieldName).get());
    }

    /** 解码属于指定账号的单条认证记录。 */
    private static @NotNull Authentication decodeAuthentication(
            @NotNull JsonNode value,
            @NotNull Account account) {
        return new Authentication(
                account.accountId(),
                new AuthenticationMethodCode(
                        DataOps.s(value.required("methodCode").textValue()).req("methodCode").get()),
                factorKinds(value.required("factorKinds")),
                Instant.parse(
                        DataOps.s(value.required("authenticatedAt").textValue()).req("authenticatedAt").get()));
    }

    /** 解码非空认证因素类别集合。 */
    private static @NotNull @Unmodifiable Set<AuthenticationFactorKind> factorKinds(
            @NotNull JsonNode values) {
        if (!values.isArray()) {
            throw new IllegalArgumentException("认证因素类别不是数组");
        }
        EnumSet<AuthenticationFactorKind> result = EnumSet.noneOf(AuthenticationFactorKind.class);
        values.forEach(
                value -> result.add(
                        AuthenticationFactorKind.valueOf(
                                DataOps.s(value.textValue()).req("factorKinds").get())));
        if (result.isEmpty()) {
            throw new IllegalArgumentException("认证记录没有因素类别");
        }
        return Set.copyOf(result);
    }
}
