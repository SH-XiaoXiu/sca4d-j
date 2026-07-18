package com.sca4d.common.audit;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.common.resource.ResourceReferenceResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 从当前请求和认证信息中读取审计操作者、请求来源与目标资源名称。
 */
public class DefaultAuditSource implements AuditSource {

    private final ResourceReferenceResolver resourceReferenceResolver;

    /** @param resourceReferenceResolver 按当前请求权限查询跨模块资源名称的解析器 */
    public DefaultAuditSource(@NotNull ResourceReferenceResolver resourceReferenceResolver) {
        this.resourceReferenceResolver = resourceReferenceResolver;
    }

    @Override
    public @NotNull AuditActor actor() {
        AuditActor actor = new AuditActor();
        CurrentRequest request = CurrentRequestHolder.currentOrNull();
        if (request == null) {
            return actor;
        }
        Subject subject = request.getSubject();
        if (subject != null) {
            actor.setSubjectId(subject.getSubjectId());
            actor.setSubjectDisplayName(subject.getDisplayName());
        }
        if (request.getAccount() != null) {
            actor.setAccountId(request.getAccount().getAccountId());
        }
        Principal principal = request.getPrincipal();
        if (principal != null) {
            actor.setPrincipalId(principal.getPrincipalId());
            actor.setPrincipalType(principal.getPrincipalType());
            actor.setPrincipalDisplayName(principal.getDisplayName());
            actor.setTenantId(principal.getTenantId());
        }
        return actor;
    }

    @Override
    public @NotNull AuditRequest request() {
        AuditRequest auditRequest = new AuditRequest();
        CurrentRequest context = CurrentRequestHolder.currentOrNull();
        if (context != null) {
            auditRequest.setTraceId(context.getTraceId());
            auditRequest.setMethod(context.getMethod());
            auditRequest.setPath(context.getPath());
            auditRequest.setClientIp(context.getClientIp());
            for (Map.Entry<String, Object> header : context.getHeaders().entrySet()) {
                if ("User-Agent".equalsIgnoreCase(header.getKey()) && header.getValue() != null) {
                    auditRequest.setUserAgent(header.getValue().toString());
                    break;
                }
            }
        }
        return auditRequest;
    }

    @Override
    public @Nullable String targetLabel(@NotNull String targetType, @NotNull String targetId) {
        ResourceReferenceKey key = new ResourceReferenceKey(targetType, targetId);
        ResourceReference reference = resourceReferenceResolver.resolve(List.of(key)).get(key);
        if (reference == null || reference.getNavigationState() != ResourceNavigationState.NAVIGABLE) {
            return null;
        }
        return reference.getLabel();
    }
}
