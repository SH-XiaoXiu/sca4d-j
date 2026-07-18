package com.sca4d.common.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.AccessCriteria;
import com.sca4d.common.access.Policy;
import com.sca4d.common.access.ResourceAccessPolicy;
import com.sca4d.common.access.Rules;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.web.PrincipalSelectionInputResolver;
import com.sca4d.common.auth.web.PrincipalSelectionInvoker;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 验证访问切面选择 Principal、读取资源主键并调用对应 Policy。 */
class AccessAspectTest {

    @Test
    void shouldAuthorizeBeforeAuditCapturesResourceTarget() {
        assertThat(AccessAspect.class.getAnnotation(Order.class).value()).isEqualTo(250);
    }

    @Test
    void shouldOnlyInterceptProjectOwnedControllersAndMessageHandlers() throws Exception {
        Around around = AccessAspect.class
                .getMethod("authorize", ProceedingJoinPoint.class)
                .getAnnotation(Around.class);

        assertThat(around.value())
                .startsWith("within(com.sca4d..*)")
                .contains("RestController", "ResponseBody", "MessageMapping");
    }

    private final FakePrincipalSelection selection = new FakePrincipalSelection();
    private final FakePermissionChecker permissionChecker = new FakePermissionChecker();
    private final FakeAccessPolicy resourceAccessPolicy = new FakeAccessPolicy();
    private final TestSession authorizedSession = TestSession.withAuthorization(
            new Subject(1L, "Subject 1"),
            List.of(Principal.of(10L, PrincipalType.DEFAULT, 1L, null)),
            Map.of(10L, new SessionPermission(10L, null, null)));
    private final AccessAspect aspect = new AccessAspect(
            selection,
            new PrincipalSelectionInputResolver(),
            new PrincipalSelectionInvoker(new ObjectMapper()),
            new StaticListableBeanFactory(Map.of("resourceAccessPolicy", resourceAccessPolicy)),
            permissionChecker);

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldSelectPrincipalAndApplyPermissionAndResourceAccess() throws Throwable {
        CurrentRequest request = CurrentRequest.authenticated(authorizedSession);
        CurrentRequestHolder.set(request);
        DemoController controller = new DemoController();

        Object value = aspect.authorize(joinPoint(
                controller,
                "secured",
                new Object[] {"42", 7L, "scene-a"},
                String.class,
                Long.class,
                String.class));

        assertThat(value).isEqualTo("ok");
        assertThat(selection.allowedTypes).containsExactly(PrincipalType.DEFAULT);
        assertThat(selection.principalId).isEqualTo(42L);
        assertThat(selection.tenantId).isEqualTo(7L);
        assertThat(selection.selectionKey).isEqualTo("scene-a");
        assertThat(request.requireSessionPermission().getPrincipalId()).isEqualTo(10L);
        assertThat(permissionChecker.events)
                .containsExactly("permission:demo:read", "role:admin");
        assertThat(resourceAccessPolicy.events).containsExactly("WRITE:42");
        DemoCriteria criteria = request.requireAccess(DemoCriteria.class);
        assertThat(criteria.system()).isFalse();
        assertThat(criteria.action()).isEqualTo(AccessAction.WRITE);
        assertThat(criteria.resourceId()).isEqualTo("42");
    }

    @Test
    void shouldResolvePrincipalFromBoundBody() throws Throwable {
        CurrentRequest request = CurrentRequest.authenticated(authorizedSession);
        CurrentRequestHolder.set(request);
        DemoBody body = new DemoBody(9L);

        aspect.authorize(joinPoint(
                new DemoController(), "body", new Object[] {body}, DemoBody.class));

        assertThat(selection.tenantId).isEqualTo(9L);
        assertThat(selection.parameters).containsEntry("body", body);
        assertThat(request.requireSessionPermission().getPrincipalId()).isEqualTo(10L);
    }

    @Test
    void shouldReplaceAllPrincipalInjectionArgumentsAfterBodySelection() throws Throwable {
        CurrentRequestHolder.set(CurrentRequest.authenticated(authorizedSession));
        DemoBody body = new DemoBody(9L);

        Object value = aspect.authorize(joinPoint(
                new DemoController(),
                "bodyWithPrincipal",
                new Object[] {body, null, null, null},
                DemoBody.class,
                Principal.class,
                Long.class,
                PrincipalType.class));

        assertThat(value).isEqualTo("10:DEFAULT:9");
    }

