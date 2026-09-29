package com.sca4d.core.api.call;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 保存入口界定的一次往返的输入快照、目标资源、身份和权限判定；不解释传输协议。 */
public final class Call {

    private final String callId;

    private final Instant startedAt;

    private final @NotNull DataValues inputs;

    private final @Nullable Resource targetResource;

    private @Nullable String parentCallId;

    private Session session;

    private Subject subject;

    private Account account;

    private Principal principal;

    private final Map<Permission, PermissionDecision> permissionDecisions = new LinkedHashMap<>();

    private boolean authenticationAssessed;

    private boolean principalAssessed;

    /**
     * 创建尚未认证的调用。
     *
     * @param callId 调用链中的调用标识
     * @param startedAt 调用开始时间
     */
    public Call(String callId, Instant startedAt) {
        this(callId, startedAt, DataValues.builder().build(), null);
    }

    /**
     * 创建带有类型化入口数据和可选目标资源、尚未认证的调用。
     *
     * @param callId 调用链中的调用标识
     * @param startedAt 调用开始时间
     * @param inputs 入口已采集的快照；所含值必须在调用期间保持不变
     * @param targetResource 已明确的主要目标；不能从协议路径猜测
     */
    public Call(String callId, Instant startedAt, @NotNull DataValues inputs, @Nullable Resource targetResource) {
        this.callId = TextUtils.requireText(callId, "调用标识");
        this.startedAt = Objects.requireNonNull(startedAt, "调用开始时间不能为空");
        this.inputs = Objects.requireNonNull(inputs, "调用输入不能为空");
        this.targetResource = targetResource;
    }

    /** 以独立标识和当前时间创建身份相同的任务调用，不继承输入、主要资源或权限判定。 */
    public @NotNull Call fork() {
        return fork(DataValues.builder().build(), null);
    }

    /** 为明确输入的任务创建独立调用，主要资源未指定。 */
    public @NotNull Call fork(@NotNull DataValues inputs) {
        return fork(inputs, null);
    }

    /** 为明确输入和主要资源的任务生成独立调用标识及当前开始时间。 */
    public @NotNull Call fork(@NotNull DataValues inputs, @Nullable Resource targetResource) {
        return fork(UUID.randomUUID().toString(), Instant.now(), inputs, targetResource);
    }

    /** 使用入口指定的标识和时间创建独立调用，不继承传输数据或目标资源。 */
    public @NotNull Call fork(@NotNull String callId, @NotNull Instant startedAt) {
        return fork(callId, startedAt, DataValues.builder().build(), null);
    }

    /**
     * 为异步工作建立独立调用，复制已确认身份而不复制权限结论或传输数据。
     *
     * @param callId 新调用标识，必须与原调用不同
     * @param startedAt 新调用开始时间
     * @param inputs 异步任务明确需要的不可变输入
     * @param targetResource 异步任务的目标资源
     * @return 没有共享可变调用状态的新调用
     */
    public @NotNull Call fork(
            @NotNull String callId,
            @NotNull Instant startedAt,
            @NotNull DataValues inputs,
            @Nullable Resource targetResource) {
        Call child = new Call(callId, startedAt, inputs, targetResource);
        if (this.callId.equals(child.callId)) {
            throw new IllegalArgumentException("异步调用必须使用独立调用标识");
        }
        child.parentCallId = this.callId;
        child.session = session;
        child.subject = subject;
        child.account = account;
        child.principal = principal;
        child.authenticationAssessed = authenticationAssessed;
        child.principalAssessed = principalAssessed;
        return child;
    }

    /**
     * 创建使用固定系统身份的任务调用。
     *
     * @param callId 调用标识
     * @param startedAt 调用开始时间
     * @return 已经完成身份阶段的系统调用
     */
    public static Call system(String callId, Instant startedAt) {
        return system(callId, startedAt, SystemPrincipals.mainPrincipal());
    }

    /** 使用已经确认的领域 Principal 建立系统调用，不赋予额外权限。 */
    public static Call system(String callId, Instant startedAt, SystemPrincipal principal) {
        Call call = new Call(callId, startedAt);
        call.authenticationAssessed = true;
        call.subject = Subject.SYSTEM;
        call.principalAssessed = true;
        call.principal = Objects.requireNonNull(principal, "系统 Principal 不能为空");
        return call;
    }

    /**
     * 写入一次成功认证形成的完整 Session 快照。
     *
     * @param session 当前会话
     * @throws IllegalStateException 认证阶段已经写入时抛出
     */
    @Contract(mutates = "this")
    public void authenticate(Session session) {
        if (authenticationAssessed) {
            throw new IllegalStateException("调用认证阶段只能写入一次");
        }
        Session checkedSession = Objects.requireNonNull(session, "会话不能为空");
        this.session = checkedSession;
        this.subject = checkedSession.subject();
        this.account = checkedSession.account();
        this.authenticationAssessed = true;
    }

