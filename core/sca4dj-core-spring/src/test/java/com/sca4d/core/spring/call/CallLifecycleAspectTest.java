package com.sca4d.core.spring.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.spring.web.RequestVersionConditions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Instant;

/** 验证 Core 注解切面读取类级声明、重复权限声明和具体实现方法。 */
class CallLifecycleAspectTest {

    @AfterEach
    void clearCall() {
        CallHolder.clear();
    }

    @Test
    @DisplayName("Intercepts Class Level Rate Limit And Uses The Most Specific Target Method")
    void interceptsClassLevelRateLimitAndUsesTheMostSpecificTargetMethod() throws Exception {
        CallLifecycle lifecycle = mock(CallLifecycle.class);
        doReturn("checked")
                .when(lifecycle)
                .execute(any(), any());
        AspectJProxyFactory factory = new AspectJProxyFactory(new ClassLimitedService());
        factory.setProxyTargetClass(false);
        factory.addAspect(new CallLifecycleAspect(lifecycle, new RequestVersionConditions()));
        LimitedService service = factory.getProxy();
        Call call = Call.system("class-rate-limit", Instant.EPOCH);
        CallHolder.set(call);

        String result = service.invoke("value");

        ArgumentCaptor<CallExecution> execution = ArgumentCaptor.forClass(CallExecution.class);
        verify(lifecycle).execute(execution.capture(), any());
        assertThat(result).isEqualTo("checked");
        assertThat(execution.getValue().call()).isSameAs(call);
        assertThat(execution.getValue().declaringType()).isEqualTo(ClassLimitedService.class);
        assertThat(execution.getValue().method().getDeclaringClass()).isEqualTo(ClassLimitedService.class);
        assertThat(execution.getValue().method().getName()).isEqualTo("invoke");
        assertThat(execution.getValue().method().getParameterTypes()).containsExactly(String.class);
        assertThat(execution.getValue().resultType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Intercepts Repeated Method Permissions")
    void interceptsRepeatedMethodPermissions() throws Exception {
        CallLifecycle lifecycle = mock(CallLifecycle.class);
        doReturn("checked")
                .when(lifecycle)
                .execute(any(), any());
        AspectJProxyFactory factory = new AspectJProxyFactory(new MethodPermissionService());
        factory.setProxyTargetClass(false);
        factory.addAspect(new CallLifecycleAspect(lifecycle, new RequestVersionConditions()));
        PermissionService service = factory.getProxy();
        CallHolder.set(Call.system("method-permissions", Instant.EPOCH));

        String result = service.invoke("value");

        ArgumentCaptor<CallExecution> execution = ArgumentCaptor.forClass(CallExecution.class);
        verify(lifecycle).execute(execution.capture(), any());
        assertThat(result).isEqualTo("checked");
        assertThat(execution.getValue().requiredPermissions())
                .extracting(RequirePermission::operation)
                .containsExactly("project:read", "project:scope:read");
    }

    @Test
    @DisplayName("Intercepts Repeated Class Permissions")
    void interceptsRepeatedClassPermissions() throws Exception {
        CallLifecycle lifecycle = mock(CallLifecycle.class);
        doReturn("checked")
                .when(lifecycle)
                .execute(any(), any());
        AspectJProxyFactory factory = new AspectJProxyFactory(new ClassPermissionService());
        factory.setProxyTargetClass(false);
        factory.addAspect(new CallLifecycleAspect(lifecycle, new RequestVersionConditions()));
        PermissionService service = factory.getProxy();
        CallHolder.set(Call.system("class-permissions", Instant.EPOCH));

        String result = service.invoke("value");

        ArgumentCaptor<CallExecution> execution = ArgumentCaptor.forClass(CallExecution.class);
        verify(lifecycle).execute(execution.capture(), any());
        assertThat(result).isEqualTo("checked");
        assertThat(execution.getValue().requiredPermissions())
                .extracting(RequirePermission::operation)
                .containsExactly("project:manage", "project:scope:manage");
    }

    /** 声明 JDK 代理暴露的测试调用接口。 */
    private interface LimitedService {

        /** @param value 测试输入 @return 原样返回输入 */
        String invoke(String value);
    }

    /** 声明重复权限切面测试使用的调用接口。 */
    private interface PermissionService {

        /** @param value 测试输入 @return 原样返回输入 */
        String invoke(String value);
    }

    /** 在具体目标类型上声明类级限流。 */
    @RateLimited(permits = 5, windowSeconds = 60)
    private static final class ClassLimitedService implements LimitedService {

        @Override
        public String invoke(String value) {
            return value;
        }
    }

    /** 在具体方法上声明两个权限要求。 */
    private static final class MethodPermissionService implements PermissionService {

        @Override
        @RequirePermission(operation = "project:read")
        @RequirePermission(operation = "project:scope:read")
        public String invoke(String value) {
            return value;
        }
    }

    /** 在具体目标类型上声明两个权限要求。 */
    @RequirePermission(operation = "project:manage")
    @RequirePermission(operation = "project:scope:manage")
    private static final class ClassPermissionService implements PermissionService {

        @Override
        public String invoke(String value) {
            return value;
        }
    }
}
