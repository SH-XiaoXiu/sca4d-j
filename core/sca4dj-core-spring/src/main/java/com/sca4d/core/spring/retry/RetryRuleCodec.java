package com.sca4d.core.spring.retry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 编解码版本化重试规则快照，不保存 Java 类名、异常或可执行判断函数。
 */
public class RetryRuleCodec implements VersionedCodec<RetryRule, String> {
    private final ObjectMapper mapper;

    /** @param mapper 脚手架内部共享 JSON 映射器，由装配方提供 */
    public RetryRuleCodec(@NotNull ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "脚手架 JSON 映射器不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.ofColon("CORE:RETRY:RULE");
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public @NotNull Class<RetryRule> valueType() {
        return RetryRule.class;
    }

    /**
     * @param rule 不可变规则
     * @return 可写入 JSONB 或内部消息头的快照
     */
    @Override
    public @NotNull String encode(@NotNull RetryRule rule) {
        Objects.requireNonNull(rule);
        ObjectNode json = mapper.createObjectNode().put("version", version());
        if (rule.maximumAttempts().isPresent())
            json.put("maxAttempts", rule.maximumAttempts().getAsLong());
        else
            json.putNull("maxAttempts");
        Backoff backoff = rule.backoff();
        ObjectNode value = json.putObject("backoff").put("type", backoff.kind().name());
        switch (backoff.kind()) {
            case FIXED -> value.put("delayMillis", backoff.delays().getFirst().toMillis());
            case EXPONENTIAL -> value.put("initialMillis", backoff.delays().getFirst().toMillis())
                    .put("multiplier", backoff.multiplier())
                    .put("maximumMillis", backoff.maximum().toMillis());
            case SEQUENCE -> {
                ArrayNode delays = value.putArray("delaysMillis");
                backoff.delays().forEach(delay -> delays.add(delay.toMillis()));
            }
        }
        try {
            return mapper.writeValueAsString(json);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("重试规则快照无法编码为 JSON", failure);
        }
    }

    /**
     * @param encoded version=1 的规则快照
     * @return 校验后的规则
     * @throws IllegalArgumentException 快照损坏或版本未知
     */
    @Override
    public @NotNull RetryRule decode(@NotNull String encoded) {
        try {
            JsonNode json = mapper.readTree(Objects.requireNonNull(encoded));
            if (json == null || !json.isObject() || number(json, "version") != version() || !json.has("maxAttempts")) {
                throw new IllegalArgumentException("不支持的重试规则快照");
            }
            JsonNode value = json.required("backoff");
            Backoff backoff = switch (Backoff.Kind.valueOf(value.required("type").asText())) {
                case FIXED -> Backoff.fixed(Duration.ofMillis(number(value, "delayMillis")));
                case EXPONENTIAL -> {
                    if (!value.required("multiplier").isNumber())
                        throw new IllegalArgumentException("无效退避倍率");
                    yield Backoff.exponential(
                            Duration.ofMillis(number(value, "initialMillis")),
                            value.get("multiplier").doubleValue(),
                            Duration.ofMillis(number(value, "maximumMillis")));
                }
                case SEQUENCE -> {
                    JsonNode array = value.required("delaysMillis");
                    if (!array.isArray())
                        throw new IllegalArgumentException("重试间隔必须为数组");
                    List<Duration> delays = new ArrayList<>();
                    for (JsonNode item : array) {
                        if (!item.isIntegralNumber() || !item.canConvertToLong())
                            throw new IllegalArgumentException("无效重试间隔");
                        delays.add(Duration.ofMillis(item.longValue()));
                    }
                    yield Backoff.sequence(delays.toArray(Duration[]::new));
                }
            };
            return json.get("maxAttempts").isNull()
                    ? RetryRule.unlimited(backoff)
                    : RetryRule.atMost(number(json, "maxAttempts"), backoff);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("重试规则快照不是合法 JSON", failure);
        }
    }

    /**
     * 读取必填的整数字段。
     *
     * @param node 包含字段的 JSON 对象
     * @param name 字段名
     * @return 字段值
     * @throws IllegalArgumentException 字段缺失、不是整数或超出 long 范围时抛出
     */
    private static long number(JsonNode node, String name) {
        JsonNode value = node.required(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw new IllegalArgumentException("无效重试规则整数: " + name);
        return value.longValue();
    }
}
