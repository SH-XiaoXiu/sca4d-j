package com.sca4d.core.spring.dictionary;

import com.sca4d.core.api.dictionary.Dictionary;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 在应用和 Sca4d-J 包中查找实现 `Dictionary` 的枚举，不读取注解或数据库。
 */
public final class DictionaryScanner {

    private final ClassLoader classLoader;

    /**
     * @param classLoader 加载扫描结果使用的应用 ClassLoader
     */
    public DictionaryScanner(@NotNull ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "应用 ClassLoader 不能为空");
    }

    /**
     * 查找指定包下全部 Dictionary 枚举。
     *
     * @param packages 应用基础包与 Sca4d-J 公共包
     * @return 按完整类名排序的枚举类型
     */
    public @NotNull List<@NotNull Class<? extends Enum<?>>> scan(
            @NotNull Collection<@NotNull String> packages) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Dictionary.class));
        scanner.setResourceLoader(new DefaultResourceLoader(classLoader));

        Set<Class<? extends Enum<?>>> types = new LinkedHashSet<>();
        for (String basePackage : new LinkedHashSet<>(Objects.requireNonNull(packages, "扫描包不能为空"))) {
            for (BeanDefinition candidate : scanner
                    .findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                if (className == null) {
                    throw new IllegalStateException("Dictionary 扫描结果缺少类名");
                }
                Class<?> type;
                try {
                    type = ClassUtils.forName(className, classLoader);
                } catch (ClassNotFoundException | LinkageError exception) {
                    throw new IllegalStateException("无法加载 Dictionary 类型: " + className, exception);
                }
                if (!type.isEnum()) {
                    throw new IllegalStateException("Dictionary 只能由枚举实现: " + className);
                }
                @SuppressWarnings("unchecked")
                Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) type;
                types.add(enumType);
            }
        }
        List<Class<? extends Enum<?>>> result = new ArrayList<>(types);
        result.sort(Comparator.comparing(Class::getName));
        return List.copyOf(result);
    }
}
