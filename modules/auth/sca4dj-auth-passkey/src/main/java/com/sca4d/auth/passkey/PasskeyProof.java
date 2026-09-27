package com.sca4d.auth.passkey;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次 Passkey assertion ceremony 标识和浏览器返回的 WebAuthn JSON。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PasskeyProof {

    private final PasskeyCeremonyId ceremonyId;

    private final String responseJson;

    /**
     * 创建只供一次认证方法调用使用的 assertion Proof。
     *
     * @param ceremonyId assertion 开始时返回的不透明标识
     * @param responseJson 浏览器原生 WebAuthn JSON API 返回的 JSON
     */
    public PasskeyProof(@NotNull PasskeyCeremonyId ceremonyId, String responseJson) {
        this.ceremonyId = Objects.requireNonNull(ceremonyId, "Passkey ceremony 标识不能为空");
        this.responseJson = TextUtils.requireText(responseJson, "Passkey assertion 响应");
    }

    /** 返回 assertion ceremony 标识。 */
    public @NotNull PasskeyCeremonyId ceremonyId() {
        return ceremonyId;
    }

    /** 返回浏览器提供且不得持久化或记录的 WebAuthn assertion JSON。 */
    public @NotNull String responseJson() {
        return responseJson;
    }
}
