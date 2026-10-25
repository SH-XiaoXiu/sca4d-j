package com.sca4d.plugin.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.*;
import com.sca4d.core.api.plugin.exception.*;
import com.sca4d.core.api.plugin.exception.PluginUnavailableException;
import com.sca4d.plugin.runtime.exception.PluginException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 通过公开调用验证 App 配置与插件代次一同发布、排空及释放。 */
class DefaultPluginRuntimeTest {
    private static final TreeCode ID = TreeCode.of("TEST:PLUGIN");

    private static final TreeCode READ = TreeCode.of("READ");

    private static final ApiDefinition DEFINITION = new ApiDefinition(
            TreeCode.of("TEST:CONFIGURATION"),
            ApiKind.EXTENSION,
            ExtensionSelection.SINGLE,
            false,
            List.of(new ApiOperation(READ, List.of(), String.class, List.of(), false)));

    private static final ApiContract<Query> QUERY = new ApiContract<>() {
        @Override
        public @NotNull ApiDefinition definition() {
            return DEFINITION;
        }

        @Override
        public @NotNull Class<Query> type() {
            return Query.class;
        }

        @Override
        public @NotNull Query client(@NotNull ApiInvoker invoker, @Nullable TreeCode key) {
            return () -> (String) invoker.invoke(new ApiTarget(DEFINITION.code(), key), READ, new Object[0]);
        }

        @Override
        public @NotNull ApiBinding bind(@NotNull Query implementation, @Nullable TreeCode key) {
            return new ApiBinding(
                    DEFINITION,
                    new ApiTarget(DEFINITION.code(), key),
                    Map.of(READ, arguments -> implementation.read()));
        }
    };

    @TempDir
    Path temporary;

