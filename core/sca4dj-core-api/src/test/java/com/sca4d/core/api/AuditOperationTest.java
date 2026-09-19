package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.audit.AuditOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 AuditOperation 与权限 Operation 分离后的操作键约束。 */
class AuditOperationTest {

    @Test
    @DisplayName("Audit 操作使用小写层级键")
    void acceptsHierarchicalOperation() {
        AuditOperation operation = AuditOperation.declared("platform/account/status/change");

        assertEquals("audit-operation", operation.type());
        assertEquals("platform/account/status/change", operation.value());
        assertEquals(operation.value(), operation.id());
        assertEquals("legacy:operation", new AuditOperation("legacy:operation").id());
    }

    @Test
    @DisplayName("Audit 操作拒绝权限风格和空层级键")
    void rejectsNonHierarchicalOperation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditOperation.declared("platform.account.update"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditOperation.declared("platform//update"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditOperation.declared("Platform/account/update"));
    }
}
