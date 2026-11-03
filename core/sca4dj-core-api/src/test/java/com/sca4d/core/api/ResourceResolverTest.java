package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.audit.AuditInvocation;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.resource.SingleResourceResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/** 验证资源解析在参数错误、权限检查和操作后审计中的公开行为。 */
class ResourceResolverTest {

    @Test
    void permissionAndAuditResolveTheSameTarget() throws Exception {
        SingleResourceResolver resolver = new DocumentResolver();
        CallExecution execution = execution(42L);
        Resource resource = resolver.resolve(execution).iterator().next();
        execution.recordOutcome(CallOutcome.success(null));
        Resource auditTarget = resolver.resolve(new AuditInvocation(execution, AuditResult.SUCCESS));
        assertEquals("42", resource.id());
        assertEquals(resource, auditTarget);
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(execution).clear());
    }

    @Test
    void invalidArgumentsFailInsteadOfReturningAnEmptyPermissionTarget() throws Exception {
        SingleResourceResolver resolver = new DocumentResolver();
        for (Object[] arguments : List.of(new Object[0], new Object[] {null}, new Object[] {"42"})) {
            CallExecution execution = execution(arguments);
            assertThrows(IllegalStateException.class, () -> resolver.resolve(execution));
        }
    }

    @Test
    void auditCanUseAnIdAfterTheResourceWasDeleted() throws Exception {
        DeletableDocumentResolver resolver = new DeletableDocumentResolver();
        CallExecution execution = execution(42L);
        Resource before = resolver.resolve(execution).iterator().next();
        resolver.deleted = true;
        execution.recordOutcome(CallOutcome.success(null));
        assertThrows(IllegalStateException.class, () -> resolver.resolve(execution));
        assertEquals(before, resolver.resolve(new AuditInvocation(execution, AuditResult.SUCCESS)));
    }

    private static CallExecution execution(Object... arguments) throws Exception {
        return new CallExecution(
                new Call("resolver-test", Instant.EPOCH),
                DocumentResolver.class,
                DocumentResolver.class.getDeclaredMethod("update", long.class),
                arguments,
                void.class);
    }

    /** 文档权限与审计都可以直接使用路径 ID 的示例。 */
    private static class DocumentResolver extends SingleResourceResolver {
        @Override
        protected @NotNull Resource resolveResource(@NotNull CallExecution execution) {
            return new Resource.Ref("DOCUMENT", Long.toString(longArgument(execution, 0)));
        }

        public void update(long id) {
        }
    }

    /** 模拟实体已经删除，但审计仍须保留目标 ID。 */
    private static final class DeletableDocumentResolver extends DocumentResolver {
        private boolean deleted;

        @Override
        protected @NotNull Resource resolveResource(@NotNull CallExecution execution) {
            if (deleted) {
                throw new IllegalStateException("文档已删除");
            }
            return super.resolveResource(execution);
        }

        @Override
        public @NotNull Resource resolve(@NotNull AuditInvocation invocation) {
            return new Resource.Ref("DOCUMENT", Long.toString(longArgument(invocation.execution(), 0)));
        }
    }
}
