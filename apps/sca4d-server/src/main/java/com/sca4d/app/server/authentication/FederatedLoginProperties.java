package com.sca4d.app.server.authentication;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/** 保存具体应用联邦登录的固定 callback 和前端完成地址。 */
@ConfigurationProperties("app.authentication.federated-login")
public class FederatedLoginProperties {
    private URI callbackUri = URI.create("http://localhost:8088/api/login/federated-authorizations/callback");

    private URI completionUri = URI.create("http://localhost:5666/#/login");

    /** @return 上游 Provider 注册使用的 callback URI */
    public @NotNull URI getCallbackUri() {
        return callbackUri;
    }

    /** @param value 只应指向本应用 callback 路由的 URI */
    public void setCallbackUri(@NotNull URI value) {
        callbackUri = value;
    }

    /** @return callback 完成后跳转的前端地址 */
    public @NotNull URI getCompletionUri() {
        return completionUri;
    }

    /** @param value 前端登录页面地址 */
    public void setCompletionUri(@NotNull URI value) {
        completionUri = value;
    }
}
