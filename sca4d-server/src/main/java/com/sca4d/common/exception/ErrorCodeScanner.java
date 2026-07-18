package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 错误码枚举扫描器。
 *
 * <p>扫描指定基础包下所有实现 {@link BizErrorCode} 的枚举类。扫描结果只用于构建错误码目录，
 * 不参与业务操作。
 */
public class ErrorCodeScanner {

    /**
     * 扫描错误码枚举类。
     *
     * <p>每个基础包执行一次 classpath 扫描，每个扫描候选执行一次类加载；最终排序只遍历内存结果，不产生额外外部调用。
     *
     * @param basePackages 基础包集合
     * @return 错误码枚举类列表
     */
    public @NotNull List<Class<? extends Enum<?>>> scan(@NotNull Set<String> basePackages) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(BizErrorCode.class));
        List<Class<? extends Enum<?>>> enumTypes = new ArrayList<>();
        for (String basePackage : basePackages) {
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                toEnumType(Objects.requireNonNull(candidate.getBeanClassName())).ifPresent(enumTypes::add);
            }
        }
        return enumTypes.stream()
                .distinct()
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    /**
     * 加载扫描候选并确认其同时是枚举和 BizErrorCode 实现。
     *
     * @param className Spring classpath 扫描返回的完整类名
     * @return 符合错误码契约的枚举类型；其他候选返回空
     * @throws IllegalStateException 扫描候选在加载阶段消失时抛出
     */
    private @NotNull Optional<Class<? extends Enum<?>>> toEnumType(@NotNull String className) {
        try {
            Class<?> type = Class.forName(className);
            if (!type.isEnum() || !BizErrorCode.class.isAssignableFrom(type)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) type;
            return Optional.of(enumType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("加载错误码枚举失败: " + className, exception);
        }
    }
}
