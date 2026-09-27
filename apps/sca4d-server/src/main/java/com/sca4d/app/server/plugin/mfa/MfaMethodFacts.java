package com.sca4d.app.server.plugin.mfa;

import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginApiType;
import org.jetbrains.annotations.NotNull;
import java.util.Set;

/** 保存认证方法的公开因素和账号识别声明，不持有认证方法对象。 */
@PluginApiType
public final class MfaMethodFacts {
    private final TreeCode code;
    private final Set<AuthenticationFactorKind> factorKinds;
    private final boolean canIdentifyAccount;
    public MfaMethodFacts(@NotNull TreeCode code, @NotNull Set<AuthenticationFactorKind> factorKinds,
            boolean canIdentifyAccount) {
        this.code = code;
        this.factorKinds = Set.copyOf(factorKinds);
        this.canIdentifyAccount = canIdentifyAccount;
    }
    /** 返回稳定认证方法编码。 */
    public @NotNull TreeCode code() { return code; }
    /** 返回方法可能贡献的独立因素类别。 */
    public @NotNull Set<AuthenticationFactorKind> factorKinds() { return factorKinds; }
    /** 返回是否允许作为首次账号发现方法。 */
    public boolean canIdentifyAccount() { return canIdentifyAccount; }
}
