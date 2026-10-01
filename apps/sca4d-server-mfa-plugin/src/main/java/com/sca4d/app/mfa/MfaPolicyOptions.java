package com.sca4d.app.mfa;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/** 由 MFA 插件自行解释内嵌 YAML 的策略选项，宿主不依赖这些字段或类型。 */
public final class MfaPolicyOptions {
    private final boolean reuseSensitiveAuthentication;

    public MfaPolicyOptions(boolean reuseSensitiveAuthentication) {
        this.reuseSensitiveAuthentication = reuseSensitiveAuthentication;
    }

    /**
     * 从当前插件资源读取私有策略选项；字段缺失时允许按账号设置复用已有认证。
     * 拒绝空文档、重复键、非布尔开关及超过 1 MiB 的资源，读取后由调用方关闭输入。
     */
    public static @NotNull MfaPolicyOptions read(@NotNull InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(1_048_577);
        if (bytes.length > 1_048_576) {
            throw new IllegalArgumentException("MFA 插件配置超过 1 MiB");
        }
        ObjectMapper mapper = new ObjectMapper(
                YAMLFactory.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonNode document = mapper.readerFor(JsonNode.class).readValue(bytes);
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("MFA 插件配置必须为非空 YAML 对象");
        }
        JsonNode policy = document.path("policy");
        if (!policy.isMissingNode() && !policy.isObject()) {
            throw new IllegalArgumentException("MFA 插件 policy 必须为对象");
        }
        JsonNode reuse = policy.path("reuse-sensitive-authentication");
        if (!reuse.isMissingNode() && !reuse.isBoolean()) {
            throw new IllegalArgumentException("MFA 插件认证复用开关必须为布尔值");
        }
        return new MfaPolicyOptions(reuse.isMissingNode() || reuse.booleanValue());
    }

    /** 返回敏感动作是否允许在账号设置的时间范围内复用已有认证。 */
    @Contract(pure = true)
    public boolean reuseSensitiveAuthentication() {
        return reuseSensitiveAuthentication;
    }
}
