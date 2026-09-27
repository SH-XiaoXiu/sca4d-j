package com.sca4d.app.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.authentication.AuthenticationMethodSettings;
import com.sca4d.app.server.plugin.api.Sca4dServerPlugin;
import com.sca4d.app.server.plugin.mfa.MfaFacts;
import com.sca4d.app.server.plugin.mfa.MfaFactsContract;
import com.sca4d.app.server.plugin.mfa.MfaMethodFacts;
import com.sca4d.app.server.plugin.mfa.MfaPolicy;
import com.sca4d.app.server.plugin.mfa.MfaPolicyContract;
import com.sca4d.app.server.plugin.mfa.MfaSettings;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.ApiTarget;
import com.sca4d.core.api.plugin.PluginLease;
import com.sca4d.core.api.plugin.PluginStatus;
import com.sca4d.core.api.plugin.PluginUnavailableException;
import com.sca4d.plugin.pf4j.Pf4jPluginLoader;
import com.sca4d.plugin.runtime.DefaultPluginRuntime;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** 使用实际独立 JAR 验证 App 生成契约、MFA 行为及代次替换。 */
class MfaPluginRuntimeTest {
    private static final TreeCode PASSWORD = TreeCode.of("AUTHENTICATION:PASSWORD");

    private static final TreeCode TOTP = TreeCode.of("AUTHENTICATION:TOTP");

    private static final TreeCode PASSKEY = TreeCode.of("AUTHENTICATION:PASSKEY");

    @TempDir
    Path temporary;

