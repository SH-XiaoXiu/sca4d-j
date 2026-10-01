package com.sca4d.app.server.plugin.mfa;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginExtension;
import com.sca4d.core.api.plugin.PluginOperation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/** 声明 Sca4d Server 必须安装的完整 MFA 策略扩展。 */
@PluginExtension(value = "AUTH:MFA:POLICY", required = true)
public interface MfaPolicy {
    /** 返回当前动作要求的因素数及认证复用时间。 */
    @PluginOperation("REQUIREMENT")
    @NotNull
    AuthenticationRequirement requirementFor(long accountId, @NotNull AuthenticationActionCode action);

    /** 返回用途开关和已完成因素允许的认证方法。 */
    @PluginOperation("ALLOWED_METHODS")
    @NotNull
    Set<TreeCode> allowedMethods(
            long accountId,
            @NotNull AuthenticationActionCode action,
            @NotNull List<Authentication> completed);

    /** 检查候选设置是否能由已安装且可用的方法满足；非法时抛出异常。 */
    @PluginOperation("VALIDATE")
    void validate(long accountId, @NotNull MfaSettings proposed);

    /** 返回删除指定方法最后一个凭据后是否仍满足全部认证要求。 */
    @PluginOperation("METHOD_REMOVAL")
    boolean allowsMethodUnavailable(long accountId, @NotNull TreeCode method);
}
