package com.sca4d.core.api.isolation;

import com.sca4d.core.api.name.Name;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 在配置加载后一次性发布整个进程使用的 App 隔离名称。 */
public class AppName {

    private static final AtomicReference<Name> VALUE = new AtomicReference<>();

    private AppName() {
    }

    /** 将配置的 App 名转为大写并替换连字符；结果必须是字母、数字或下划线组成的单节点。 */
    public static @NotNull Name normalize(@NotNull String name) {
        String original = Objects.requireNonNull(name, "App 名称不能为空");
        if (!original.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("App 名称必须只包含 ASCII 字母、数字、下划线或连字符");
        }
        String value = original.toUpperCase(Locale.ROOT).replace('-', '_');
        if (!value.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("App 名称归一化后必须只包含字母、数字或下划线");
        }
        return new Name(value);
    }

    /** 原子发布归一化后的名称；重复发布相同名称允许，改变已经发布的名称立即失败。 */
    public static void publish(@NotNull String name) {
        Name value = normalize(name);
        if (!VALUE.compareAndSet(null, value) && !value.equals(VALUE.get())) {
            throw new IllegalStateException("进程的 App 名称已经固定，不能在运行中改变");
        }
    }

    /** 返回已发布的 App 名；启动初始化前使用隔离名称立即失败。 */
    public static @NotNull Name current() {
        Name value = VALUE.get();
        if (value == null) {
            throw new IllegalStateException("App 名称尚未初始化，请在内部设施创建前发布 spring.application.name");
        }
        return value;
    }
}