    @Test
    @DisplayName("配置重载固定活动构件，旧作用域读取旧配置并在归还后释放")
    void reloadsCachedArtifactAndDrainsConfigurationWithItsGeneration() throws Throwable {
        Provider provider = new Provider();
        FixtureLoader loader = new FixtureLoader();
        Path artifact = temporary.resolve("plugin.jar");
        Files.writeString(artifact, "artifact-a");
        try (var runtime = runtime(provider, loader); var worker = Executors.newSingleThreadExecutor()) {
            runtime.replace(artifact, Duration.ZERO);
            Query query = QUERY.client(runtime, null);
            assertEquals("1:artifact-a", query.read());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> loader.entries.getFirst().context.configuration(Integer.class));
            try (PluginLease ignored = runtime.openScope()) {
                provider.revision.set(2);
                Files.writeString(artifact, "artifact-b");
                PluginUpdate update = worker.submit(() -> runtime.reload(ID, Duration.ZERO)).get(10, TimeUnit.SECONDS);
                assertEquals(PluginStatus.State.DRAINING, update.previous().state());
                assertEquals("1:artifact-a", query.read());
                assertEquals(0, provider.unloaded.size());
                assertEquals(1, provider.reloads.get());
                assertEquals(runtime.statuses().getFirst().digest(), runtime.statuses().getLast().digest());
            }
            assertEquals("2:artifact-a", query.read());
            assertEquals(List.of("1:artifact-a"), provider.unloaded);
            assertEquals(PluginStatus.State.STOPPED, runtime.statuses().getFirst().state());
            assertThrows(
                    PluginUnavailableException.class,
                    () -> loader.entries.getFirst().context.configuration(String.class));
        }
        assertEquals(List.of("1:artifact-a", "2:artifact-a"), provider.unloaded);
        assertEquals(2, loader.closed.get());
        for (Path path : provider.copies)
            assertFalse(Files.exists(path));
    }

    @Test
    @DisplayName("App 重载失败、API 不兼容和插件初始化失败均保留活动配置")
    void preservesActiveConfigurationAndReleasesRejectedSnapshots() throws Throwable {
        Provider provider = new Provider();
        FixtureLoader loader = new FixtureLoader();
        Path artifact = temporary.resolve("plugin.jar");
        Files.writeString(artifact, "artifact-a");
        try (var runtime = runtime(provider, loader)) {
            runtime.replace(artifact, Duration.ZERO);
            Query query = QUERY.client(runtime, null);
            provider.failReload = true;
            assertThrows(PluginException.class, () -> runtime.reload(ID, Duration.ZERO));
            assertEquals("1:artifact-a", query.read());
            assertTrue(provider.unloaded.isEmpty());
            provider.failReload = false;
            provider.wrongApi = true;
            assertThrows(PluginException.class, () -> runtime.reload(ID, Duration.ZERO));
            assertEquals(1, loader.entries.size());
            assertEquals(1, provider.unloaded.size());
            provider.wrongApi = false;
            loader.failInitialization = true;
            assertThrows(PluginException.class, () -> runtime.reload(ID, Duration.ZERO));
            assertEquals(2, provider.unloaded.size());
            assertEquals(1, loader.closed.get());
            assertEquals("1:artifact-a", query.read());
            assertEquals(1, runtime.statuses().size());
        }
        assertEquals(3, provider.unloaded.size());
        try (var files = Files.list(temporary.resolve("cache"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    @DisplayName("插件和配置卸载同时失败时仍关闭加载器并记录 FAILED")
    void closesLoaderEvenWhenUnloadCallbacksFail() throws Exception {
        Provider provider = new Provider();
        provider.failUnload = true;
        FixtureLoader loader = new FixtureLoader();
        loader.failUnload = true;
        Path artifact = temporary.resolve("plugin.jar");
        Files.writeString(artifact, "artifact-a");
        try (var runtime = runtime(provider, loader)) {
            runtime.replace(artifact, Duration.ZERO);
            assertEquals(PluginStatus.State.FAILED, runtime.stop(ID, Duration.ZERO).state());
            assertEquals(1, loader.closed.get());
            assertEquals(1, provider.unloaded.size());
            assertFalse(Files.exists(provider.copies.getFirst()));
        }
        assertEquals(1, provider.unloaded.size());
    }

    @Test
    @DisplayName("两个宿主实例的配置代次互不替换，共享提供者释放各自快照")
    void keepsIndependentRuntimeConfigurations() throws Throwable {
        Provider provider = new Provider();
        Path artifact = temporary.resolve("plugin.jar");
        Files.writeString(artifact, "artifact-a");
        try (var first = runtime(provider, new FixtureLoader()); var second = runtime(provider, new FixtureLoader())) {
            first.replace(artifact, Duration.ZERO);
            provider.revision.set(2);
            second.replace(artifact, Duration.ZERO);
            provider.revision.set(3);
            first.reload(ID, Duration.ZERO);
            assertEquals("3:artifact-a", QUERY.client(first, null).read());
            assertEquals("2:artifact-a", QUERY.client(second, null).read());
        }
        assertEquals(3, provider.unloaded.size());
    }

    /** 用公开契约构造隔离运行实例，测试加载器只替代实际类加载。 */
    private DefaultPluginRuntime<String> runtime(Provider provider, FixtureLoader loader) {
        var runtime = new DefaultPluginRuntime<>(
                TreeCode.of("TEST:APP"),
                "1.1.0",
                Entry.class,
                Set.of(Entry.class.getName(), Query.class.getName(), PluginEntrypoint.class.getName()),
                loader,
                temporary.resolve("cache"),
                provider);
        runtime.declare(QUERY);
        return runtime;
    }

    /** 表达公开读取扩展，具体绑定会返回所属插件代次的配置。 */
    public interface Query {
        /** 返回当前代次的配置值。 */
        @NotNull
        String read() throws Throwable;
    }

    /** 在加载时登记读取扩展，并保存上下文以检查停止后的访问限制。 */
    public static class Entry implements PluginEntrypoint {
        private PluginContext context;

        private boolean failInitialization;

        private boolean failUnload;

        @Override
        public void onLoad(@NotNull PluginContext context) {
            this.context = context;
            context.register(QUERY, () -> context.configuration(String.class), null);
            if (failInitialization)
                throw new IllegalStateException("候选初始化失败");
        }

        @Override
        public void onUnload() {
            if (failUnload)
                throw new IllegalStateException("插件卸载失败");
        }
    }

    /** 从指定副本读取构件内容，记录配置的加载与关闭，并按测试设定触发失败。 */
    private static final class Provider implements PluginConfiguration<String> {
        private final AtomicInteger revision = new AtomicInteger(1);

        private final AtomicInteger reloads = new AtomicInteger();

        private final List<Path> copies = new CopyOnWriteArrayList<>();

        private final List<String> unloaded = new CopyOnWriteArrayList<>();

        private boolean failReload;

        private boolean wrongApi;

        private boolean failUnload;

        @Override
        public @NotNull Class<String> type() {
            return String.class;
        }

        @Override
        public @NotNull PluginConfigurationSnapshot<String> load(@NotNull Path artifact) throws Exception {
            copies.add(artifact);
            return new PluginConfigurationSnapshot<>(
                    new PluginDescriptor(
                            ID,
                            "1.0.0",
                            TreeCode.of(wrongApi ? "OTHER:APP" : "TEST:APP"),
                            "*",
                            Entry.class.getName()),
                    revision.get() + ":" + Files.readString(artifact));
        }

        @Override
        public @NotNull PluginConfigurationSnapshot<String> reload(@NotNull Path artifact) throws Exception {
            reloads.incrementAndGet();
            if (failReload)
                throw new IllegalArgumentException("App 配置读取失败");
            return load(artifact);
        }

        @Override
        public void unload(@NotNull PluginConfigurationSnapshot<String> snapshot) {
            unloaded.add(snapshot.values());
            if (failUnload)
                throw new IllegalArgumentException("App 配置卸载失败");
        }
    }

    /** 创建独立入口并记录关闭结果，不实现版本算法，也不改变运行时状态。 */
    private static final class FixtureLoader implements PluginLoader {
        private final List<Entry> entries = new CopyOnWriteArrayList<>();

        private final AtomicInteger closed = new AtomicInteger();

        private boolean failInitialization;

        private boolean failUnload;

        @Override
        public boolean supportsVersion(@NotNull String version, @NotNull String range) {
            return true;
        }

        @Override
        public @NotNull LoadedPlugin load(
                @NotNull Path artifact,
                @NotNull PluginDescriptor descriptor,
                @NotNull Class<? extends PluginEntrypoint> entryBase,
                @NotNull Set<String> sharedClasses) {
            Entry entry = new Entry();
            entry.failInitialization = failInitialization;
            entry.failUnload = failUnload;
            entries.add(entry);
            return new LoadedPlugin() {
                @Override
                public @NotNull PluginEntrypoint entrypoint() {
                    return entry;
                }

                @Override
                public @NotNull ClassLoader classLoader() {
                    return Entry.class.getClassLoader();
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }
    }
}
