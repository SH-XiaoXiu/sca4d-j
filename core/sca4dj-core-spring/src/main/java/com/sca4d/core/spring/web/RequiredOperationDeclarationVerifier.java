package com.sca4d.core.spring.web;

import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.permission.OperationCatalog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 在应用启动时检查 Controller 上 {@link RequirePermission} 声明的固定 Operation 都存在于 {@link OperationCatalog}。
 *
 * <p>只读取 {@code operation} 非空白的声明；使用 {@code resolver} 或 {@code resource} 的动态资源声明不检查。
 * 同时读取 Controller 类和处理方法上的声明，重复注解容器 {@code RequirePermissions} 中的每一项都会检查。
 */
public class RequiredOperationDeclarationVerifier implements SmartInitializingSingleton {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(RequiredOperationDeclarationVerifier.class);

    private final @NotNull OperationCatalog catalog;

    private final @NotNull RequestMappingHandlerMapping mappings;

    /**
     * 创建 Operation 声明校验器。
     *
     * @param mappings 应用实际注册的 Spring MVC 路由
     * @param catalog  应用安装的完整 Operation 目录
     */
    public RequiredOperationDeclarationVerifier(
            @NotNull RequestMappingHandlerMapping mappings,
            @NotNull OperationCatalog catalog) {
        this.mappings = Objects.requireNonNull(mappings, "Spring MVC 路由不能为空");
        this.catalog = Objects.requireNonNull(catalog, "Operation 目录不能为空");
    }

    /**
     * 逐个检查全部处理方法的 Operation 声明。
     *
     * @throws IllegalStateException 任一声明不是合法 Operation 编码，或在目录中没有对应的叶子时抛出；
     *                               消息按声明位置列出每个 {@code 类#方法} 和对应的文本，类级声明只列出类名
     */
    @Override
    public void afterSingletonsInstantiated() {
        // 类级声明被同一个 Controller 的所有方法共享，用有序集合去重后每处只报告一次。
        Set<String> failures = new TreeSet<>();
        int checked = 0;
        for (HandlerMethod handler : mappings.getHandlerMethods().values()) {
            Class<?> controller = handler.getBeanType();
            checked += verify(controller.getName(), controller, failures);
            checked += verify(
                    controller.getName() + "#" + handler.getMethod().getName(),
                    handler.getMethod(),
                    failures);
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "@RequirePermission 声明了 Operation 目录中不存在的 Operation | count="
                            + failures.size()
                            + "\n"
                            + String.join("\n", failures));
        }
        LOG.debug(
                "已校验 Controller 声明的固定 Operation | handlers={} operations={}",
                mappings.getHandlerMethods().size(),
                checked);
    }

    /**
     * 检查一个类或方法上的全部 {@link RequirePermission} 声明。
     *
     * @return 检查过的固定 Operation 数量
     */
    private int verify(@NotNull String location, @NotNull AnnotatedElement element, @NotNull Set<String> failures) {
        List<String> operations = new ArrayList<>();
        for (RequirePermission permission : AnnotatedElementUtils
                .findMergedRepeatableAnnotations(element, RequirePermission.class)) {
            if (!permission.operation().isBlank()) {
                operations.add(permission.operation());
            }
        }
        for (String operation : operations) {
            if (!declared(operation)) {
                failures.add("  " + location + " -> " + operation);
            }
        }
        return operations.size();
    }

    /** @return 文本是合法 Operation 编码且目录中存在对应叶子时为 {@code true} */
    private boolean declared(@NotNull String operation) {
        try {
            return catalog.leaf(operation).isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
