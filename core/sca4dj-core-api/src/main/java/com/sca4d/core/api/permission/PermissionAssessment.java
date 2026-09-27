package com.sca4d.core.api.permission;

import com.sca4d.core.api.call.Call;
import org.jetbrains.annotations.CheckReturnValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 按唯一策略认领规则执行权限判断（Permission Assessment）。
 *
 * <p>没有策略认领或多个策略同时认领都属于配置错误，不能默认允许，也不能按未定义的先后顺序选择策略。
 */
public class PermissionAssessment {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionAssessment.class);

    private final List<PermissionPolicy> policies;

    /**
     * 创建权限评估器。
     *
     * @param policies 可认领不同资源的策略集合
     */
    public PermissionAssessment(List<PermissionPolicy> policies) {
        this.policies = List.copyOf(Objects.requireNonNull(policies, "权限策略不能为空"));
    }

    /**
     * 执行一次权限判断。
     *
     * @param call 已完成 Principal 选择的调用
     * @param permission 待判断权限
     * @return 唯一策略作出的允许、拒绝或携带资源范围的允许判定
     * @throws IllegalStateException 没有策略或多个策略认领资源时抛出
     */
    @CheckReturnValue
    public PermissionDecision assess(Call call, Permission permission) {
        Objects.requireNonNull(call, "调用不能为空");
        Objects.requireNonNull(permission, "待判断权限不能为空");
        if (call.principal().isEmpty()) {
            throw new IllegalStateException("权限判断前必须完成业务主体选择");
        }
        // 资源必须由唯一策略处理，避免同一 Permission 得到相互冲突的判断结果。
        List<PermissionPolicy> claimed = policies.stream()
                .filter(policy -> policy.supports(permission.resource()))
                .toList();
        if (claimed.isEmpty()) {
            throw new IllegalStateException("没有权限策略认领资源: " + permission.resource().type());
        }
        if (claimed.size() > 1) {
            throw new IllegalStateException("多个权限策略同时认领资源: " + permission.resource().type());
        }
        // 策略返回资源范围时必须与 Permission 的资源类型一致，防止范围被用于其他资源。
        PermissionDecision decision = Objects.requireNonNull(
                claimed.getFirst().decide(call, permission),
                "权限策略不能返回空判定");
        decision.scope().ifPresent(scope -> {
            if (!permission.resource().type().equals(scope.resourceType())) {
                throw new IllegalStateException(
                        "权限策略返回的资源范围类型不一致: " + scope.resourceType() + " != "
                                + permission.resource().type());
            }
        });
        LOG.trace(
                "权限判断完成 | callId={} principalId={} permission={} allowed={} scopeType={}",
                call.callId(),
                call.principal().orElseThrow().principalId(),
                permission,
                decision.isAllowed(),
                decision.scope().map(scope -> scope.getClass().getName()).orElse(null));
        return decision;
    }
}
