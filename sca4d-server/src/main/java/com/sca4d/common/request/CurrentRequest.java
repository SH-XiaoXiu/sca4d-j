package com.sca4d.common.request;

import com.sca4d.common.access.AccessCriteria;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.MissingAuthException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.permission.SessionPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前请求。
 *
 * <p>保存 HTTP、STOMP 与 SYSTEM 执行期间的请求信息、认证身份、执行主体、权限和数据访问条件。
 */
public class CurrentRequest {

    private String traceId;
    private String method;
    private String path;
    private String clientIp;
    private OffsetDateTime startedAt;
    private Session session;
    private Subject subject;
    private Account account;
    private Principal principal;
    private SessionPermission sessionPermission;
    private Map<String, Object> pathVariables = Map.of();
    private Map<String, Object> queryParameters = Map.of();
    private Map<String, Object> headers = Map.of();
    private Object payload;
    private final Map<Class<? extends AccessCriteria>, AccessCriteria> access = new LinkedHashMap<>();

    /** 创建不包含传输层数据的空请求态。 */
    public CurrentRequest() {
    }

    /**
     * 创建包含传输层基础数据的请求态。
     *
     * @param traceId 一次请求跨服务传递的追踪标识
     * @param method HTTP 方法或消息帧方法
     * @param path HTTP 路径或消息 destination
     * @param clientIp 可识别时的客户端地址
     * @param startedAt 请求开始时间
     */
    public CurrentRequest(
            @NotNull String traceId,
            @NotNull String method,
            @NotNull String path,
            @Nullable String clientIp,
            @NotNull OffsetDateTime startedAt) {
        this.traceId = traceId;
        this.method = method;
        this.path = path;
        this.clientIp = clientIp;
        this.startedAt = startedAt;
    }

    /**
     * 创建只包含已认证会话数据的请求。
     *
     * @param session 已认证会话
     * @return 当前请求
     */
    public static @NotNull CurrentRequest authenticated(@NotNull Session session) {
        CurrentRequest request = new CurrentRequest();
        request.setSession(session);
        request.setSubject(session.getSubject());
        request.setAccount(session.getAccount());
        return request;
    }

    /** @return HTTP 或消息入口已经设置的请求追踪标识 */
    public @Nullable String getTraceId() {
        return traceId;
    }

    /** @param traceId 一次请求跨服务传递的追踪标识 */
    public void setTraceId(@NotNull String traceId) {
        this.traceId = traceId;
    }

    /** @return HTTP 方法或消息帧方法 */
    public @Nullable String getMethod() {
        return method;
    }

    /** @param method HTTP 方法或消息帧方法 */
    public void setMethod(@NotNull String method) {
        this.method = method;
    }

    /** @return HTTP 路径或消息 destination */
    public @Nullable String getPath() {
        return path;
    }

    /** @param path HTTP 路径或消息 destination */
    public void setPath(@NotNull String path) {
        this.path = path;
    }

    /** @return 可识别时的客户端地址 */
    public @Nullable String getClientIp() {
        return clientIp;
    }

    /** @param clientIp 可识别时的客户端地址 */
    public void setClientIp(@Nullable String clientIp) {
        this.clientIp = clientIp;
    }

    /** @return 传输层已经建立时的请求开始时间 */
    public @Nullable OffsetDateTime getStartedAt() {
        return startedAt;
    }