    /**
     * 选择本次调用使用的业务主体，并绑定 Session 中属于所选主体的静态资源。
     *
     * @param principal 当前业务主体
     * @throws IllegalStateException 尚未完成认证、没有 Session 或已经选择其他主体时抛出
     * @throws IllegalArgumentException Principal 的精确类型和标识不属于当前 Session 时抛出
     */
    @Contract(mutates = "this")
    public void selectPrincipal(Principal principal) {
        if (!authenticationAssessed) {
            throw new IllegalStateException("选择业务主体前必须先完成认证");
        }
        Principal checked = Objects.requireNonNull(principal, "业务主体不能为空");
        if (principalAssessed) {
            if (this.principal.principalId() == checked.principalId()
                    && this.principal.getClass().equals(checked.getClass())) {
                return;
            }
            throw new IllegalStateException("同一次调用不能切换业务主体");
        }
        if (session == null) {
            throw new IllegalStateException("用户调用选择业务主体前必须存在 Session");
        }
        this.principal = session.principal(checked);
        this.principalAssessed = true;
    }

    /**
     * 判断当前 Principal 的静态资源视图是否包含指定资源。
     *
     * @param resource 需要同时匹配类型和标识的资源
     * @return 尚未选择 Principal、系统调用没有 Session 或资源不存在时为 {@code false}
     */
    @Contract(pure = true)
    public boolean grants(Resource resource) {
        if (!principalAssessed || session == null) {
            return false;
        }
        Resource checked = Objects.requireNonNull(resource, "资源不能为空");
        return session.grants(principal.principalId(), checked);
    }

    /**
     * 判断当前 Principal 的静态资源视图是否包含指定 Operation。
     *
     * <p>此重载供调用方直接传入注解和 Operation 模块使用的字符串常量，不需要重复创建 {@link Operation}。
     *
     * @param operationCode 非空白 Operation 编码
     * @return 尚未选择 Principal、系统调用没有 Session 或 Operation 不存在时为 {@code false}
     */
    @Contract(pure = true)
    public boolean grants(@NotNull String operationCode) {
        return grants(new Operation(operationCode));
    }

    /**
     * 保存权限策略对当前调用作出的唯一判定。
     *
     * @param permission 已完成判断的权限
     * @param decision 唯一策略作出的权限判定
     * @throws IllegalStateException 同一权限已经写入或资源范围类型不匹配时抛出
     */
    @Contract(mutates = "this")
    public void recordPermissionDecision(Permission permission, PermissionDecision decision) {
        Permission checkedPermission = Objects.requireNonNull(permission, "权限不能为空");
        PermissionDecision checkedDecision = Objects.requireNonNull(decision, "权限判定不能为空");
        checkedDecision.scope().ifPresent(scope -> {
            if (!checkedPermission.resource().type().equals(scope.resourceType())) {
                throw new IllegalStateException(
                        "资源范围类型与权限资源不一致: " + scope.resourceType() + " != "
                                + checkedPermission.resource().type());
            }
        });
        PermissionDecision existing = permissionDecisions.putIfAbsent(checkedPermission, checkedDecision);
        if (existing != null) {
            throw new IllegalStateException("同一调用中的权限只能判定一次: " + checkedPermission);
        }
    }

    /**
     * 返回指定权限在当前调用中的判定。
     *
     * @param permission 待查询的完整权限
     * @return 已经执行策略时返回判定，否则为空
     */
    @Contract(pure = true)
    public Optional<PermissionDecision> permissionDecision(Permission permission) {
        return Optional.ofNullable(permissionDecisions.get(Objects.requireNonNull(permission, "权限不能为空")));
    }

    /** @return 调用标识 */
    @Contract(pure = true)
    public String callId() {
        return callId;
    }

    /** @return 调用开始时间 */
    @Contract(pure = true)
    public Instant startedAt() {
        return startedAt;
    }

    /** @return 入口采集的类型化数据，不包含隐式传输对象 */
    @Contract(pure = true)
    public @NotNull DataValues inputs() {
        return inputs;
    }

    /** 按入口声明的键读取快照；当前入口没有提供时返回空。 */
    @Contract(pure = true)
    public <T> @NotNull Optional<T> input(@NotNull DataKey<T> key) {
        return inputs.find(key);
    }

    /** @return 已确定的主要目标，不能代替 Permission 中的资源和动作 */
    @Contract(pure = true)
    public @NotNull Optional<Resource> targetResource() {
        return Optional.ofNullable(targetResource);
    }

    /** @return 异步工作发起者的标识，不持有发起者的可变 Call */
    @Contract(pure = true)
    public @NotNull Optional<String> parentCallId() {
        return Optional.ofNullable(parentCallId);
    }

    /** @return 当前会话；匿名调用和系统调用为空 */
    @Contract(pure = true)
    public Optional<Session> session() {
        return Optional.ofNullable(session);
    }

    /** @return 当前登录身份；尚未认证时为空 */
    @Contract(pure = true)
    public Optional<Subject> subject() {
        return Optional.ofNullable(subject);
    }

    /** @return 当前账号；匿名调用和系统调用为空 */
    @Contract(pure = true)
    public Optional<Account> account() {
        return Optional.ofNullable(account);
    }

    /** @return 当前业务主体；尚未选择时为空 */
    @Contract(pure = true)
    public Optional<Principal> principal() {
        return Optional.ofNullable(principal);
    }

    /** @return 认证阶段已经完成时返回 {@code true} */
    @Contract(pure = true)
    public boolean isAuthenticationAssessed() {
        return authenticationAssessed;
    }

    /** @return 业务主体阶段已经完成时返回 {@code true} */
    @Contract(pure = true)
    public boolean isPrincipalAssessed() {
        return principalAssessed;
    }
}