    @Test
    @DisplayName("实际 JAR 双向读取宿主事实并保留登录和独立因素规则")
    void authenticatesUsingActualPluginJar() {
        try (DefaultPluginRuntime runtime = runtime()) {
            assertThrows(PluginUnavailableException.class, runtime::verifyRequired);
            runtime.replace(artifact(), Duration.ZERO);
            runtime.verifyRequired();
            MfaPolicy policy = MfaPolicyContract.INSTANCE.client(runtime, null);
            assertEquals(2, policy.requirementFor(42, AuthenticationActions.LOGIN_ACTION).factorCount());
            assertTrue(
                    policy.requirementFor(42, AuthenticationActions.LOGIN_ACTION)
                            .reusableAuthenticationMaximumAge()
                            .isEmpty());
            assertEquals(Set.of(PASSWORD), policy.allowedMethods(42, AuthenticationActions.LOGIN_ACTION, List.of()));
            Authentication password = new Authentication(
                    42,
                    PASSWORD,
                    Set.of(AuthenticationFactorKind.KNOWLEDGE),
                    Instant.parse("2026-09-01T00:00:00Z"));
            assertEquals(
                    Set.of(TOTP, PASSKEY),
                    policy.allowedMethods(42, AuthenticationActions.LOGIN_ACTION, List.of(password)));
            assertFalse(policy.allowsMethodUnavailable(42, PASSWORD));
            assertTrue(policy.allowsMethodUnavailable(42, TOTP));
            assertEquals(
                    Duration.ofMinutes(5),
                    policy.requirementFor(
                            42,
                            new AuthenticationActionCode(AuthenticationActions.PASSWORD_REPLACE_CODE))
                            .reusableAuthenticationMaximumAge()
                            .orElseThrow());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> policy.validate(
                            42,
                            new MfaSettings(
                                    3,
                                    2,
                                    Duration.ofMinutes(5),
                                    settings().methods())));
        }
    }

    @Test
    @DisplayName("原始 invoke 拒绝未公开操作、非法参数和不存在的扩展")
    void validatesRawInvocation() {
        try (DefaultPluginRuntime runtime = runtime()) {
            runtime.replace(artifact(), Duration.ZERO);
            ApiTarget target = new ApiTarget(TreeCode.of("AUTH:MFA:POLICY"), null);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> runtime.invoke(
                            target,
                            TreeCode.of("REQUIREMENT"),
                            new Object[] {"wrong", AuthenticationActions.LOGIN_ACTION}));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> runtime.invoke(target, TreeCode.of("PRIVATE"), new Object[] {}));
            assertThrows(
                    PluginUnavailableException.class,
                    () -> runtime.invoke(
                            new ApiTarget(TreeCode.of("NOT:EXPORTED"), null),
                            TreeCode.of("READ"),
                            new Object[] {}));
        }
    }

    @Test
    @DisplayName("错误入口和不兼容 API 的候选不会替换活动策略")
    void preservesActivePluginAfterCandidateFailure() throws Exception {
        try (DefaultPluginRuntime runtime = runtime()) {
            runtime.replace(artifact(), Duration.ZERO);
            Path badEntry = candidate("1.1.0", "missing.Plugin", ">=1.0.0 & <2.0.0");
            assertThrows(IllegalStateException.class, () -> runtime.replace(badEntry, Duration.ZERO));
            Path incompatible = candidate("1.1.0", "com.sca4d.app.mfa.MfaPolicyPlugin", ">=2.0.0");
            assertThrows(IllegalStateException.class, () -> runtime.replace(incompatible, Duration.ZERO));
            assertEquals(1, runtime.statuses().size());
            assertEquals(PluginStatus.State.ACTIVE, runtime.statuses().getFirst().state());
            assertEquals(
                    2,
                    MfaPolicyContract.INSTANCE.client(runtime, null)
                            .requirementFor(42, AuthenticationActions.LOGIN_ACTION)
                            .factorCount());
        }
    }

    @Test
    @DisplayName("更新等待旧作用域结束且稳定客户端使用新代次")
    void drainsOldGenerationAfterScope() throws Exception {
        try (DefaultPluginRuntime runtime = runtime();
                var workers = Executors.newSingleThreadExecutor()) {
            runtime.replace(artifact(), Duration.ZERO);
            MfaPolicy policy = MfaPolicyContract.INSTANCE.client(runtime, null);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var oldCall = workers.submit(() -> {
                try (PluginLease ignored = runtime.openScope()) {
                    policy.requirementFor(42, AuthenticationActions.LOGIN_ACTION);
                    entered.countDown();
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                    return policy.requirementFor(42, AuthenticationActions.LOGIN_ACTION).factorCount();
                }
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var update = runtime.replace(
                    candidate(
                            "1.1.0",
                            "com.sca4d.app.mfa.MfaPolicyPlugin",
                            ">=1.0.0 & <2.0.0"),
                    Duration.ZERO);
            assertEquals(PluginStatus.State.DRAINING, update.previous().state());
            assertEquals("1.1.0", update.current().descriptor().version());
            assertEquals(2, policy.requirementFor(42, AuthenticationActions.LOGIN_ACTION).factorCount());
            release.countDown();
            assertEquals(2, oldCall.get(10, TimeUnit.SECONDS));
            assertEquals(PluginStatus.State.STOPPED, runtime.statuses().getFirst().state());
            assertThrows(
                    IllegalStateException.class,
                    () -> runtime.stop(TreeCode.of("SCA4D:SERVER:MFA"), Duration.ZERO));
        }
    }

    @Test
    @DisplayName("API 构件没有宿主业务实现且插件构件没有重复共享类")
    void keepsArtifactsSeparate() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.sca4d.app.mfa.MfaPolicyPlugin"));
        try (JarFile jar = new JarFile(artifact().toFile())) {
            assertNotNull(jar.getJarEntry("com/sca4d/app/mfa/MfaPolicyPlugin.class"));
            assertNull(jar.getJarEntry("com/sca4d/app/server/plugin/mfa/MfaPolicy.class"));
            assertNull(jar.getJarEntry("com/sca4d/core/api/name/code/TreeCode.class"));
        }
    }

    /** 用生成契约与测试事实构造公开运行入口。 */
    private DefaultPluginRuntime runtime() {
        Set<String> shared = new HashSet<>();
        Properties description = new Properties();
        try (InputStream input = Sca4dServerPlugin.class.getClassLoader()
                .getResourceAsStream("META-INF/sca4dj/api.properties")) {
            description.load(input);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        try (InputStream input = Sca4dServerPlugin.class.getClassLoader()
                .getResourceAsStream(description.getProperty("api.shared-index"));
                Scanner scanner = new Scanner(input, StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine())
                shared.add(scanner.nextLine());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        DefaultPluginRuntime runtime = new DefaultPluginRuntime(
                TreeCode.of("SCA4D:SERVER"),
                "1.0.0",
                Sca4dServerPlugin.class,
                shared,
                new Pf4jPluginLoader(),
                temporary.resolve("cache"));
        runtime.declare(MfaFactsContract.INSTANCE);
        runtime.declare(MfaPolicyContract.INSTANCE);
        runtime.bind(MfaFactsContract.INSTANCE.bind(new Facts(), null));
        return runtime;
    }

    /** 读取构建任务明确提供的外部插件构件。 */
    private static Path artifact() {
        return Path.of(System.getProperty("sca4dj.plugin.artifacts[0]"));
    }

    /** 使用共享设置表达密码加独立持有因素，不依赖测试运行顺序。 */
    private static MfaSettings settings() {
        return new MfaSettings(
                2,
                2,
                Duration.ofMinutes(5),
                List.of(
                        new AuthenticationMethodSettings(PASSWORD, true, true, true),
                        new AuthenticationMethodSettings(TOTP, false, true, true),
                        new AuthenticationMethodSettings(PASSKEY, false, true, true)));
    }

    /** 仅改变构件描述构造候选，实际类仍来自独立 JAR。 */
    private Path candidate(String version, String entry, String range) throws Exception {
        Path result = Files.createTempFile(temporary, "candidate-", ".jar");
        try (JarFile original = new JarFile(artifact().toFile());
                JarOutputStream output = new JarOutputStream(Files.newOutputStream(result))) {
            var entries = original.entries();
            while (entries.hasMoreElements()) {
                JarEntry old = entries.nextElement();
                output.putNextEntry(new JarEntry(old.getName()));
                if (old.getName().equals("META-INF/sca4dj/plugin.properties")) {
                    Properties properties = new Properties();
                    try (InputStream input = original.getInputStream(old)) {
                        properties.load(input);
                    }
                    properties.setProperty("plugin.version", version);
                    properties.setProperty("plugin.entry", entry);
                    properties.setProperty("plugin.requires-api", range);
                    properties.store(output, "Test candidate");
                } else if (!old.isDirectory()) {
                    try (InputStream input = original.getInputStream(old)) {
                        input.transferTo(output);
                    }
                }
                output.closeEntry();
            }
        }
        return result;
    }

    /** 提供不包含凭据的账号事实，测试插件反向调用宿主。 */
    private static final class Facts implements MfaFacts {
        @Override
        public @NotNull MfaSettings settings(long accountId) {
            return MfaPluginRuntimeTest.settings();
        }

        @Override
        public @NotNull List<MfaMethodFacts> methods() {
            return List.of(
                    new MfaMethodFacts(PASSWORD, Set.of(AuthenticationFactorKind.KNOWLEDGE), true),
                    new MfaMethodFacts(TOTP, Set.of(AuthenticationFactorKind.POSSESSION), false),
                    new MfaMethodFacts(PASSKEY, Set.of(AuthenticationFactorKind.POSSESSION), false));
        }

        @Override
        public @NotNull Set<TreeCode> availableMethods(long accountId) {
            return Set.of(PASSWORD, TOTP, PASSKEY);
        }
    }
}