    /** @param startedAt 请求开始时间 */
    public void setStartedAt(@NotNull OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    /** @return 当前请求恢复出的认证会话 */
    public @Nullable Session getSession() {
        return session;
    }

    /** @param session 当前请求恢复出的认证会话 */
    public void setSession(@Nullable Session session) {
        this.session = session;
    }

    /** @return 当前会话对应的登录 Subject */
    public @Nullable Subject getSubject() {
        return subject;
    }

    /** @param subject 当前会话对应的登录 Subject */
    public void setSubject(@Nullable Subject subject) {
        this.subject = subject;
    }

    /** @return 当前会话完成认证的登录 Account */
    public @Nullable Account getAccount() {
        return account;
    }

    /** @param account 当前会话完成认证的登录 Account */
    public void setAccount(@Nullable Account account) {
        this.account = account;
    }

    /** @return 当前请求最终选择的执行 Principal */
    public @Nullable Principal getPrincipal() {
        return principal;
    }

    /** @param principal 当前请求最终选择的执行 Principal */
    public void setPrincipal(@Nullable Principal principal) {
        this.principal = principal;
    }

    /** @return 当前 Principal 已解析的会话权限 */
    public @Nullable SessionPermission getSessionPermission() {
        return sessionPermission;
    }

    /** @param sessionPermission 当前 Principal 已解析的会话权限 */
    public void setSessionPermission(@Nullable SessionPermission sessionPermission) {
        this.sessionPermission = sessionPermission;
    }

    /** @return 不可变的路径变量数据 */
    public @NotNull Map<String, Object> getPathVariables() {
        return pathVariables;
    }

    /** @param pathVariables 当前请求的路径变量数据 */
    public void setPathVariables(@NotNull Map<String, ?> pathVariables) {
        this.pathVariables = Map.copyOf(pathVariables);
    }

    /** @return 不可变的查询参数数据 */
    public @NotNull Map<String, Object> getQueryParameters() {
        return queryParameters;
    }

    /** @param queryParameters 当前请求的查询参数数据 */
    public void setQueryParameters(@NotNull Map<String, ?> queryParameters) {
        this.queryParameters = Map.copyOf(queryParameters);
    }

    /** @return 不可变的请求头数据 */
    public @NotNull Map<String, Object> getHeaders() {
        return headers;
    }

    /** @param headers 当前请求的请求头数据 */
    public void setHeaders(@NotNull Map<String, ?> headers) {
        this.headers = Map.copyOf(headers);
    }

    /** @return 消息帧或方法调用绑定的原始载荷 */
    public @Nullable Object getPayload() {
        return payload;
    }

    /** @param payload 消息帧或方法调用绑定的原始载荷 */
    public void setPayload(@Nullable Object payload) {
        this.payload = payload;
    }

    /**
     * 判断当前请求是否由有效会话发起。
     *
     * @return Session 与 Subject 同时存在时返回 true
     */
    public boolean authenticated() {
        return session != null && subject != null && account != null;
    }

    /**
     * 获取当前已认证会话。
     *
     * @return 当前会话
     */
    public @NotNull Session requireSession() {
        if (session == null || subject == null || account == null) {
            throw new UnauthenticatedException();
        }
        return session;
    }

    /**
     * 获取当前已认证 Subject。
     *
     * @return 当前 Subject
     */
    public @NotNull Subject requireSubject() {
        if (session == null || subject == null || account == null) {
            throw new UnauthenticatedException();
        }
        return subject;
    }

    /**
     * 获取当前已认证 Account。
     *
     * @return 当前 Account
     */
    public @NotNull Account requireAccount() {
        if (session == null || subject == null || account == null) {
            throw new UnauthenticatedException();
        }
        return account;
    }

    /**
     * 获取当前执行 Principal。
     *
     * @return 当前 Principal
     */
    public @NotNull Principal requirePrincipal() {
        if (principal == null) {
            throw new MissingAuthException();
        }
        return principal;
    }

    /**
     * 获取当前 Principal 的会话权限。
     *
     * @return 当前会话权限
     */
    public @NotNull SessionPermission requireSessionPermission() {
        if (sessionPermission == null) {
            throw new MissingAuthException();
        }
        return sessionPermission;
    }

    /**
     * 判断当前请求是否为显式 SYSTEM 执行。
     *
     * @return 当前 Principal 为 SYSTEM 时返回 true
     */
    public boolean system() {
        return principal != null && principal.getPrincipalType() == PrincipalType.SYSTEM;
    }

    /**
     * 写入当前请求的业务模块资源访问范围。
     *
     * @param type 条件固定类型
     * @param criteria 业务模块访问范围
     * @param <C> 条件类型
     */
    public <C extends AccessCriteria> void putAccess(
            @NotNull Class<C> type, @NotNull C criteria) {
        AccessCriteria existing = access.putIfAbsent(type, criteria);
        if (existing != null) {
            throw new ResourceAccessDeniedException();
        }
    }

    /**
     * 获取当前请求的业务模块资源访问范围。
     *
     * @param type 条件类型
     * @param <C> 条件类型
     * @return 业务模块访问范围
     */
    public <C extends AccessCriteria> @NotNull C requireAccess(@NotNull Class<C> type) {
        if (!access.containsKey(type)) {
            throw new ResourceAccessDeniedException();
        }
        return type.cast(access.get(type));
    }
}
