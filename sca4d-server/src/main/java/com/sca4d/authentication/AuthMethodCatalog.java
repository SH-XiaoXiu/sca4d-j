package com.sca4d.authentication;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 收集应用提供的认证方式并建立类型与 Proof 的唯一目录。 */
@Component
public class AuthMethodCatalog {

    private final List<AuthMethod<?>> methods;
    private final Map<AuthMethodType, AuthMethod<?>> byType;

    /**
     * 建立 Spring 容器当前注册的认证方式目录。
     *
     * @param methods 已启用的认证方式；类型不能重复且每项至少允许一种认证目的
     * @throws IllegalArgumentException 集合包含空项时抛出
     * @throws IllegalStateException 类型重复或认证方式没有声明用途时抛出
     */
    public AuthMethodCatalog(@NotNull List<AuthMethod<?>> methods) {
        Map<AuthMethodType, AuthMethod<?>> registered = new LinkedHashMap<>();
        for (AuthMethod<?> method : methods) {
            if (method == null) {
                throw new IllegalArgumentException("认证方式不能为空");
            }
            if (registered.putIfAbsent(method.type(), method) != null) {
                throw new IllegalStateException("重复 AuthMethodType: " + method.type());
            }
            if (method.capabilities().isEmpty()) {
                throw new IllegalStateException("AuthMethod 必须声明至少一种能力: " + method.type());
            }
        }
        this.methods = registered.values().stream()
                .sorted(Comparator.comparing(method -> method.type().value()))
                .toList();
        this.byType = Map.copyOf(registered);
    }

    /** @return 按稳定类型编码排序的认证方式 */
    public @NotNull List<AuthMethod<?>> methods() {
        return methods;
    }

    /**
     * 读取指定类型的认证方式。
     *
     * @throws IllegalStateException 代码请求了未注册的认证方式时抛出
     */
    public @NotNull AuthMethod<?> require(@NotNull AuthMethodType type) {
        AuthMethod<?> method = byType.get(type);
        if (method == null) {
            throw new IllegalStateException("缺少 AuthMethod: " + type);
        }
        return method;
    }

    /**
     * 读取类型和 Proof Java 类型同时匹配的认证方式。
     *
     * @throws IllegalStateException 类型未注册或 Proof 类型不符合方式契约时抛出
     */
    @SuppressWarnings("unchecked")
    public <P> @NotNull AuthMethod<P> require(
            @NotNull AuthMethodType type, @NotNull Class<P> proofClass) {
        AuthMethod<?> method = require(type);
        if (!method.proofClass().equals(proofClass)) {
            throw new IllegalStateException("AuthMethod Proof 类型不匹配: " + type);
        }
        return (AuthMethod<P>) method;
    }
}
