package com.sca4d.auth.engine;

import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.AuthenticationMethodCode;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 索引应用安装的 {@link AuthenticationMethod}，并集中执行 Proof 类型和方法声明检查。
 */
public final class AuthenticationMethods {

    private final Map<AuthenticationMethodCode, AuthenticationMethod<?>> methods;

    /**
     * 创建认证方法目录。
     *
     * @param methods 应用安装的认证方法
     */
    public AuthenticationMethods(@NotNull List<AuthenticationMethod<?>> methods) {
        Map<AuthenticationMethodCode, AuthenticationMethod<?>> indexed = new LinkedHashMap<>();
        for (AuthenticationMethod<?> method : List.copyOf(
                Objects.requireNonNull(methods, "认证方法不能为空"))) {
            AuthenticationMethod<?> duplicate = indexed.putIfAbsent(method.code(), method);
            if (duplicate != null) {
                throw new IllegalStateException("认证方法编码重复: " + method.code().value());
            }
            if (method.factorKinds().isEmpty()) {
                throw new IllegalStateException("认证方法必须声明因素类别: " + method.code().value());
            }
        }
        this.methods = Map.copyOf(indexed);
    }

    /**
     * 调用能够从 Proof 识别 Account 的认证方法。
     *
     * @param submission 包含方法编码、精确 Proof 类型和单次 Proof 的提交材料
     * @return 已识别并验证的 Account、认证记录和非秘密 Credential 引用
     * @throws IllegalArgumentException 方法未安装、Proof 类型不匹配或方法不能识别 Account 时抛出
     * @throws RuntimeException 认证材料、Account 或 Credential 无效时抛出对应认证失败
     */
    public @NotNull VerifiedAuthentication authenticate(
            @NotNull AuthenticationSubmission<?> submission) {
        AuthenticationSubmission<?> input = Objects.requireNonNull(submission, "认证提交不能为空");
        AuthenticationMethod<?> method = method(input.methodCode());
        if (method.proofType() != input.proofType()) {
            throw new IllegalArgumentException("认证材料类型与认证方法不一致");
        }
        if (!method.canIdentifyAccount()) {
            throw new IllegalArgumentException("该认证方法不能识别账号");
        }
        VerifiedAuthentication verification = authenticate(method, null, input.proof());
        if (!method.code().equals(verification.authentication().methodCode())
                || !method.factorKinds().containsAll(verification.authentication().factorKinds())) {
            throw new IllegalStateException("认证方法返回了未声明的方法编码或因素类别");
        }
        return verification;
    }

    /**
     * 调用提交材料所指定的方法验证已经确定的 Account。
     *
     * @param account 调用方已经确定且要求认证结果保持一致的 Account
     * @param submission 包含方法编码、精确 Proof 类型和单次 Proof 的提交材料
     * @return 指定 Account 的认证结果和非秘密 Credential 引用
     * @throws IllegalArgumentException 方法未安装或 Proof 类型不匹配时抛出
     * @throws IllegalStateException 方法对该 Account 不可用或返回其他 Account 时抛出
     * @throws RuntimeException 认证材料、Account 或 Credential 无效时抛出对应认证失败
     */
    public @NotNull VerifiedAuthentication authenticate(
            @NotNull Account account,
            @NotNull AuthenticationSubmission<?> submission) {
        Account checkedAccount = Objects.requireNonNull(account, "认证 Account 不能为空");
        AuthenticationSubmission<?> input = Objects.requireNonNull(submission, "认证提交不能为空");
        AuthenticationMethod<?> method = method(input.methodCode());
        if (method.proofType() != input.proofType()) {
            throw new IllegalArgumentException("认证材料类型与认证方法不一致");
        }
        if (!method.available(checkedAccount)) {
            throw new IllegalStateException("认证方法当前不可用");
        }
        VerifiedAuthentication verification = authenticate(method, checkedAccount, input.proof());
        if (checkedAccount.accountId() != verification.account().accountId()) {
            throw new IllegalStateException("认证方法返回了其他 Account");
        }
        if (!method.code().equals(verification.authentication().methodCode())
                || !method.factorKinds().containsAll(verification.authentication().factorKinds())) {
            throw new IllegalStateException("认证方法返回了未声明的方法编码或因素类别");
        }
        return verification;
    }

