package com.sca4d.core.api.setting;

import com.sca4d.core.api.exception.CoreApiException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** 在配置加载后初始化一组不再改变的 Java 静态字段。 */
public final class ConfiguredConstants {

    private static final TreeCode READER_CODE = TreeCode.ofColon("CONFIG:ENVIRONMENT");

    private static final SettingOwner OWNER = new SettingOwner(TreeCode.ofSegment("CONFIG"), "PROCESS");

    private static final ThreadLocal<Initialization> INITIALIZATION = new ThreadLocal<>();

    private static final ReentrantLock INITIALIZATION_LOCK = new ReentrantLock();

    private static final Map<Class<?>, Map<String, Value>> GROUPS = new LinkedHashMap<>();

    private ConfiguredConstants() {
    }

    /**
     * 在配置来源就绪后初始化一个常量类，并返回可以交给 Spring 占位符使用的有效值。
     *
     * <p>同一类只能采用相同配置重复初始化。类提前初始化或配置改变时抛出异常；调用方应终止进程启动。
     *
     * @param group 包含通过 {@link #text(String, String)} 声明的静态字段的类
     * @param source 按完整配置键读取外部文本；缺失时返回 null
     * @return 该类全部配置键及其已经校验的有效文本
     */
    public static @NotNull Map<String, String> initialize(
            @NotNull Class<?> group,
            @NotNull Function<String, String> source) {
        INITIALIZATION_LOCK.lock();
        try {
            return initializeLocked(group, source);
        } finally {
            INITIALIZATION_LOCK.unlock();
        }
    }

    private static @NotNull Map<String, String> initializeLocked(
            @NotNull Class<?> group,
            @NotNull Function<String, String> source) {
        Class<?> checkedGroup = Objects.requireNonNull(group, "常量类不能为空");
        Function<String, String> checkedSource = Objects.requireNonNull(source, "配置来源不能为空");
        Map<String, Value> previous = GROUPS.get(checkedGroup);
        if (previous != null) {
            Map<String, String> effective = new LinkedHashMap<>();
            previous.forEach((key, value) -> {
                String current = resolve(key, value.defaultValue, checkedSource);
                if (!value.resolved.equals(current)) {
                    throw new IllegalStateException("常量已经固定，不能在运行中改变: " + key);
                }
                effective.put(key, current);
            });
            return Map.copyOf(effective);
        }
        if (INITIALIZATION.get() != null) {
            throw new IllegalStateException("不能嵌套初始化配置常量");
        }
        Initialization initialization = new Initialization(checkedSource);
        INITIALIZATION.set(initialization);
        try {
            Class.forName(checkedGroup.getName(), true, checkedGroup.getClassLoader());
            if (initialization.values.isEmpty()) {
                throw new IllegalStateException(
                        "常量类已在配置来源就绪前初始化，或没有声明配置常量: "
                                + checkedGroup.getName());
            }
            for (Map.Entry<Class<?>, Map<String, Value>> entry : GROUPS.entrySet()) {
                for (String key : initialization.values.keySet()) {
                    if (entry.getValue().containsKey(key)) {
                        throw new IllegalStateException("配置常量重复声明: " + key);
                    }
                }
            }
            Map<String, Value> values = Map.copyOf(initialization.values);
            GROUPS.put(checkedGroup, values);
            Map<String, String> effective = new LinkedHashMap<>();
            values.forEach((key, value) -> effective.put(key, value.resolved));
            return Map.copyOf(effective);
        } catch (ClassNotFoundException exception) {
            throw new CoreApiException("找不到配置常量类: " + checkedGroup.getName(), exception);
        } finally {
            INITIALIZATION.remove();
        }
    }

    /**
     * 在静态字段声明处解析只读文本；类初始化完成后不再读取外部来源。
     *
     * @param property 完整 Spring 配置键
     * @param defaultValue 外部没有覆盖值时使用的非空默认文本
     * @return 经 Setting Codec 校验的文本，供 static final 字段保存
     * @throws IllegalStateException 常量类在配置来源就绪前初始化时抛出
     */
    @CheckReturnValue
    public static @NotNull String text(@NotNull String property, @NotNull String defaultValue) {
        Initialization initialization = INITIALIZATION.get();
        if (initialization == null) {
            throw new IllegalStateException("配置常量必须在配置加载后初始化: " + property);
        }
        String key = Objects.requireNonNull(property, "配置键不能为空");
        if (!key.matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+")) {
            throw new IllegalArgumentException("配置常量键格式错误: " + key);
        }
        String value = resolve(key, defaultValue, initialization.source);
        if (initialization.values.putIfAbsent(key, new Value(defaultValue, value)) != null) {
            throw new IllegalStateException("配置常量重复声明: " + key);
        }
        return value;
    }

    /** 按 Setting 的读取、默认值和文本校验规则求出启动期有效值。 */
    private static @NotNull String resolve(
            @NotNull String property,
            @NotNull String defaultValue,
            @NotNull Function<String, String> source) {
        TreeCode code = TreeCode.ofDot("CONFIG." + property.toUpperCase(Locale.ROOT).replace('-', '_'));
        String canonical = source.apply(property);
        String variable = source.apply(property.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
        if (canonical != null && variable != null && !canonical.equals(variable)) {
            throw new IllegalStateException("配置键与环境变量值不一致: " + property);
        }
        String raw = canonical == null ? variable : canonical;
        SettingReader<String> reader = new SettingReader<>() {
            @Override
            public @NotNull TreeCode code() {
                return READER_CODE;
            }

            @Override
            public @NotNull SettingValue<String> read(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
                return SettingValue.read(READER_CODE, setting, owner, raw, 0, null, false);
            }
        };
        return Setting.builder(code, SettingCodec.text(512))
                .from(reader)
                .defaultValue(Objects.requireNonNull(defaultValue, "配置常量默认值不能为空"))
                .owner(OWNER)
                .build()
                .get();
    }

    /** 保存当前线程正在初始化的常量类和解析结果。 */
    private static final class Initialization {
        private final Function<String, String> source;

        private final Map<String, Value> values = new LinkedHashMap<>();

        private Initialization(Function<String, String> source) {
            this.source = source;
        }
    }

    /** 保存重复初始化时需要比较的默认值和有效值。 */
    private static final class Value {
        private final String defaultValue;

        private final String resolved;

        private Value(String defaultValue, String resolved) {
            this.defaultValue = defaultValue;
            this.resolved = resolved;
        }
    }
}
