package com.sca4d.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.common.resource.ResourceReferenceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证审计来源从当前请求读取操作者、客户端和资源名称。 */
class DefaultAuditSourceTest {

    @AfterEach
    void clearCurrentRequest() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldReadAuditRequestFactsFromCurrentRequestOnly() {
        CurrentRequest request = new CurrentRequest();
        request.setTraceId("trace-1");
        request.setMethod("POST");
        request.setPath("/orders");
        request.setClientIp("127.0.0.1");
        request.setHeaders(Map.of("user-agent", "Mozilla/5.0"));
        CurrentRequestHolder.set(request);

        AuditRequest auditRequest = new DefaultAuditSource(mock(ResourceReferenceResolver.class)).request();

        assertThat(auditRequest.getTraceId()).isEqualTo("trace-1");
        assertThat(auditRequest.getMethod()).isEqualTo("POST");
        assertThat(auditRequest.getPath()).isEqualTo("/orders");
        assertThat(auditRequest.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(auditRequest.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void shouldCaptureRecognizableTargetLabel() {
        ResourceReferenceResolver resolver = mock(ResourceReferenceResolver.class);
        ResourceReferenceKey key = new ResourceReferenceKey("user", "10");
        when(resolver.resolve(List.of(key))).thenReturn(Map.of(
                key,
                new ResourceReference(key, "张三", null, ResourceNavigationState.NAVIGABLE, "/users/10", null)));

        String label = new DefaultAuditSource(resolver).targetLabel("user", "10");

        assertThat(label).isEqualTo("张三");
    }

    @Test
    void shouldNotCaptureTargetLabelOutsideCurrentPermissionBoundary() {
        ResourceReferenceResolver resolver = mock(ResourceReferenceResolver.class);
        ResourceReferenceKey key = new ResourceReferenceKey("user", "10");
        when(resolver.resolve(List.of(key))).thenReturn(Map.of(
                key,
                new ResourceReference(
                        key,
                        "不可公开的名称",
                        null,
                        ResourceNavigationState.FORBIDDEN,
                        null,
                        "当前作用域无权查看详情")));

        String label = new DefaultAuditSource(resolver).targetLabel("user", "10");

        assertThat(label).isNull();
    }
}
