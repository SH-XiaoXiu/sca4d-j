package com.sca4d.plugin.runtime;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.ApiBinding;
import com.sca4d.core.api.plugin.ApiContract;
import com.sca4d.core.api.plugin.ApiKind;
import com.sca4d.core.api.plugin.ApiOperation;
import com.sca4d.core.api.plugin.ApiTarget;
import com.sca4d.core.api.plugin.LoadedPlugin;
import com.sca4d.core.api.plugin.PluginConfiguration;
import com.sca4d.core.api.plugin.PluginConfigurationSnapshot;
import com.sca4d.core.api.plugin.PluginContext;
import com.sca4d.core.api.plugin.PluginDescriptor;
import com.sca4d.core.api.plugin.PluginEntrypoint;
import com.sca4d.core.api.plugin.PluginLease;
import com.sca4d.core.api.plugin.PluginLoader;
import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.core.api.plugin.PluginStatus;
import com.sca4d.core.api.plugin.PluginUpdate;
import com.sca4d.core.api.plugin.exception.PluginUnavailableException;
import com.sca4d.plugin.runtime.exception.PluginException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** 管理一个 App 的绑定快照和插件代次，在同步操作结束后释放旧实现。 */
public class DefaultPluginRuntime<C> implements PluginRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPluginRuntime.class);

    private final Class<C> configurationType;

    private final Class<? extends PluginEntrypoint> entryBase;

    private final Path cache;

    private final PluginConfiguration<C> configuration;

    private final PluginLoader loader;

    private final Set<String> sharedClasses;

    private final String apiVersion;

    private final TreeCode api;

    private final ReentrantLock management = new ReentrantLock();

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition changed = lock.newCondition();

    private final Map<TreeCode, ApiContract<?>> contracts = new LinkedHashMap<>();

    private final Map<ApiTarget, OwnedBinding> services = new LinkedHashMap<>();

    private final Map<TreeCode, Generation> active = new LinkedHashMap<>();

    private final List<Generation> generations = new ArrayList<>();

    private final ThreadLocal<Frame> scope = new ThreadLocal<>();

    private long sequence;

    private boolean closed;

    /** 保存宿主 API、共享类清单与具体加载实现；不扫描宿主 classpath。 */
    public DefaultPluginRuntime(
            @NotNull TreeCode api,
            @NotNull String apiVersion,
            @NotNull Class<? extends PluginEntrypoint> entryBase,
            @NotNull Set<String> sharedClasses,
            @NotNull PluginLoader loader,
            @NotNull Path cache,
            @NotNull PluginConfiguration<C> configuration) {
        this.api = Objects.requireNonNull(api);
        this.apiVersion = Objects.requireNonNull(apiVersion);
        this.entryBase = Objects.requireNonNull(entryBase);
        this.sharedClasses = Set.copyOf(sharedClasses);
        this.loader = Objects.requireNonNull(loader);
        this.cache = Objects.requireNonNull(cache);
        this.configuration = Objects.requireNonNull(configuration);
        this.configurationType = Objects.requireNonNull(configuration.type());
    }

    @Override
    public void declare(@NotNull ApiContract<?> contract) {
        lock.lock();
        try {
            ensureConfigurable();
            if (contracts.putIfAbsent(contract.definition().code(), contract) != null) {
                throw new IllegalArgumentException("契约编码重复: " + contract.definition().code());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void bind(@NotNull ApiBinding binding) {
        lock.lock();
        try {
            ensureConfigurable();
            validateBinding(binding, ApiKind.SERVICE);
            if (services.putIfAbsent(binding.target(), new OwnedBinding(binding, null)) != null) {
                throw new IllegalArgumentException("宿主服务重复: " + binding.target());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public @NotNull PluginUpdate replace(@NotNull Path artifact, @NotNull Duration drainTimeout) {
        return installGeneration(artifact, drainTimeout, null);
    }

    @Override
    public @NotNull PluginUpdate reload(@NotNull TreeCode id, @NotNull Duration drainTimeout) {
        validateManagement(drainTimeout);
        management.lock();
        try {
            Path artifact;
            lock.lock();
            try {
                Generation current = active.get(Objects.requireNonNull(id));
                if (current == null) {
                    throw new PluginUnavailableException("插件未启用: " + id);
                }
                artifact = current.artifact;
            } finally {
                lock.unlock();
            }
            // 固定活动构件后再复制，管理锁阻止它在重载读取期间被其他更新释放。
            return installGeneration(artifact, drainTimeout, id);
        } finally {
            management.unlock();
        }
    }

    /** 将构件和 App 配置作为同一个候选代次初始化、校验并发布，重载时保持插件编码。 */
    private @NotNull PluginUpdate installGeneration(
            @NotNull Path artifact,
            @NotNull Duration drainTimeout,
            @Nullable TreeCode reloadedId) {
        validateManagement(drainTimeout);
        management.lock();
        try {
            lock.lock();
            try {
                if (closed) {
                    throw new PluginUnavailableException("插件运行机制已关闭");
                }
            } finally {
                lock.unlock();
            }

            // 先固定交付物，后续更新原始路径不会改变已经加载的字节。
            Files.createDirectories(cache);
            Path copy = Files.createTempFile(cache, "plugin-", ".jar");
            Generation candidate = null;
            try {
                Files.copy(artifact, copy, StandardCopyOption.REPLACE_EXISTING);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = Files.newInputStream(copy)) {
                    byte[] buffer = new byte[8192];
                    for (int count; (count = input.read(buffer)) >= 0;) {
                        digest.update(buffer, 0, count);
                    }
                }
                // App 解释资源字段并提供独立配置；框架不规定 YAML 路径或元信息的键。
                PluginConfigurationSnapshot<C> configured = Objects.requireNonNull(
                        reloadedId == null ? configuration.load(copy) : configuration.reload(copy),
                        "App 插件配置不能为空");
                candidate = new Generation(
                        configured,
                        ++sequence,
                        copy,
                        HexFormat.of().formatHex(digest.digest()));
                PluginDescriptor descriptor = configured.descriptor();
                if (reloadedId != null && !reloadedId.equals(descriptor.id())) {
                    throw new IllegalArgumentException("配置重载不能改变插件编码");
                }
                if (!configurationType.isInstance(configured.values())) {
                    throw new IllegalArgumentException("App 插件配置类型与声明不一致");
                }
                checkSharedValue(configured.values(), Collections.newSetFromMap(new IdentityHashMap<>()));
                // 在创建加载器前核对插件身份和 API 版本，避免不兼容入口执行初始化。
                if (!loader.supportsVersion(descriptor.version(), "*")
                        || !api.equals(descriptor.api())
                        || !loader.supportsVersion(apiVersion, descriptor.requiresApi())) {
                    throw new IllegalArgumentException("插件 App API 或版本不兼容");
                }
                LOG.trace("App 插件配置已读取 | plugin={} reload={}", descriptor.id(), reloadedId != null);
                // 候选在独立加载器中登记实现；此时调用目录仍使用旧代次。
                LoadedPlugin loaded = loader.load(copy, descriptor, entryBase, sharedClasses);
                candidate.loaded = loaded;
                ClassLoader before = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(loaded.classLoader());
                    loaded.entrypoint().onLoad(new CandidateContext(candidate));
                } finally {
                    Thread.currentThread().setContextClassLoader(before);
                }

                // 发布前检查全部候选绑定，冲突时保留原活动目录。
                Generation previous;
                lock.lock();
                try {
                    if (closed) {
                        throw new PluginUnavailableException("插件运行机制已关闭");
                    }
                    previous = active.get(descriptor.id());
                    Map<ApiTarget, OwnedBinding> next = snapshot(previous);
                    for (ApiBinding binding : candidate.bindings.values()) {
                        if (next.putIfAbsent(binding.target(), new OwnedBinding(binding, candidate)) != null) {
                            throw new IllegalArgumentException("扩展实现编码冲突: " + binding.target());
                        }
                    }
                    if (previous != null && !candidate.bindings.keySet().containsAll(previous.bindings.keySet())) {
                        throw new IllegalArgumentException("更新不得移除旧代次已经提供的扩展");
                    }
                    candidate.state = PluginStatus.State.ACTIVE;
                    generations.add(candidate);
                    active.put(descriptor.id(), candidate);
                    if (previous != null) {
                        previous.state = PluginStatus.State.DRAINING;
                    }
                } finally {
                    lock.unlock();
                }
                LOG.info(
                        "插件代次已启用 | plugin={} version={} generation={}",
                        descriptor.id(),
                        descriptor.version(),
                        candidate.number);
                // 新调用已经使用候选；等待旧租用结束不会阻塞新调用，也不会撤销发布结果。
                if (previous != null) {
                    drain(previous, drainTimeout);
                }
                return new PluginUpdate(status(candidate), previous == null ? null : status(previous));
            } catch (Throwable failure) {
                if (candidate != null && candidate.state != PluginStatus.State.ACTIVE) {
                    cleanupCandidate(candidate, failure);
                } else if (candidate == null) {
                    try {
                        Files.deleteIfExists(copy);
                    } catch (IOException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw new PluginException("插件候选加载失败: " + failure.getClass().getSimpleName(), failure);
            }
        } catch (IOException failure) {
            throw new PluginException("插件交付物读取失败", failure);
        } finally {
            management.unlock();
        }
    }

    @Override
    public @NotNull PluginStatus stop(@NotNull TreeCode id, @NotNull Duration drainTimeout) {
        validateManagement(drainTimeout);
        management.lock();
        try {
            Generation generation;
            lock.lock();
            try {
                generation = active.get(id);
                if (generation == null) {
                    throw new PluginUnavailableException("插件未启用: " + id);
                }
                for (ApiBinding binding : generation.bindings.values()) {
                    if (binding.definition().required()) {
                        throw new IllegalStateException("必需扩展只能替换或随 App 关闭: " + binding.target());
                    }
                }
                active.remove(id);
                generation.state = PluginStatus.State.DRAINING;
            } finally {
                lock.unlock();
            }
            drain(generation, drainTimeout);
            return status(generation);
        } finally {
            management.unlock();
        }
    }

    @Override
    public @NotNull PluginLease openScope() {
        // 嵌套调用复用外层快照，防止一次业务操作混用更新前后的实现。
        Frame existing = scope.get();
        if (existing != null) {
            existing.depth++;
            return new Lease(existing);
        }
        lock.lock();
        try {
            if (closed) {
                throw new PluginUnavailableException("插件运行机制已关闭");
            }
            // 在同一把锁下保存目录并增加使用计数，旧代次不能在快照建立期间被释放。
            Frame frame = new Frame(snapshot(null), Thread.currentThread());
            frame.generations.forEach(generation -> generation.uses++);
            scope.set(frame);
            return new Lease(frame);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public @Nullable Object invoke(
            @NotNull ApiTarget target,
            @NotNull TreeCode operation,
            Object @NotNull [] arguments) throws Throwable {
        try (PluginLease ignored = openScope()) {
            // 动态入口也检查操作签名，不能绕过生成客户端传入不兼容参数。
            OwnedBinding owned = scope.get().bindings.get(target);
            if (owned == null) {
                throw new PluginUnavailableException("公开目标不可用: " + target);
            }
            ApiOperation signature = owned.binding.definition().operations().get(operation);
            if (signature == null) {
                throw new IllegalArgumentException("操作未公开: " + operation);
            }
            if (signature.parameters().size() != arguments.length) {
                throw new IllegalArgumentException("调用参数数量不一致");
            }
            for (int index = 0; index < arguments.length; index++) {
                checkValue(
                        signature.parameters().get(index),
                        arguments[index],
                        signature.nullableParameters().get(index));
                checkSharedValue(arguments[index], Collections.newSetFromMap(new IdentityHashMap<>()));
            }
            // 在原线程调用目标对象，临时切换 TCCL；异常和正常返回都恢复宿主线程状态。
            ClassLoader before = Thread.currentThread().getContextClassLoader();
            try {
                if (owned.owner != null) {
                    Thread.currentThread().setContextClassLoader(owned.owner.loaded.classLoader());
                } else {
                    Thread.currentThread().setContextClassLoader(entryBase.getClassLoader());
                }
                LOG.trace("公开操作开始调用 | target={} operation={}", target, operation);
                Object result = owned.binding.operations().get(operation).invoke(arguments);
                // 返回前检查类型来源，防止插件私有对象被宿主保存并阻止旧加载器释放。
                if (signature.resultType() != void.class) {
                    checkValue(signature.resultType(), result, signature.nullableResult());
                    checkSharedValue(result, Collections.newSetFromMap(new IdentityHashMap<>()));
                } else if (result != null) {
                    throw new IllegalStateException("void 操作不得返回结果");
                }
                return result;
            } finally {
                Thread.currentThread().setContextClassLoader(before);
            }
        }
    }

    @Override
    public void verifyRequired() {
        lock.lock();
        try {
            Set<TreeCode> available = new HashSet<>();
            snapshot(null).keySet().forEach(target -> available.add(target.code()));
            for (ApiContract<?> contract : contracts.values()) {
                if (contract.definition().required() && !available.contains(contract.definition().code())) {
                    throw new PluginUnavailableException("必需扩展未加载: " + contract.definition().code());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public @NotNull List<PluginStatus> statuses() {
        lock.lock();
        try {
            return generations.stream().map(this::status).toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        validateManagement(Duration.ZERO);
        management.lock();
        try {
            List<Generation> stopped;
            lock.lock();
            try {
                closed = true;
                stopped = new ArrayList<>(active.values());
                active.clear();
                stopped.forEach(generation -> generation.state = PluginStatus.State.DRAINING);
            } finally {
                lock.unlock();
            }
            stopped.forEach(generation -> drain(generation, Duration.ZERO));
        } finally {
            management.unlock();
        }
    }

    /** 检查构造期登记；插件运行后不允许修改宿主契约。 */
    private void ensureConfigurable() {
        if (closed || !generations.isEmpty()) {
            throw new IllegalStateException("宿主契约只能在插件加载前登记");
        }
    }

    /** 按当前活动代次生成完整快照，候选替换时排除旧代次。 */
    private Map<ApiTarget, OwnedBinding> snapshot(Generation excluded) {
        Map<ApiTarget, OwnedBinding> result = new LinkedHashMap<>(services);
        for (Generation generation : active.values()) {
            if (generation != excluded) {
                generation.bindings
                        .forEach((target, binding) -> result.put(target, new OwnedBinding(binding, generation)));
            }
        }
        return result;
    }

    /** 检查绑定使用宿主已经声明的同一份契约。 */
    private void validateBinding(ApiBinding binding, ApiKind kind) {
        ApiContract<?> declared = contracts.get(binding.definition().code());
        if (declared == null || declared.definition() != binding.definition() || binding.definition().kind() != kind) {
            throw new IllegalArgumentException("绑定不是宿主公开契约: " + binding.target());
        }
    }

    /** 防止管理操作等待调用它自己的租用，并校验等待时间。 */
    private void validateManagement(Duration timeout) {
        if (scope.get() != null || timeout.isNegative()) {
            throw new IllegalStateException("管理操作必须在调用作用域外执行，等待时间不得为负");
        }
    }

    /** 在指定时间内等待代次停止；超时保留 DRAINING，不撤销已生效的新版本。 */
    private void drain(Generation generation, Duration timeout) {
        // 条件等待会释放目录锁，租用归还后唤醒；超时不会强行终止正在执行的代码。
        long remaining;
        try {
            remaining = timeout.toNanos();
        } catch (ArithmeticException overflow) {
            remaining = Long.MAX_VALUE;
        }
        lock.lock();
        try {
            while (generation.uses > 0 && remaining > 0) {
                try {
                    remaining = changed.awaitNanos(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        if (status(generation).uses() > 0) {
            LOG.warn(
                    "插件仍有活动租用，旧代次将在租用归还后释放 | plugin={} generation={} interrupted={}",
                    generation.descriptor.id(),
                    generation.number,
                    Thread.currentThread().isInterrupted());
        }
        cleanup(generation);
    }

    /** 取得清理权后关闭入口和加载器；失败时保留真实状态供诊断。 */
    private void cleanup(Generation generation) {
        // 管理线程和最后一个归还租用的线程都可能触发清理，只有取得清理权的线程执行卸载。
        lock.lock();
        try {
            if (generation.uses != 0 || generation.cleaning || generation.state != PluginStatus.State.DRAINING) {
                return;
            }
            generation.cleaning = true;
        } finally {
            lock.unlock();
        }
        // 任一卸载回调失败仍继续释放其余资源，最后只保留不持有实现对象的失败状态。
        Throwable exception = releaseResources(generation);
        String failure = exception == null ? null : exception.getClass().getName();
        if (exception != null) {
            LOG.error(
                    "插件代次释放失败 | plugin={} generation={} failureType={}",
                    generation.descriptor.id(),
                    generation.number,
                    failure,
                    exception);
        }
        lock.lock();
        try {
            generation.failure = failure;
            generation.state = failure == null ? PluginStatus.State.STOPPED : PluginStatus.State.FAILED;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        LOG.info(
                "插件代次停止结果 | plugin={} generation={} state={}",
                generation.descriptor.id(),
                generation.number,
                generation.state);
    }

    /** 候选初始化失败后尝试停止并关闭加载器，清理错误保留为原失败的附加异常。 */
    private void cleanupCandidate(Generation candidate, Throwable failure) {
        Throwable cleanupFailure = releaseResources(candidate);
        if (cleanupFailure != null) {
            failure.addSuppressed(cleanupFailure);
        }
        candidate.state = cleanupFailure == null ? PluginStatus.State.STOPPED : PluginStatus.State.FAILED;
    }

    /** 分别释放插件入口、App 配置、加载器和构件副本，汇总失败并解除代次对实现对象的引用。 */
    private @Nullable Throwable releaseResources(Generation generation) {
        Throwable failure = null;
        LoadedPlugin loaded = generation.loaded;
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        try {
            if (loaded != null) {
                Thread.currentThread().setContextClassLoader(loaded.classLoader());
                loaded.entrypoint().onUnload();
            }
        } catch (Throwable exception) {
            failure = exception;
        } finally {
            Thread.currentThread().setContextClassLoader(before);
        }
        try {
            configuration.unload(Objects.requireNonNull(generation.configuration));
        } catch (Throwable exception) {
            if (failure == null)
                failure = exception;
            else if (failure != exception)
                failure.addSuppressed(exception);
        }
        if (loaded != null) {
            try {
                loaded.close();
            } catch (Throwable exception) {
                if (failure == null)
                    failure = exception;
                else if (failure != exception)
                    failure.addSuppressed(exception);
            }
        }
        try {
            Files.deleteIfExists(generation.artifact);
        } catch (Throwable exception) {
            if (failure == null)
                failure = exception;
            else if (failure != exception)
                failure.addSuppressed(exception);
        }
        generation.bindings.clear();
        generation.loaded = null;
        generation.configuration = null;
        return failure;
    }

    /** 返回持锁读取的诊断快照。 */
    private PluginStatus status(Generation generation) {
        lock.lock();
        try {
            return new PluginStatus(
                    generation.descriptor,
                    generation.number,
                    generation.state,
                    generation.uses,
                    generation.digest,
                    generation.failure);
        } finally {
            lock.unlock();
        }
    }

    /** 检查空值和基本类型装箱，不允许动态调用绕过参数签名。 */
    private static void checkValue(Class<?> type, Object value, boolean nullable) {
        Map<Class<?>, Class<?>> boxed = Map.of(
                boolean.class,
                Boolean.class,
                byte.class,
                Byte.class,
                short.class,
                Short.class,
                int.class,
                Integer.class,
                long.class,
                Long.class,
                float.class,
                Float.class,
                double.class,
                Double.class,
                char.class,
                Character.class);
        if (value == null ? !nullable || type.isPrimitive() : !boxed.getOrDefault(type, type).isInstance(value)) {
            throw new IllegalArgumentException("调用值与公开类型不一致: " + type.getName());
        }
    }

    /** 禁止私有实现对象逃出调用范围，容器元素也必须属于平台或共享类型。 */
    private void checkSharedValue(Object value, Set<Object> visited) {
        // 用对象身份去重，允许循环容器；检查容器中的实际值，而不只检查擦除后的签名。
        if (value == null || !visited.add(value))
            return;
        Class<?> type = value.getClass();
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive())
                return;
            for (int index = 0; index < Array.getLength(value); index++)
                checkSharedValue(Array.get(value, index), visited);
            return;
        }
        ClassLoader defining = type.getClassLoader();
        // 类名相同不代表 Java 类型相同，共享类型必须是宿主加载器提供的同一个 Class。
        if (defining != null && defining != ClassLoader.getPlatformClassLoader()) {
            if (!sharedClasses.contains(type.getName())) {
                throw new IllegalArgumentException("调用不能传递非共享实现类型: " + type.getName());
            }
            try {
                if (entryBase.getClassLoader().loadClass(type.getName()) != type) {
                    throw new IllegalArgumentException("调用类型不是宿主共享定义");
                }
            } catch (ClassNotFoundException failure) {
                throw new IllegalArgumentException("共享类型不存在", failure);
            }
        }
        if (value instanceof Collection<?> collection)
            collection.forEach(item -> checkSharedValue(item, visited));
        if (value instanceof Map<?, ?> map)
            map.forEach((key, item) -> {
                checkSharedValue(key, visited);
                checkSharedValue(item, visited);
            });
        if (value instanceof Optional<?> optional)
            optional.ifPresent(item -> checkSharedValue(item, visited));
        if (value instanceof Class<?> represented
                && represented.getClassLoader() != null
                && represented.getClassLoader() != ClassLoader.getPlatformClassLoader()
                && !sharedClasses.contains(represented.getName())) {
            throw new IllegalArgumentException("调用不能传递私有 Class 对象");
        }
    }

    /** 保存当前插件的候选登记范围，关闭后不允许遗留上下文继续调用。 */
    private final class CandidateContext implements PluginContext {
        private final Generation owner;

        private CandidateContext(Generation owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull PluginDescriptor descriptor() {
            return owner.descriptor;
        }

        @Override
        public <T> @NotNull T configuration(@NotNull Class<T> type) {
            lock.lock();
            try {
                Frame frame = scope.get();
                if (owner.state != null
                        && owner.state != PluginStatus.State.ACTIVE
                        && !(owner.state == PluginStatus.State.DRAINING
                                && frame != null
                                && frame.generations.contains(owner))) {
                    throw new PluginUnavailableException("插件代次已停止");
                }
                if (type != configurationType) {
                    throw new IllegalArgumentException("请求的配置类型不是 App 公开类型");
                }
                return type.cast(Objects.requireNonNull(owner.configuration).values());
            } finally {
                lock.unlock();
            }
        }

        @Override
        public <T> @NotNull T service(@NotNull ApiContract<T> contract) {
            lock.lock();
            try {
                if (contracts.get(contract.definition().code()) != contract
                        || contract.definition().kind() != ApiKind.SERVICE) {
                    throw new IllegalArgumentException("宿主服务未公开");
                }
            } finally {
                lock.unlock();
            }
            return contract.client((target, operation, arguments) -> {
                Frame frame = scope.get();
                if (owner.state != PluginStatus.State.ACTIVE
                        && !(owner.state == PluginStatus.State.DRAINING
                                && frame != null
                                && frame.generations.contains(owner))
                        && owner.state != null) {
                    throw new PluginUnavailableException("插件代次已停止");
                }
                return DefaultPluginRuntime.this.invoke(target, operation, arguments);
            }, null);
        }

        @Override
        public <T> void register(@NotNull ApiContract<T> contract, @NotNull T implementation, @Nullable TreeCode key) {
            lock.lock();
            try {
                if (owner.state != null) {
                    throw new IllegalStateException("候选发布后不能登记扩展");
                }
                ApiBinding binding = contract.bind(implementation, key);
                validateBinding(binding, ApiKind.EXTENSION);
                // 在发布前发现旧 API 编译出的缺失方法，避免业务首次调用才出现链接错误。
                for (var method : contract.type().getMethods()) {
                    try {
                        var actual = implementation.getClass().getMethod(method.getName(), method.getParameterTypes());
                        if (Modifier.isAbstract(actual.getModifiers())
                                || !method.getReturnType().isAssignableFrom(actual.getReturnType())) {
                            throw new IllegalArgumentException("插件没有实现当前 API 方法: " + method.getName());
                        }
                    } catch (NoSuchMethodException failure) {
                        throw new IllegalArgumentException("插件 API 方法不兼容", failure);
                    }
                }
                if (owner.bindings.putIfAbsent(binding.target(), binding) != null) {
                    throw new IllegalArgumentException("插件扩展重复登记");
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /** 保存一次线程绑定快照的嵌套深度与实际插件租用。 */
    private final class Frame {
        private Map<ApiTarget, OwnedBinding> bindings;

        private final Set<Generation> generations = new HashSet<>();

        private final Thread thread;

        private int depth = 1;

        private Frame(Map<ApiTarget, OwnedBinding> bindings, Thread thread) {
            this.bindings = Map.copyOf(bindings);
            bindings.values().forEach(binding -> {
                if (binding.owner != null)
                    generations.add(binding.owner);
            });
            this.thread = thread;
        }
    }

    /** 在最后一个嵌套范围关闭时解除线程绑定，再清理已无使用者的旧代次。 */
    private final class Lease implements PluginLease {
        private final Frame frame;

        private boolean released;

        private Lease(Frame frame) {
            this.frame = frame;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != frame.thread) {
                throw new IllegalStateException("插件作用域必须在原线程关闭");
            }
            if (released)
                return;
            released = true;
            if (--frame.depth != 0)
                return;
            scope.remove();
            // 先解除线程和快照引用，再减少租用计数；旧代次清理不会留下当前范围的强引用。
            List<Generation> owners = List.copyOf(frame.generations);
            lock.lock();
            try {
                owners.forEach(generation -> generation.uses--);
                frame.bindings = Map.of();
                frame.generations.clear();
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            owners.forEach(DefaultPluginRuntime.this::cleanup);
        }
    }

    /** 保存已声明操作及它所属的代次；宿主服务没有插件所有者。 */
    private final class OwnedBinding {
        private final ApiBinding binding;

        private final Generation owner;

        private OwnedBinding(ApiBinding binding, Generation owner) {
            this.binding = binding;
            this.owner = owner;
        }
    }

    /** 保存可释放的实现资源；停止成功后清除入口和分发器引用。 */
    private final class Generation {
        private final PluginDescriptor descriptor;

        private @Nullable PluginConfigurationSnapshot<C> configuration;

        private final long number;

        private LoadedPlugin loaded;

        private final Path artifact;

        private final String digest;

        private final Map<ApiTarget, ApiBinding> bindings = new LinkedHashMap<>();

        private volatile PluginStatus.State state;

        private int uses;

        private boolean cleaning;

        private String failure;

        private Generation(PluginConfigurationSnapshot<C> configuration, long number, Path artifact, String digest) {
            this.descriptor = configuration.descriptor();
            this.configuration = configuration;
            this.number = number;
            this.artifact = artifact;
            this.digest = digest;
        }
    }
}
