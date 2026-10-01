package com.sca4d.core.api.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

/** 验证 Audit 调用快照按指定类型读取业务返回值。 */
class AuditInvocationTest {

    @Test
    @DisplayName("按指定类型返回业务结果")
    void returnsResultWithRequestedType() throws Exception {
        AuditInvocation invocation = invocation("done");

        assertEquals("done", invocation.getResult(String.class));
    }

    @Test
    @DisplayName("没有业务返回值时拒绝读取结果")
    void rejectsMissingResult() throws Exception {
        AuditInvocation invocation = invocation(null);

        assertThrows(IllegalStateException.class, () -> invocation.getResult(String.class));
    }

    @Test
    @DisplayName("返回值类型不匹配时拒绝读取结果")
    void rejectsUnexpectedResultType() throws Exception {
        AuditInvocation invocation = invocation("done");

        assertThrows(ClassCastException.class, () -> invocation.getResult(Integer.class));
    }

    @Test
    @DisplayName("结果类型为空时拒绝读取结果")
    void rejectsMissingResultType() throws Exception {
        AuditInvocation invocation = invocation("done");

        assertThrows(NullPointerException.class, () -> invocation.getResult(null));
    }

    private static AuditInvocation invocation(Object result) throws Exception {
        Method method = AuditInvocationTest.class.getDeclaredMethod("auditedMethod");
        CallExecution execution = new CallExecution(
                Call.system("audit-result-test", Instant.EPOCH, new SystemPrincipal(1, TreeCode.ofSegment("MAIN"))),
                AuditInvocationTest.class,
                method,
                new Object[0],
                Object.class);
        execution.recordOutcome(CallOutcome.success(result));
        return new AuditInvocation(execution, AuditResult.SUCCESS);
    }

    @SuppressWarnings("unused")
    private void auditedMethod() {
    }
}