    /**
     * 复核已保存的非秘密证据引用。
     *
     * @param account      证据所属账号
     * @param verification 已保存验证结果
     */
    public void verifyCurrent(
            @NotNull Account account,
            @NotNull VerifiedAuthentication verification) {
        Account checkedAccount = Objects.requireNonNull(account, "账号不能为空");
        VerifiedAuthentication checkedVerification = Objects.requireNonNull(verification, "认证结果不能为空");
        if (checkedAccount.accountId() != checkedVerification.account().accountId()) {
            throw new IllegalArgumentException("认证结果不属于指定 Account");
        }
        AuthenticationMethod<?> method = methods.get(checkedVerification.authentication().methodCode());
        if (method == null || !method.available(account)) {
            throw new IllegalStateException("认证证据对应的方法已经不可用");
        }
        method.verifyCurrent(checkedAccount, checkedVerification);
    }

    /**
     * 判断指定 Account 当前能否使用已安装的认证方法。
     *
     * @param account 已确定的 Account
     * @param methodCode 认证方法编码
     * @return 方法已经安装且当前可用时为 {@code true}
     */
    public boolean available(
            @NotNull Account account,
            @NotNull AuthenticationMethodCode methodCode) {
        AuthenticationMethod<?> method = methods.get(Objects.requireNonNull(methodCode, "认证方法编码不能为空"));
        return method != null && method.available(Objects.requireNonNull(account, "账号不能为空"));
    }

    /**
     * 返回策略允许且账号当前可用的方法。
     *
     * @param account        已确定账号
     * @param allowedMethods 具体应用策略允许的方法
     * @return 按编码稳定排序的已安装方法
     */
    public @NotNull @Unmodifiable List<AuthenticationMethodCode> available(
            @NotNull Account account,
            @NotNull Set<AuthenticationMethodCode> allowedMethods) {
        Account checkedAccount = Objects.requireNonNull(account, "账号不能为空");
        Set<AuthenticationMethodCode> allowed = Set.copyOf(
                Objects.requireNonNull(allowedMethods, "策略允许的方法不能为空"));
        List<AuthenticationMethodCode> result = new ArrayList<>();
        for (AuthenticationMethod<?> method : methods.values()) {
            if (allowed.contains(method.code()) && method.available(checkedAccount)) {
                result.add(method.code());
            }
        }
        result.sort(Comparator.comparing(AuthenticationMethodCode::value));
        return List.copyOf(result);
    }

    /**
     * 按具体类型读取应用安装的认证方法，供组合根调用该方法特有的 Credential 或 ceremony 操作。
     *
     * @param methodType 认证方法公开类型
     * @param <M> 认证方法类型
     * @return 唯一匹配的已安装实例
     * @throws IllegalArgumentException 没有安装该类型时抛出
     * @throws IllegalStateException 多个已安装实例匹配该类型时抛出
     */
    public @NotNull <M extends AuthenticationMethod<?>> M method(@NotNull Class<M> methodType) {
        Class<M> checkedType = Objects.requireNonNull(methodType, "认证方法类型不能为空");
        List<AuthenticationMethod<?>> matched = methods.values()
                .stream()
                .filter(checkedType::isInstance)
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("认证方法未安装: " + checkedType.getName());
        }
        if (matched.size() > 1) {
            throw new IllegalStateException("认证方法类型匹配多个实例: " + checkedType.getName());
        }
        return checkedType.cast(matched.getFirst());
    }

    /**
     * 调用每个已安装方法永久删除指定 Account 的 Credential 和认证状态。
     *
     * <p>各方法删除操作必须幂等；任一方法失败时异常直接传播，Account 删除流程可以再次调用以继续清理。
     *
     * @param accountId 要清理的 Account 标识
     */
    public void eraseAccount(long accountId) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account 标识必须为正数");
        }
        for (AuthenticationMethod<?> method : methods.values()) {
            method.eraseAccount(accountId);
        }
    }

    /**
     * 返回指定编码对应的已安装认证方法。
     */
    private AuthenticationMethod<?> method(AuthenticationMethodCode code) {
        AuthenticationMethod<?> method = methods.get(Objects.requireNonNull(code, "认证方法编码不能为空"));
        if (method == null) {
            throw new IllegalArgumentException("认证方法不存在");
        }
        return method;
    }

    /**
     * 在保留认证材料类型约束的情况下调用认证方法。
     */
    private static <P> VerifiedAuthentication authenticate(
            AuthenticationMethod<P> method,
            @Nullable Account account,
            Object proof) {
        return method.authenticate(account, method.proofType().cast(proof));
    }
}
