package com.sca4d.common.dictionary;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 不可变代码字典枚举扫描器。
 *
 * <p>扫描器只发现显式声明 {@link CodeDictionary} 的枚举，避免通过字段名称或包位置猜测业务含义。
 */
public class CodeDictionaryScanner {

    /**
     * 扫描指定基础包中的代码字典枚举。
     *
     * <p>每个基础包执行一次 classpath 扫描，每个候选执行一次类加载；最终排序只遍历内存结果，不执行数据库或外部调用。
     *
     * @param basePackages Spring Boot 应用及业务扩展包集合
     * @return 按 Java 完整类名固定排序的代码字典枚举
     */
    public @NotNull List<Class<? extends Enum<?>>> scan(@NotNull Set<String> basePackages) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CodeDictionary.class));
        List<Class<? extends Enum<?>>> enumTypes = new ArrayList<>();
        for (String basePackage : basePackages) {
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                toCodeDictionary(Objects.requireNonNull(candidate.getBeanClassName())).ifPresent(enumTypes::add);
            }
        }
        return enumTypes.stream()
                .distinct()
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    /**
     * 加载扫描候选并确认其同时满足枚举和 BaseEnum 契约。
     *
     * @param className Spring classpath 扫描返回的完整类名
     * @return 合法代码字典枚举
     * @throws IllegalArgumentException 显式声明代码字典的类型不是枚举或没有实现 BaseEnum 时抛出
     * @throws IllegalStateException 扫描候选在加载阶段消失时抛出
     */
    private @NotNull Optional<Class<? extends Enum<?>>> toCodeDictionary(@NotNull String className) {
        try {
            Class<?> type = Class.forName(className);
            if (!type.isEnum()) {
                throw new IllegalArgumentException("@CodeDictionary 只能声明在枚举上: " + className);
            }
            if (!BaseEnum.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("代码字典枚举必须实现 BaseEnum: " + className);
            }
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) type;
            return Optional.of(enumType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("加载代码字典枚举失败: " + className, exception);
        }
    }
}