    @Test
    void shouldSkipLoginAndAccessChecksForAnonymousMethod() throws Throwable {
        Object value = aspect.authorize(
                joinPoint(new DemoController(), "anonymous", new Object[0]));

        assertThat(value).isEqualTo("ok");
        assertThat(permissionChecker.events).isEmpty();
        assertThat(resourceAccessPolicy.events).isEmpty();
    }

    @Test
    void shouldRejectAnonymousAndProtectedDeclarationsAtSameLevel() throws Exception {
        ProceedingJoinPoint joinPoint = joinPoint(new DemoController(), "invalidAnonymous", new Object[0]);

        assertThatThrownBy(() -> aspect.authorize(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@AnonymousAccess");
    }

    @Test
    void shouldRejectEmptyPermissionDeclaration() throws Exception {
        ProceedingJoinPoint joinPoint = joinPoint(new DemoController(), "emptyPermission", new Object[0]);

        assertThatThrownBy(() -> aspect.authorize(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少声明一个权限或角色");
    }

    @Test
    void shouldRejectBlankAndWildcardPermissionCodes() throws Exception {
        assertThatThrownBy(() -> aspect.authorize(
                joinPoint(new DemoController(), "blankPermission", new Object[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法权限编码");
        assertThatThrownBy(() -> aspect.authorize(
                joinPoint(new DemoController(), "wildcardRole", new Object[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法角色编码");
    }

    @Test
    void shouldBypassRbacForSystemButStillResolveResourceAccess() throws Throwable {
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(Principal.of(1L, PrincipalType.SYSTEM, 1L, null));
        CurrentRequestHolder.set(request);

        Object value = aspect.authorize(
                joinPoint(new DemoController(), "system", new Object[] {"42"}, String.class));

        assertThat(value).isEqualTo("ok");
        assertThat(permissionChecker.events).isEmpty();
        assertThat(resourceAccessPolicy.events).containsExactly("MANAGE:42");
        DemoCriteria criteria = request.requireAccess(DemoCriteria.class);
        assertThat(criteria.system()).isTrue();
        assertThat(criteria.action()).isEqualTo(AccessAction.MANAGE);
    }

    /** 声明 Principal 选择、权限和资源访问注解组合的测试 Controller。 */
    @RestController
    static class DemoController {

        @AuthPrincipal(value = PrincipalType.DEFAULT, principalId = "#id", tenantId = "#tenantId", selectionKey = "#selectionKey")
        @RequirePermission(permissions = "demo:read", roles = "admin")
        @Access(policy = FakeAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#id")
        String secured(String id, Long tenantId, String selectionKey) {
            return "ok";
        }

        @AuthPrincipal(tenantId = "#body.tenantId")
        String body(@RequestBody DemoBody body) {
            return "ok";
        }

        @AuthPrincipal(tenantId = "#body.tenantId")
        String bodyWithPrincipal(
                @RequestBody DemoBody body,
                @AuthPrincipal Principal principal,
                @AuthPrincipal Long principalId,
                @AuthPrincipal PrincipalType principalType) {
            return principalId + ":" + principalType + ":" + principal.getTenantId();
        }

        @AnonymousAccess
        String anonymous() {
            return "ok";
        }

        @AnonymousAccess
        @RequirePermission("demo:read")
        String invalidAnonymous() {
            return "ok";
        }

        @RequirePermission
        String emptyPermission() {
            return "ok";
        }

        @RequirePermission(" ")
        String blankPermission() {
            return "ok";
        }

        @RequirePermission(roles = "*")
        String wildcardRole() {
            return "ok";
        }

        @RequirePermission("system:execute")
        @Access(policy = FakeAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#id")
        String system(String id) {
            return "ok";
        }
    }

    /** 供 body 选择测试使用的最小请求体。 */
    private static final class DemoBody {

        private final Long tenantId;

        /**
         * 创建携带租户选择输入的最小请求体。
         *
         * @param tenantId SpEL 从 body 读取的租户主键
         */
        private DemoBody(@NotNull Long tenantId) {
            this.tenantId = tenantId;
        }

        public @NotNull Long getTenantId() {
            return tenantId;
        }
    }

    /** 记录业务模块 Policy 输入和 SYSTEM 分支的测试条件。 */
    private static final class DemoCriteria implements AccessCriteria {

        private final boolean system;
        private final AccessAction action;
        private final String resourceId;

        /**
         * 固化业务模块 Policy 解析结果，供请求态断言使用。
         *
         * @param system 是否由 SYSTEM Principal 解析
         * @param action 类型安全资源访问等级
         * @param resourceId 可选单资源标识
         */
        private DemoCriteria(
                boolean system, @NotNull AccessAction action, @Nullable String resourceId) {
            this.system = system;
            this.action = action;
            this.resourceId = resourceId;
        }

        /** @return 是否为 SYSTEM Principal 访问 */
        private boolean system() {
            return system;
        }

        /** @return Policy 解析后的类型安全访问等级 */
        private @NotNull AccessAction action() {
            return action;
        }

        /** @return 单资源标识；列表访问时为 {@code null} */
        private @Nullable String resourceId() {
            return resourceId;
        }
    }

    /** 记录 Policy 输入并返回固定访问等级的内存实现。 */
    static final class FakeAccessPolicy implements ResourceAccessPolicy<DemoResource, DemoCriteria> {

        private static final Policy<DemoResource> POLICY = Rules.policy(
                "access-aspect-test",
                DemoResource.class,
                "/cedar/access-aspect-schema.cedar",
                List.of(Rules.any(
                        Rules.<DemoResource>principal(PrincipalType.DEFAULT),
                        Rules.<DemoResource>principal(PrincipalType.SYSTEM))),
                List.of());

        private final List<String> events = new ArrayList<>();

        @Override
        public @NotNull Policy<DemoResource> policy() {
            return POLICY;
        }

        @Override
        public @NotNull Class<DemoCriteria> criteriaType() {
            return DemoCriteria.class;
        }

        @Override
        public @NotNull DemoResource resource(
                @NotNull CurrentRequest request,
                @NotNull AccessAction action,
                @Nullable Object resourceId) {
            String resolvedResourceId = resourceId == null ? null : resourceId.toString();
            return new DemoResource(resolvedResourceId);
        }

        @Override
        public @NotNull DemoCriteria criteria(
                @NotNull CurrentRequest request,
                @NotNull AccessAction action,
                @NotNull DemoResource resource) {
            String resolvedResourceId = resource.resourceId();
            events.add(action.value() + ":" + resolvedResourceId);
            return new DemoCriteria(request.system(), action, resolvedResourceId);
        }
    }

    /** 供测试 Policy 接收 SpEL 解析结果的资源。 */
    private static final class DemoResource {

        private final String resourceId;

        /** @param resourceId Access 注解解析出的可选资源标识 */
        private DemoResource(@Nullable String resourceId) {
            this.resourceId = resourceId;
        }

        /** @return Access 注解解析出的可选资源标识 */
        private @Nullable String resourceId() {
            return resourceId;
        }
    }

    /** 按测试设置允许或拒绝角色和权限检查的实现。 */
    private static final class FakePermissionChecker implements PermissionChecker {

        private final List<String> events = new ArrayList<>();

        @Override
        public @NotNull SessionPermission require(@NotNull CurrentRequest request) {
            SessionPermission permission = new SessionPermission(request.requirePrincipal().requirePrincipalId(), null, null);
            request.setSessionPermission(permission);
            return permission;
        }

        @Override
        public void requirePermission(
                @NotNull CurrentRequest request, @NotNull String permissionCode) {
            events.add("permission:" + permissionCode);
        }

        @Override
        public void requireRole(@NotNull CurrentRequest request, @NotNull String roleCode) {
            events.add("role:" + roleCode);
        }
    }

    /** 返回固定 Principal 并记录选择参数的实现。 */
    private static final class FakePrincipalSelection
            implements
                PrincipalSelection<Map<String, Object>> {

        private PrincipalType[] allowedTypes = new PrincipalType[0];
        private Long principalId;
        private Long tenantId;
        private String selectionKey;
        private Map<String, Object> parameters = Map.of();

        @Override
        public @NotNull Class<Map<String, Object>> parameterType() {
            return PrincipalSelection.mapParameterType();
        }

        @Override
        public @NotNull Principal select(
                @NotNull CurrentRequest request,
                @NotNull PrincipalType[] allowedTypes,
                @Nullable Long principalId,
                @Nullable Long tenantId,
                @Nullable String selectionKey,
                @NotNull Map<String, Object> parameters) {
            this.allowedTypes = allowedTypes;
            this.principalId = principalId;
            this.tenantId = tenantId;
            this.selectionKey = selectionKey;
            this.parameters = parameters;
            return Principal.of(10L, PrincipalType.DEFAULT, 1L, tenantId);
        }
    }

    /**
     * 创建使用真实反射方法并可替换参数执行的 JoinPoint 测试替身。
     *
     * @param target Controller 测试对象
     * @param methodName 待拦截方法名
     * @param args 当前绑定参数，可包含待注入的 {@code null} 槽位
     * @param parameterTypes 方法声明参数类型
     * @return 可供 AccessAspect 执行的 JoinPoint
     * @throws Exception 方法契约发生漂移时抛出
     */
    private @NotNull ProceedingJoinPoint joinPoint(
            @NotNull Object target,
            @NotNull String methodName,
            @NotNull Object[] args,
            @NotNull Class<?>... parameterTypes)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        return new DemoJoinPoint(target, method, args);
    }

    /** 提供固定 MethodSignature 和返回值的 ProceedingJoinPoint。 */
    private static final class DemoJoinPoint implements ProceedingJoinPoint {

        private final Object target;
        private final Method method;
        private final Object[] args;

        /**
         * 创建直接反射调用目标方法的 JoinPoint 测试替身。
         *
         * @param target Controller 测试对象
         * @param method 真实处理方法
         * @param args 当前方法参数
         */
        private DemoJoinPoint(@NotNull Object target, @NotNull Method method, @NotNull Object[] args) {
            this.target = target;
            this.method = method;
            this.args = args;
        }

        @Override
        public @Nullable Object proceed() throws Throwable {
            return method.invoke(target, args);
        }

        @Override
        public @Nullable Object proceed(@NotNull Object[] args) throws Throwable {
            return method.invoke(target, args);
        }

        @Override
        public void set$AroundClosure(@Nullable AroundClosure aroundClosure) {
        }

        @Override
        public @NotNull Object getThis() {
            return target;
        }

        @Override
        public @NotNull Object getTarget() {
            return target;
        }

        @Override
        public @NotNull Object[] getArgs() {
            return args;
        }

        @Override
        public @NotNull Signature getSignature() {
            return new DemoMethodSignature(method);
        }

        @Override
        public @NotNull String toShortString() {
            return method.getName();
        }

        @Override
        public @NotNull String toLongString() {
            return method.toString();
        }

        @Override
        public @Nullable SourceLocation getSourceLocation() {
            return null;
        }

        @Override
        public @NotNull String getKind() {
            return "method-execution";
        }

        @Override
        public @Nullable StaticPart getStaticPart() {
            return null;
        }
    }

    /** 为切面单元测试提供反射 MethodSignature。 */
    private static final class DemoMethodSignature implements MethodSignature {

        private final Method method;

        /**
         * 创建代理指定反射方法元数据的 MethodSignature。
         *
         * @param method 真实 Controller 方法
         */
        private DemoMethodSignature(@NotNull Method method) {
            this.method = method;
        }

        @Override
        public @NotNull Method getMethod() {
            return method;
        }

        @Override
        public @NotNull Class<?> getReturnType() {
            return method.getReturnType();
        }

        @Override
        public @NotNull Class<?>[] getParameterTypes() {
            return method.getParameterTypes();
        }

        @Override
        public @Nullable String[] getParameterNames() {
            return null;
        }

        @Override
        public @NotNull Class<?>[] getExceptionTypes() {
            return method.getExceptionTypes();
        }

        @Override
        public @NotNull String toShortString() {
            return method.getName();
        }

        @Override
        public @NotNull String toLongString() {
            return method.toString();
        }

        @Override
        public @NotNull String getName() {
            return method.getName();
        }

        @Override
        public int getModifiers() {
            return method.getModifiers();
        }

        @Override
        public @NotNull Class<?> getDeclaringType() {
            return method.getDeclaringClass();
        }

        @Override
        public @NotNull String getDeclaringTypeName() {
            return method.getDeclaringClass().getName();
        }
    }
}
