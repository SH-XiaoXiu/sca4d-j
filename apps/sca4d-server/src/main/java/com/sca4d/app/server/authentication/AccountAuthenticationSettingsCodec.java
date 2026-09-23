package com.sca4d.app.server.authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.setting.SettingCodec;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 把账号认证策略转换为不包含 Account 标识的稳定 JSON 文档。 */
public final class AccountAuthenticationSettingsCodec implements SettingCodec<AccountAuthenticationSettings> {

    private static final Set<String> ROOT_FIELDS = Set.of(
            "loginFactorCount",
            "sensitiveActionFactorCount",
            "reusableAuthenticationMaximumAgeSeconds",
            "methods");

    private static final Set<String> METHOD_FIELDS = Set.of(
            "methodCode",
            "loginStartEnabled",
            "loginAdditionalEnabled",
            "sensitiveActionEnabled");

    /** 创建账号认证策略 Codec。 */
    public AccountAuthenticationSettingsCodec() {
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String encode(@NotNull AccountAuthenticationSettings value) {
        return encodeJson(value).toString();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull AccountAuthenticationSettings decode(@NotNull String value) {
        try {
            return decodeJson(SettingCodec.JSON.readTree(Objects.requireNonNull(value, "认证设置文本不能为空")));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("账号认证设置必须是完整 JSON object", exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull JsonNode encodeJson(@NotNull AccountAuthenticationSettings value) {
        AccountAuthenticationSettings checked = Objects.requireNonNull(value, "账号认证设置不能为空");
        ObjectNode root = SettingCodec.JSON.createObjectNode();
        root.put("loginFactorCount", checked.loginFactorCount());
        root.put("sensitiveActionFactorCount", checked.sensitiveActionFactorCount());
        root.put(
                "reusableAuthenticationMaximumAgeSeconds",
                checked.reusableAuthenticationMaximumAge().toSeconds());
        ArrayNode methods = root.putArray("methods");
        checked.methods()
                .values()
                .stream()
                .sorted(Comparator.comparing(method -> method.methodCode().value()))
                .forEach(method -> {
                    ObjectNode item = methods.addObject();
                    item.put("methodCode", method.methodCode().value());
                    item.put("loginStartEnabled", method.loginStartEnabled());
                    item.put("loginAdditionalEnabled", method.loginAdditionalEnabled());
                    item.put("sensitiveActionEnabled", method.sensitiveActionEnabled());
                });
        return root;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull AccountAuthenticationSettings decodeJson(@NotNull JsonNode value) {
        JsonNode root = requireObject(Objects.requireNonNull(value, "认证设置 JSON 不能为空"), "认证设置");
        rejectUnknownFields(root, ROOT_FIELDS, "认证设置");
        int loginFactorCount = requireInteger(root, "loginFactorCount");
        int sensitiveActionFactorCount = requireInteger(root, "sensitiveActionFactorCount");
        JsonNode maximumAge = root.get("reusableAuthenticationMaximumAgeSeconds");
        if (maximumAge == null || !maximumAge.isIntegralNumber() || !maximumAge.canConvertToLong()) {
            throw new IllegalArgumentException("reusableAuthenticationMaximumAgeSeconds 必须是 Long");
        }
        long maximumAgeSeconds = maximumAge.longValue();
        JsonNode methodNodes = root.get("methods");
        if (methodNodes == null || !methodNodes.isArray() || methodNodes.isEmpty()) {
            throw new IllegalArgumentException("认证设置 methods 必须是非空 array");
        }
        List<AuthenticationMethodSettings> methods = new ArrayList<>();
        for (JsonNode methodNode : methodNodes) {
            JsonNode method = requireObject(methodNode, "认证方法设置");
            rejectUnknownFields(method, METHOD_FIELDS, "认证方法设置");
            JsonNode methodCode = method.get("methodCode");
            if (methodCode == null || !methodCode.isTextual() || methodCode.textValue().isBlank()) {
                throw new IllegalArgumentException("methodCode 必须是非空 String");
            }
            methods.add(
                    new AuthenticationMethodSettings(
                            TreeCode.ofSlash(methodCode.textValue()),
                            requireBoolean(method, "loginStartEnabled"),
                            requireBoolean(method, "loginAdditionalEnabled"),
                            requireBoolean(method, "sensitiveActionEnabled")));
        }
        return new AccountAuthenticationSettings(
                loginFactorCount,
                sensitiveActionFactorCount,
                Duration.ofSeconds(maximumAgeSeconds),
                methods);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String valueType() {
        return "object";
    }

    /** 返回必须存在的 JSON object。 */
    private static @NotNull JsonNode requireObject(@NotNull JsonNode value, @NotNull String name) {
        if (!value.isObject()) {
            throw new IllegalArgumentException(name + "必须是 JSON object");
        }
        return value;
    }

    /** 拒绝当前结构版本没有声明的 JSON 字段。 */
    private static void rejectUnknownFields(
            @NotNull JsonNode value,
            @NotNull Set<String> allowed,
            @NotNull String name) {
        value.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(name + "包含未知字段: " + field);
            }
        });
    }

    /** 返回必须存在的整数 JSON 字段。 */
    private static int requireInteger(@NotNull JsonNode value, @NotNull String field) {
        JsonNode node = value.get(field);
        if (node == null || !node.isInt()) {
            throw new IllegalArgumentException(field + "必须是 Integer");
        }
        return node.intValue();
    }

    /** 返回必须存在的布尔 JSON 字段。 */
    private static boolean requireBoolean(@NotNull JsonNode value, @NotNull String field) {
        JsonNode node = value.get(field);
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException(field + "必须是 Boolean");
        }
        return node.booleanValue();
    }
}
