package com.sca4d.openapi.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 返回 OAuth Client Credentials 交换得到的 Bearer Token 和 scope。 */
@Schema(name = "OpenApiToken", description = "OpenAPI Client Credentials 签发的短期 Bearer Token。")
public class OpenApiTokenResponse {

    private final @NotNull String accessToken;

    private final int expiresIn;

    private final @NotNull String scope;

    /** 创建令牌响应。 */
    public OpenApiTokenResponse(
            @NotNull String accessToken,
            @NotNull Instant expiresAt,
            @NotNull String scope) {
        this.accessToken = requireAccessToken(accessToken);
        long seconds = Duration.between(Instant.now(), Objects.requireNonNull(expiresAt, "过期时间不能为空"))
                .toSeconds();
        if (seconds <= 0 || seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Token 剩余有效期必须在正整数范围内");
        }
        this.expiresIn = (int) seconds;
        this.scope = Objects.requireNonNull(scope, "scope 不能为空").trim();
    }

    /** @return OAuth access_token */
    @JsonProperty("access_token")
    @Schema(description = "请求 OpenAPI Tool 使用的 Bearer Token。", example = "eyJraWQiOiJvcGVuYXBpLTIwMjYtMDgifQ.example")
    public @NotNull String getAccessToken() {
        return accessToken;
    }

    /** @return access_token 剩余秒数 */
    @JsonProperty("expires_in")
    @Schema(description = "Token 剩余有效秒数。", example = "300", minimum = "1")
    public int getExpiresIn() {
        return expiresIn;
    }

    /** @return 空格分隔的 Operation scope */
    @JsonProperty("scope")
    @Schema(description = "空格分隔的授权 Operation。", example = "iot:devices:read")
    public @NotNull String getScope() {
        return scope;
    }

    /** @return 固定 Bearer 类型 */
    @JsonProperty("token_type")
    @Schema(description = "固定为 Bearer。", example = "Bearer", allowableValues = "Bearer")
    public @NotNull String getTokenType() {
        return "Bearer";
    }

    /** 校验并返回去除首尾空白的 access_token。 */
    private static @NotNull String requireAccessToken(@NotNull String value) {
        Objects.requireNonNull(value, "access_token 不能为空");
        String checked = value.trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("access_token 不能为空");
        }
        return checked;
    }
}
