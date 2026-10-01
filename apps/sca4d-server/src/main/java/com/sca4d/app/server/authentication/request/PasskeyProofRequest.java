package com.sca4d.app.server.authentication.request;

import com.sca4d.auth.passkey.PasskeyCeremonyId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收一次 Passkey assertion ceremony 标识和浏览器 WebAuthn JSON 响应。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public class PasskeyProofRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String ceremonyId;

    @NotBlank
    @Size(max = 65536)
    private @Nullable String responseJson;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public PasskeyProofRequest() {
    }

    /** 返回类型化 ceremony 标识。 */
    public @NotNull PasskeyCeremonyId ceremonyId() {
        return new PasskeyCeremonyId(Objects.requireNonNull(ceremonyId, "Passkey ceremony 标识不能为空"));
    }

    public void setCeremonyId(@Nullable String ceremonyId) {
        this.ceremonyId = ceremonyId;
    }

    public @NotNull String getResponseJson() {
        return Objects.requireNonNull(responseJson, "Passkey assertion 响应不能为空");
    }

    public void setResponseJson(@Nullable String responseJson) {
        this.responseJson = responseJson;
    }

    @Override
    public @NotNull String toString() {
        return "PasskeyProofRequest[ceremonyId=" + ceremonyId + ", responseJson=protected]";
    }
}
