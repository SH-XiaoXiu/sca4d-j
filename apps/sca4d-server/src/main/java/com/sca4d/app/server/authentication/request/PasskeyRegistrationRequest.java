package com.sca4d.app.server.authentication.request;

import com.sca4d.auth.passkey.PasskeyCeremonyId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 接收 Passkey registration ceremony、浏览器响应和 Credential 名称。
 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public class PasskeyRegistrationRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String ceremonyId;

    @NotBlank
    @Size(max = 65536)
    private @Nullable String responseJson;

    @NotBlank
    @Size(max = 100)
    private @Nullable String name;

    /**
     * 创建供 JSON 反序列化使用的空请求。
     */
    public PasskeyRegistrationRequest() {
    }

    /**
     * 返回经过非空校验的 Passkey ceremony 标识。
     */
    public @NotNull PasskeyCeremonyId ceremonyId() {
        return new PasskeyCeremonyId(Objects.requireNonNull(ceremonyId, "Passkey ceremony 标识不能为空"));
    }

    public void setCeremonyId(@Nullable String ceremonyId) {
        this.ceremonyId = ceremonyId;
    }

    public @NotNull String getResponseJson() {
        return Objects.requireNonNull(responseJson, "Passkey registration 响应不能为空");
    }

    public void setResponseJson(@Nullable String responseJson) {
        this.responseJson = responseJson;
    }

    public @NotNull String getName() {
        return Objects.requireNonNull(name, "Passkey 名称不能为空");
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Override
    public @NotNull String toString() {
        return "PasskeyRegistrationRequest[ceremonyId=" + ceremonyId + ", responseJson=protected]";
    }
}
