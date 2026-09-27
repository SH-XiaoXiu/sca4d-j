package com.sca4d.app.server.plugin;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.app.server.plugin.mfa.MfaFacts;
import com.sca4d.app.server.plugin.mfa.MfaMethodFacts;
import com.sca4d.app.server.plugin.mfa.MfaPolicy;
import com.sca4d.app.server.plugin.mfa.MfaSettings;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginBinding;
import com.sca4d.core.api.plugin.PluginMount;
import com.sca4d.core.api.plugin.PluginScope;
import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.plugin.spring.PluginAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证真实生成契约的 Spring 挂载、Bean 方法绑定和必需插件启动检查。 */
class MfaPluginSpringTest {
    @TempDir Path temporary;

    @Test
    @DisplayName("Bean 方法绑定宿主事实，参数挂载插件客户端，作用域恢复正常")
    void mountsGeneratedClientAndBindsActualBean() {
        try (var context = context(System.getProperty("sca4dj.plugin.artifacts[0]"))) {
            context.refresh();
            Probe probe = context.getBean(Probe.class);
            assertEquals(2, probe.factors());
            PluginRuntime runtime = context.getBean(PluginRuntime.class);
            assertEquals(0, runtime.statuses().getFirst().uses());
        }
    }

    @Test
    @DisplayName("必需 MFA 构件缺失使宿主上下文启动失败")
    void rejectsMissingRequiredArtifact() {
        try (var context = context(temporary.resolve("missing.jar").toString())) {
            assertThrows(RuntimeException.class, context::refresh);
        }
    }

    /** 使用独立上下文只装配插件机制，不启动 HTTP 或访问业务数据库。 */
    private AnnotationConfigApplicationContext context(String artifact) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("plugin-test", Map.of(
                "sca4dj.plugin.enabled", "true", "sca4dj.plugin.api", "SCA4D:SERVER",
                "sca4dj.plugin.artifacts[0]", artifact,
                "sca4dj.plugin.cache-directory", temporary.resolve("cache").toString())));
        context.register(PluginAutoConfiguration.class, Beans.class);
        return context;
    }

    /** 声明宿主服务与普通业务挂载，框架不要求测试 Bean 实现插件入口。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Beans {
        /** 暴露无凭据的测试事实。 */
        @Bean
        @PluginBinding(MfaFacts.class)
        MfaFacts facts() { return new Facts(); }

        /** 通过参数声明挂载必需策略客户端。 */
        @Bean
        @PluginScope
        Probe probe(@PluginMount("AUTH:MFA:POLICY") MfaPolicy policy) { return new Probe(policy); }
    }

    /** 保存普通业务对策略客户端的调用，不直接依赖加载实现。 */
    public static class Probe {
        private final MfaPolicy policy;
        public Probe(MfaPolicy policy) { this.policy = policy; }
        /** 返回当前账号登录所需因素。 */
        public int factors() { return policy.requirementFor(42, AuthenticationActions.LOGIN_ACTION).factorCount(); }
    }

    /** 提供最小密码与 TOTP 事实。 */
    static class Facts implements MfaFacts {
        private static final TreeCode PASSWORD = TreeCode.of("AUTHENTICATION:PASSWORD");
        private static final TreeCode TOTP = TreeCode.of("AUTHENTICATION:TOTP");
        @Override public @NotNull MfaSettings settings(long accountId) {
            return new MfaSettings(2, 2, Duration.ofMinutes(5), List.of(
                    new AuthenticationMethodSettings(PASSWORD, true, true, true),
                    new AuthenticationMethodSettings(TOTP, false, true, true)));
        }
        @Override public @NotNull List<MfaMethodFacts> methods() {
            return List.of(new MfaMethodFacts(PASSWORD, Set.of(AuthenticationFactorKind.KNOWLEDGE), true),
                    new MfaMethodFacts(TOTP, Set.of(AuthenticationFactorKind.POSSESSION), false));
        }
        @Override public @NotNull Set<TreeCode> availableMethods(long accountId) { return Set.of(PASSWORD, TOTP); }
    }
}
