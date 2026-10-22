package com.sca4d.app.server.authentication;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginApiType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一个认证方法在登录首个认证阶段、附加认证因素和敏感动作中的启用状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
@PluginApiType
public final class AuthenticationMethodSettings {

    private final TreeCode methodCode;

    private final boolean loginAdditionalEnabled;

    private final boolean loginStartEnabled;

    private final boolean sensitiveActionEnabled;

    /**
     * 创建认证方法开关。
     *
     * @param methodCode 认证模块声明的稳定方法编码
     * @param loginStartEnabled 是否允许使用当前方法识别账号并开始登录
     * @param loginAdditionalEnabled 是否允许当前方法补充登录因素
     * @param sensitiveActionEnabled 是否允许当前方法认证登录后的敏感动作
     */
    public AuthenticationMethodSettings(
            @NotNull TreeCode methodCode,
            boolean loginStartEnabled,
            boolean loginAdditionalEnabled,
            boolean sensitiveActionEnabled) {
        this.methodCode = Objects.requireNonNull(methodCode, "认证方法编码不能为空");
        this.loginStartEnabled = loginStartEnabled;
        this.loginAdditionalEnabled = loginAdditionalEnabled;
        this.sensitiveActionEnabled = sensitiveActionEnabled;
    }

    /** 返回认证模块声明的稳定方法编码。 */
    public @NotNull TreeCode methodCode() {
        return methodCode;
    }

    /** 返回当前方法是否允许开始登录。 */
    public boolean loginStartEnabled() {
        return loginStartEnabled;
    }

    /** 返回当前方法是否允许补充登录因素。 */
    public boolean loginAdditionalEnabled() {
        return loginAdditionalEnabled;
    }

    /** 返回当前方法是否允许认证登录后的敏感动作。 */
    public boolean sensitiveActionEnabled() {
        return sensitiveActionEnabled;
    }
}
