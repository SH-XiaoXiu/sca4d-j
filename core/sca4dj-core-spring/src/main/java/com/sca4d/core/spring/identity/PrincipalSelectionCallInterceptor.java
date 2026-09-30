package com.sca4d.core.spring.identity;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 在统一调用生命周期内执行 CurrentPrincipal 声明的主体选择。
 */
public class PrincipalSelectionCallInterceptor
        implements
            CallLifecycle.Around {

    /** 业务主体选择节点编码。 */
    public static final TreeCode CODE = TreeCode.of("CALL/PRINCIPAL_SELECTION");

    private static final Logger LOG = LoggerFactory.getLogger(PrincipalSelectionCallInterceptor.class);

    private final ApplicationContext applicationContext;

    private final PrincipalSelectionInputResolver inputResolver;

    private final PrincipalSelectionInvoker invoker;

    /**
     * 创建调用中的业务身份选择处理。
     *
     * @param applicationContext 提供具体 Principal 选择器的 Spring 容器
     * @param inputResolver      HTTP 选择输入解析器
     * @param invoker            类型化选择器调用器
     */
    public PrincipalSelectionCallInterceptor(
            ApplicationContext applicationContext,
            PrincipalSelectionInputResolver inputResolver,
            PrincipalSelectionInvoker invoker) {
        this.applicationContext = applicationContext;
        this.inputResolver = inputResolver;
        this.invoker = invoker;
    }

    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.declaration(CurrentPrincipal.class).isPresent();
    }

    /**
     * 在权限判断前确定主体；MVC 参数绑定阶段已经选择时复用结果。
     *
     * @param execution 当前方法执行
     * @param next      剩余调用
     * @return 剩余调用结果
     * @throws IllegalStateException 需要选择主体但缺少 HTTP 输入或没有解析到必需主体
     */
    @Override
    public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
        CurrentPrincipal declaration = execution.declaration(CurrentPrincipal.class).orElseThrow();

        Call call = execution.call();
        if (!call.isPrincipalAssessed()) {
            // 只在一次调用中选择一次 Principal；之后的切面和业务方法复用 Call 中的选择结果。
            PrincipalSelection<?> selection = CurrentPrincipalDeclaration.selection(
                    declaration,
                    applicationContext);
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes == null) {
                throw new IllegalStateException("业务身份选择只能在 HTTP 请求中执行");
            }
            HttpServletRequest request = attributes.getRequest();
            PrincipalSelectionInput input = inputResolver.resolve(
                    execution.method(),
                    execution.arguments(),
                    request);
            Principal principal = invoker.select(
                    selection,
                    call.session().orElse(null),
                    call.subject().orElse(null),
                    call.account().orElse(null),
                    input);
            if (principal != null) {
                call.selectPrincipal(principal);
                MDC.put("principalId", Long.toString(principal.principalId()));
                LOG.trace(
                        "业务身份已选择 | callId={} principalId={} selector={}",
                        call.callId(),
                        principal.principalId(),
                        selection.getClass().getName());
            } else if (declaration.principalRequired()) {
                throw new IllegalStateException("当前接口必须确定业务身份");
            }
        }
        // Principal 已写入 Call 后才继续 Controller，权限判断不会重新选择业务身份。
        return next.proceed();
    }

    /** 异步业务复用已经完成的主体选择，并将异步等待交给内层生命周期。 */
    @Override
    public @NotNull CompletionStage<CallOutcome> aroundAsync(
            @NotNull CallExecution execution,
            @NotNull CallLifecycle.AsyncNext next) {
        around(execution, () -> CallOutcome.replayed("principal-selection"));
        return next.proceedAsync();
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull Set<TreeCode> after() {
        return Set.of(IdentityCallInterceptor.CODE);
    }

    @Override
    public @NotNull Set<TreeCode> before() {
        return Set.of(IdentityCallInterceptor.PrincipalRequirement.CODE, PermissionCallInterceptor.CODE);
    }
}
