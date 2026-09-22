package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.audit.AuditOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 AuditOperation 与权限 Operation 分离后的操作键约束。 */
class AuditOperationTest {

    @Test
    @DisplayName("Audit 操作使用规范层级键")
    void acceptsHierarchicalOperation() {
        AuditOperation operation = AuditOperation.declared("PLATFORM/ACCOUNT/STATUS/CHANGE");

        assertEquals("AUDIT/OPERATIONS", operation.type());
        assertEquals("PLATFORM/ACCOUNT/STATUS/CHANGE", operation.value());
        assertEquals(operation.value(), operation.id());
        assertEquals("LEGACY:OPERATION", new AuditOperation("legacy:operation").id());
    }

    @Test
    @DisplayName("Audit 操作拒绝点号分隔和空层级键")
    void rejectsNonHierarchicalOperation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditOperation.declared("PLATFORM.ACCOUNT.UPDATE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditOperation.declared("platform//update"));
        assertEquals("PLATFORM/ACCOUNT/UPDATE", AuditOperation.declared("Platform/ACCOUNT/UPDATE").value());
    }
}
