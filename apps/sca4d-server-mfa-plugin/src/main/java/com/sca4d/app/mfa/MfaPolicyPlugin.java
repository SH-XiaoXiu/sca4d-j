package com.sca4d.app.mfa;

import com.sca4d.app.server.plugin.api.Sca4dServerPlugin;
import com.sca4d.app.server.plugin.api.Sca4dServerPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** 为 Sca4d Server 安装完整的登录和敏感动作 MFA 策略。 */
public final class MfaPolicyPlugin extends Sca4dServerPlugin {
    private static final Logger LOG = LoggerFactory.getLogger(MfaPolicyPlugin.class);

    @Override
    public void onLoad(Sca4dServerPluginContext context) throws Exception {
        // 插件读取当前代次自己的资源，App 只提供公开配置与事实服务。
        MfaPolicyOptions options;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"), "MFA 插件缺少 plugin.yml")) {
            options = MfaPolicyOptions.read(input);
        }
        context.registerMfaPolicy(new DefaultMfaPolicy(context.mfaFacts(), options));
        LOG.debug(
                "MFA 策略候选已登记 | applicationName={} reuseSensitiveAuthentication={}",
                context.configuration().applicationName(),
                options.reuseSensitiveAuthentication());
    }
}
