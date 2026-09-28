package com.sca4d.plugin.spring;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.ApiContract;
import com.sca4d.core.api.plugin.PluginConfiguration;
import com.sca4d.core.api.plugin.PluginEntrypoint;
import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.plugin.pf4j.Pf4jPluginLoader;
import com.sca4d.plugin.runtime.DefaultPluginRuntime;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

/** 根据 App 编译生成的描述创建运行对象，不扫描任意业务类。 */
final class PluginHost {
    private final PluginRuntime runtime;

    private final Map<TreeCode, ApiContract<?>> contracts;

    /** 选择指定 API 描述并读取精确共享清单，缺失或重复时拒绝装配。 */
    PluginHost(PluginProperties properties, ClassLoader classLoader, PluginConfiguration<?> configuration)
            throws Exception {
        TreeCode api = TreeCode.of(Objects.requireNonNull(properties.getApi(), "插件 App API 不能为空"));
        // 只选择配置指定的 App API；同编码的重复描述会使共享类来源不确定，必须拒绝。
        Properties selected = null;
        Enumeration<URL> descriptions = classLoader.getResources("META-INF/sca4dj/api.properties");
        while (descriptions.hasMoreElements()) {
            Properties candidate = new Properties();
            try (InputStream input = descriptions.nextElement().openStream()) {
                candidate.load(input);
            }
            if (api.equals(TreeCode.of(candidate.getProperty("api.id")))) {
                if (selected != null)
                    throw new IllegalStateException("App API 描述重复: " + api);
                selected = candidate;
            }
        }
        if (selected == null)
            throw new IllegalStateException("App API 描述不存在: " + api);
        // 读取生成的精确清单，不从宿主完整 classpath 推断插件可见类型。
        Set<String> shared = new HashSet<>();
        Enumeration<URL> indices = classLoader.getResources(selected.getProperty("api.shared-index"));
        int indexCount = 0;
        while (indices.hasMoreElements()) {
            if (++indexCount > 1)
                throw new IllegalStateException("App API 共享清单重复");
            try (InputStream input = indices.nextElement().openStream();
                    Scanner scanner = new Scanner(input, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine())
                    shared.add(scanner.nextLine());
            }
        }
        if (indexCount != 1)
            throw new IllegalStateException("App API 共享清单不存在");
        // 配置类型由 App API 声明，App 提供者必须返回宿主加载的同一份类型。
        Class<?> configurationType = Class.forName(
                Objects.requireNonNull(selected.getProperty("api.configuration-type"), "App API 缺少公开配置类型"),
                false,
                classLoader);
        if (configuration.type() != configurationType || !shared.contains(configurationType.getName())) {
            throw new IllegalStateException("App 插件配置提供者与公开配置类型不一致");
        }
        // 先登记生成契约，实际宿主 Bean 在单例创建完成后由生命周期回调绑定。
        Class<? extends PluginEntrypoint> entry = Class.forName(selected.getProperty("api.entry"), false, classLoader)
                .asSubclass(PluginEntrypoint.class);
        runtime = new DefaultPluginRuntime<>(
                api,
                selected.getProperty("api.version"),
                entry,
                shared,
                new Pf4jPluginLoader(),
                Path.of(properties.getCacheDirectory()),
                configuration);
        Map<TreeCode, ApiContract<?>> declarations = new LinkedHashMap<>();
        for (String name : selected.getProperty("api.contracts").split(",")) {
            ApiContract<?> contract = (ApiContract<?>) Class.forName(name, true, classLoader)
                    .getField("INSTANCE")
                    .get(null);
            declarations.put(contract.definition().code(), contract);
            runtime.declare(contract);
        }
        contracts = Map.copyOf(declarations);
    }

    /** 返回 Core 接口，不对 App 暴露具体运行实现。 */
    @NotNull
    PluginRuntime runtime() {
        return runtime;
    }

    /** 返回准确声明的契约，未知编码直接失败。 */
    @NotNull
    ApiContract<?> contract(TreeCode code) {
        ApiContract<?> contract = contracts.get(code);
        if (contract == null)
            throw new IllegalArgumentException("契约未公开: " + code);
        return contract;
    }
}
