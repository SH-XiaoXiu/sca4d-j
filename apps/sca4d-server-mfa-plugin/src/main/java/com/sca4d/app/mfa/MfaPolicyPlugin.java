package com.sca4d.app.mfa;

import com.sca4d.app.server.plugin.api.Sca4dServerPlugin;
import com.sca4d.app.server.plugin.api.Sca4dServerPluginContext;

/** 为 Sca4d Server 安装完整的登录和敏感动作 MFA 策略。 */
public final class MfaPolicyPlugin extends Sca4dServerPlugin {
    @Override
    public void onLoad(Sca4dServerPluginContext context) {
        context.registerMfaPolicy(new DefaultMfaPolicy(context.mfaFacts()));
    }
}
