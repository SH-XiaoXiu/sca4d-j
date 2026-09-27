package com.sca4d.app.server.plugin.mfa;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginService;
import com.sca4d.core.api.plugin.PluginOperation;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Set;

/** 向策略插件提供设置与方法事实，不暴露凭据、Proof 和内部 Repository。 */
@PluginService("AUTH:MFA:FACTS")
public interface MfaFacts {
    /** 返回当前账号保存或默认的 MFA 设置。 */
    @PluginOperation("SETTINGS")
    @NotNull MfaSettings settings(long accountId);
    /** 返回当前 App 安装的方法元数据，不检查账号凭据。 */
    @PluginOperation("METHODS")
    @NotNull List<MfaMethodFacts> methods();
    /** 检查指定账号目前确实可用的方法；只在需要可满足性检查时调用。 */
    @PluginOperation("AVAILABLE")
    @NotNull Set<TreeCode> availableMethods(long accountId);
}
