package com.sca4d.plugin.pf4j;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.plugin.LoadedPlugin;
import com.sca4d.core.api.plugin.PluginDescriptor;
import com.sca4d.core.api.plugin.PluginEntrypoint;
import com.sca4d.core.api.plugin.PluginLoader;
import org.jetbrains.annotations.NotNull;
import org.pf4j.ClassLoadingStrategy;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.DefaultVersionManager;
import org.pf4j.PluginClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/** 使用 PF4J 的加载器隔离实现与私有依赖，只共享宿主明确公开的类。 */
public final class Pf4jPluginLoader implements PluginLoader {
    private static final Logger LOG = LoggerFactory.getLogger(Pf4jPluginLoader.class);

    private final DefaultVersionManager versions = new DefaultVersionManager();

    @Override
    public boolean supportsVersion(@NotNull String version, @NotNull String range) {
        return versions.checkVersionConstraint(version, range);
    }

    @Override
    public @NotNull LoadedPlugin load(
            @NotNull Path artifact,
            @NotNull PluginDescriptor descriptor,
            @NotNull Class<? extends PluginEntrypoint> entryBase,
            @NotNull Set<String> sharedClasses) throws Exception {
        Set<String> shared = Set.copyOf(sharedClasses);
        if (!shared.contains(entryBase.getName()) || !shared.contains(PluginEntrypoint.class.getName())) {
            throw new IllegalArgumentException("共享类清单缺少 App 或运行入口契约");
        }
        // 在入口类加载前拒绝重复共享类型及嵌套构件，避免同名类由不同加载器定义。
        try (JarFile jar = new JarFile(artifact.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                String className = name.endsWith(".class")
                        ? name.substring(0, name.length() - 6).replace('/', '.')
                        : "";
                if (shared.contains(className)) {
                    throw new IllegalArgumentException("插件重复打包共享类: " + className);
                }
                if (name.startsWith("META-INF/versions/") || name.startsWith("BOOT-INF/")) {
                    throw new IllegalArgumentException("插件交付不接受多版本类或 Boot 嵌套依赖");
                }
            }
        }
        // 每个代次创建独立加载器，私有依赖只从本插件读取，共享类使用宿主定义。
        ClassLoader host = entryBase.getClassLoader();
        DefaultPluginDescriptor pf4jDescriptor = new DefaultPluginDescriptor(
                descriptor.id().format(NameFormat.COLON),
                "",
                descriptor.entry(),
                descriptor.version(),
                "*",
                "",
                "");
        IsolatedClassLoader classLoader = new IsolatedClassLoader(
                new DefaultPluginManager(List.of()),
                pf4jDescriptor,
                host,
                shared);
        try {
            classLoader.addURL(artifact.toUri().toURL());
            // 入口必须继承 App 生成的基类，并通过公开无参构造方法实例化。
            Class<? extends PluginEntrypoint> entry = classLoader.loadClass(descriptor.entry())
                    .asSubclass(entryBase);
            PluginEntrypoint instance = entry.getConstructor().newInstance();
            LOG.debug("插件入口已加载 | plugin={} entry={}", descriptor.id(), descriptor.entry());
            return new Loaded(instance, classLoader);
        } catch (Throwable failure) {
            try {
                classLoader.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /** 限定平台、共享及私有来源，避免 PF4J 默认的宿主回退。 */
    private static final class IsolatedClassLoader extends PluginClassLoader {
        private final ClassLoader host;

        private final Set<String> shared;

        private IsolatedClassLoader(
                DefaultPluginManager manager,
                DefaultPluginDescriptor descriptor,
                ClassLoader host,
                Set<String> shared) {
            super(
                    manager,
                    descriptor,
                    ClassLoader.getPlatformClassLoader(),
                    new ClassLoadingStrategy(List.of(ClassLoadingStrategy.Source.PLUGIN)));
            this.host = host;
            this.shared = shared;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (shared.contains(name)) {
                    return host.loadClass(name);
                }
                try {
                    return ClassLoader.getPlatformClassLoader().loadClass(name);
                } catch (ClassNotFoundException privateClass) {
                    return super.loadClass(name);
                }
            }
        }

        @Override
        protected boolean shouldDelegateToParent(String name) {
            return false;
        }

        @Override
        public URL getResource(String name) {
            return sharedResource(name) ? host.getResource(name) : findResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return sharedResource(name) ? host.getResources(name) : findResources(name);
        }

        /** 共享类资源遵循类定义来源，私有服务索引不合并宿主资源。 */
        private boolean sharedResource(String name) {
            return name.endsWith(".class")
                    && shared.contains(name.substring(0, name.length() - 6).replace('/', '.'));
        }
    }

    /** 保存入口及 PF4J 加载器，并在释放时解除入口引用。 */
    private static final class Loaded implements LoadedPlugin {
        private PluginEntrypoint entry;

        private final IsolatedClassLoader loader;

        private Loaded(PluginEntrypoint entry, IsolatedClassLoader loader) {
            this.entry = entry;
            this.loader = loader;
        }

        @Override
        public @NotNull PluginEntrypoint entrypoint() {
            if (entry == null)
                throw new IllegalStateException("加载资源已经关闭");
            return entry;
        }

        @Override
        public @NotNull ClassLoader classLoader() {
            return loader;
        }

        @Override
        public void close() throws IOException {
            loader.close();
            entry = null;
        }
    }
}
