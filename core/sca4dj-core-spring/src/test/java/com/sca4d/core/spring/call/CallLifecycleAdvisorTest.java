package com.sca4d.core.spring.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sca4d.core.api.annotation.RateLimited;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.annotation.RequirePermissions;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证 Core 注解切面读取类级声明、重复权限声明和具体实现方法。 */
class CallLifecycleAdvisorTest {

    @Test
    @DisplayName("应用自行注册注解，统一 Advisor 匹配接口声明并把声明交给执行器")
    void registersApplicationOwnedAnnotation() throws Exception {
        CallLifecycle lifecycle = mock(CallLifecycle.class);
        doReturn("checked").when(lifecycle).execute(any(), any());
        AtomicInteger attachments = new AtomicInteger();
        CallLifecycleAdvisor.Declaration<Risk> registration = new CallLifecycleAdvisor.Declaration<>() {
            @Override
            public @NonNull Class<Risk> annotationType() {
                return Risk.class;
            }

            @Override
            public void attach(@NonNull CallExecution execution, @NonNull Risk annotation) {
                attachments.incrementAndGet();
            }
        };
        AspectJProxyFactory factory = new AspectJProxyFactory(new RiskService());
        factory.setProxyTargetClass(false);
        factory.addAdvisor(new CallLifecycleAdvisor(lifecycle, List.of(registration), Clock.systemUTC()));
        RiskApi service = factory.getProxy();
        assertThat(service.update()).isEqualTo("checked");
        ArgumentCaptor<CallExecution> execution = ArgumentCaptor.forClass(CallExecution.class);
        verify(lifecycle).execute(execution.capture(), any());
        assertThat(execution.getValue().declaration(Risk.class)).isPresent();
        assertThat(attachments.get()).isEqualTo(1);
        assertThat(CallHolder.currentOrNull()).isNull();
        assertThrows(
                IllegalArgumentException.class,
                () -> new CallLifecycleAdvisor(
                        lifecycle,
                        List.of(registration, registration),
                        Clock.systemUTC()));
    }

    @Test
    @DisplayName("未注册的应用注解不触发调用生命周期")
    void leavesUnregisteredAnnotationsOutsideTheAdvisor() {
        CallLifecycle lifecycle = mock(CallLifecycle.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new RiskService());
        factory.addAdvisor(new CallLifecycleAdvisor(lifecycle, List.of(), Clock.systemUTC()));
        RiskApi service = factory.getProxy();
        assertThat(service.update()).isEqualTo("actual");
        verifyNoInteractions(lifecycle);
    }

    @Test
    @DisplayName("声明了调用功能却未注册处理者时在业务执行前拒绝")
    void rejectsMissingFeatureRegistration() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new MethodPermissionService());
        factory.setProxyTargetClass(false);
        factory.addAdvisor(new CallLifecycleAdvisor(mock(CallLifecycle.class), List.of(), Clock.systemUTC()));
        PermissionService service = factory.getProxy();
        assertThrows(IllegalArgumentException.class, () -> service.invoke("value"));
    }

    /** 模拟由具体应用维护的注解，Core 无需引用它。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    private @interface Risk {
    }

    /** 注解定义在 JDK 代理接口上。 */
    private interface RiskApi {
        @Risk
        String update();
    }

    /** 业务实现不复制接口注解。 */
    private static class RiskService implements RiskApi {
        @Override
        public String update() {
            return "actual";
        }
    }

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
        factory.addAdvisor(
                new CallLifecycleAdvisor(
                        lifecycle,
                        List.<CallLifecycleAdvisor.Declaration<RateLimited>>of(() -> RateLimited.class),
                        Clock.systemUTC()));
        LimitedService service = factory.getProxy();
        Call call = Call.system("class-rate-limit", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
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
        factory.addAdvisor(
                new CallLifecycleAdvisor(
                        lifecycle,
                        List.of(
                                (CallLifecycleAdvisor.Declaration<RequirePermission>) () -> RequirePermission.class,
                                (CallLifecycleAdvisor.Declaration<RequirePermissions>) () -> RequirePermissions.class),
                        Clock.systemUTC()));
        PermissionService service = factory.getProxy();
        CallHolder.set(
                Call.system("method-permissions", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))));

        String result = service.invoke("value");

        ArgumentCaptor<CallExecution> execution = ArgumentCaptor.forClass(CallExecution.class);
        verify(lifecycle).execute(execution.capture(), any());
        assertThat(result).isEqualTo("checked");
        assertThat(execution.getValue().declarations(RequirePermission.class))
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
        factory.addAdvisor(
                new CallLifecycleAdvisor(
                        lifecycle,
                        List.of(
                                (CallLifecycleAdvisor.Declaration<RequirePermission>) () -> RequirePermission.class,
                                (CallLifecycleAdvisor.Declaration<RequirePermissions>) () -> RequirePermissions.class),
                        Clock.systemUTC()));
        PermissionService service = factory.getProxy();
        CallHolder.set(
                Call.system("class-permissions", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))));

        String result = service.invoke("value");

        ArgumentCaptor<CallExecution> execution = ArgumentCaptor.forClass(CallExecution.class);
        verify(lifecycle).execute(execution.capture(), any());
        assertThat(result).isEqualTo("checked");
        assertThat(execution.getValue().declarations(RequirePermission.class))
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
    private static class ClassLimitedService implements LimitedService {

        @Override
        public String invoke(String value) {
            return value;
        }
    }

    /** 在具体方法上声明两个权限要求。 */
    private static class MethodPermissionService implements PermissionService {

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
    private static class ClassPermissionService implements PermissionService {

        @Override
        public String invoke(String value) {
            return value;
        }
    }
}
