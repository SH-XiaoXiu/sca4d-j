package com.sca4d.core.spring.config;

import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.annotation.RequirePermissions;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.name.code.AttributeAxis;
import com.sca4d.core.api.permission.OperationAxes;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.permission.SessionOperationPermissionPolicy;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.permission.CurrentResourceScope;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import java.util.List;

/** 注册操作目录、权限策略和权限调用参与者。 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CorePermissionConfiguration {

    /** @return 当前调用资源范围入口 */
    @Bean
    CurrentResourceScope currentResourceScope() {
        return new CurrentResourceScope();
    }

    /** @return 固定操作使用 Session 静态资源判断的内置策略 */
    @Bean
    SessionOperationPermissionPolicy sessionOperationPermissionPolicy() {
        return new SessionOperationPermissionPolicy();
    }

    /** @param policies Spring 容器中的业务权限策略 @return 唯一策略认领的权限评估器 */
    @Bean
    PermissionAssessment permissionAssessment(List<PermissionPolicy> policies) {
        return new PermissionAssessment(policies);
    }

    /** @return {@link OperationAxes#INTENT}；每个 Operation 叶子必须声明一个意图 */
    @Bean
    AttributeAxis intentAxis() {
        return OperationAxes.INTENT;
    }

    /** @return {@link OperationAxes#NATURE}；每个 Operation 叶子必须声明一个性质 */
    @Bean
    AttributeAxis natureAxis() {
        return OperationAxes.NATURE;
    }

    /** @return {@link OperationAxes#RISK}；Operation 叶子按需声明风险 */
    @Bean
    AttributeAxis riskAxis() {
        return OperationAxes.RISK;
    }

    /** @return {@link OperationAxes#VISIBILITY}；Operation 叶子按需声明是否列入授权编辑器目录 */
    @Bean
    AttributeAxis visibilityAxis() {
        return OperationAxes.VISIBILITY;
    }

    /**
     * @param modules 各业务模块声明的 Operation 目录
     * @param axes    容器中全部 {@link AttributeAxis} Bean，包含 Core 登记的标准轴和应用自己登记的轴
     * @return 完成结构和属性校验、排序的 Operation 目录
     */
    @Bean
    OperationCatalog operationCatalog(
            @NotNull List<@NotNull OperationModule> modules,
            @NotNull ObjectProvider<@NotNull AttributeAxis> axes) {
        return new OperationCatalog(modules, axes.orderedStream().toList());
    }

    /** @param permissionAssessment 权限策略入口 @param resolverLookup 调用值解析器目录 @return 权限调用前评估器 */
    @Bean
    PermissionCallInterceptor permissionCallInterceptor(
            PermissionAssessment permissionAssessment,
            CallValueResolverLookup resolverLookup,
            SPICallValueResolver expressionResolver) {
        return new PermissionCallInterceptor(permissionAssessment, resolverLookup, expressionResolver);
    }

    /** 注册 Core 自己定义的权限注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<RequirePermission> permissionCallBinding() {
        return () -> RequirePermission.class;
    }

    /** 注册 repeatable 权限注解的容器。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<RequirePermissions> permissionsCallBinding() {
        return () -> RequirePermissions.class;
    }
}
