package com.sca4d.core.spring.identity;

import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/** 查找 Controller 声明的 {@link CurrentPrincipal}，并取得声明指定的选择器。 */
class CurrentPrincipalDeclaration {

    private CurrentPrincipalDeclaration() {
    }

    /**
     * 按方法、Controller 类型和方法参数的顺序查找当前业务身份声明。
     *
     * @param method 当前 Controller 方法
     * @param controllerType 当前 Controller 类型
     * @return 找到的声明；当前方法不选择业务身份时为空
     */
    static @Nullable CurrentPrincipal find(
            @NotNull Method method,
            @NotNull Class<?> controllerType) {
        CurrentPrincipal declaration = AnnotatedElementUtils.findMergedAnnotation(
                method,
                CurrentPrincipal.class);
        if (declaration != null) {
            return declaration;
        }
        declaration = AnnotatedElementUtils.findMergedAnnotation(
                controllerType,
                CurrentPrincipal.class);
        if (declaration != null) {
            return declaration;
        }
        for (Parameter parameter : method.getParameters()) {
            CurrentPrincipal parameterDeclaration = parameter.getAnnotation(CurrentPrincipal.class);
            if (parameterDeclaration != null) {
                return parameterDeclaration;
            }
        }
        return null;
    }

    /**
     * 取得声明指定的选择器；声明使用默认类型时要求容器中存在唯一通用实现。
     *
     * @param declaration 当前业务身份声明
     * @param applicationContext Spring 容器
     * @return 本次调用使用的选择器
     */
    static @NotNull PrincipalSelection<?> selection(
            @NotNull CurrentPrincipal declaration,
            @NotNull ApplicationContext applicationContext) {
        return declaration.selection() == PrincipalSelection.class
                ? applicationContext.getBean(PrincipalSelection.class)
                : applicationContext.getBean(declaration.selection());
    }
}
