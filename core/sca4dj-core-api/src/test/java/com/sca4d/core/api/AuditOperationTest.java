package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 验证 AuditOperation 与权限 Operation 分离后的操作键约束。
 */
class AuditOperationTest {

    @Test
    @DisplayName("Audit 操作使用规范层级键")
    void acceptsHierarchicalOperation() {
        AuditOperation operation = AuditOperation.declared("PLATFORM/ACCOUNT/STATUS/CHANGE");

        assertEquals(TreeCode.of("AUDIT/OPERATIONS"), operation.type());
        assertEquals("PLATFORM/ACCOUNT/STATUS/CHANGE", operation.code().format(NameFormat.SLASH));
        assertEquals(operation.code().format(NameFormat.SLASH), operation.id());
        assertEquals("LEGACY/OPERATION", new AuditOperation("legacy:operation").id());
    }

    @Test
    @DisplayName("Audit 操作接受点式声明并拒绝空节点")
    void rejectsNonHierarchicalOperation() {
        assertEquals(
                AuditOperation.declared("PLATFORM/ACCOUNT/UPDATE"),
                AuditOperation.declared("PLATFORM.ACCOUNT.UPDATE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditOperation.declared("platform//update"));
        assertEquals(
                "PLATFORM/ACCOUNT/UPDATE",
                AuditOperation.declared("Platform/ACCOUNT/UPDATE").code().format(NameFormat.SLASH));
    }
}
