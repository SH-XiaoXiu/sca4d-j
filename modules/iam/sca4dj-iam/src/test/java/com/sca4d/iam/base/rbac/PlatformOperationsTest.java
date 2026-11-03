package com.sca4d.iam.base.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.permission.OperationAxes;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.rbac.RbacGrantable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

/** 验证 {@link PlatformOperations} 的叶子满足标准属性轴，并标出会提升权限和不可恢复的操作。 */
class PlatformOperationsTest {

    private final OperationCatalog catalog = new OperationCatalog(
            List.of(new PlatformOperations()),
            Stream.concat(OperationAxes.standard().stream(), Stream.of(RbacGrantable.GRANTABLE)).toList());

    @Test
    @DisplayName("Declares Intent And Nature On All Platform Leaves")
    void declaresIntentAndNatureOnAllLeaves() {
        assertThat(catalog.leaves()).hasSize(9);
        assertThat(catalog.leavesUnder(OperationAxes.Intent.READ)).hasSize(2);
        assertThat(catalog.leavesWith(OperationAxes.Nature.FUNCTION)).hasSize(9);
    }

    @Test
    @DisplayName("Marks Role And Principal Role Changes As Privilege Escalation")
    void marksPrivilegeEscalation() {
        assertThat(catalog.leavesWith(OperationAxes.Risk.PRIVILEGE_ESCALATION))
                .extracting(OperationNode.Leaf::code)
                .containsExactly(
                        PlatformOperations.ROLES_CREATE,
                        PlatformOperations.ROLES_UPDATE,
                        PlatformOperations.PRINCIPAL_ROLES_ASSIGN);
        assertThat(catalog.leavesWith(OperationAxes.Risk.IRREVERSIBLE))
                .extracting(OperationNode.Leaf::code)
                .containsExactly(PlatformOperations.ROLES_DELETE, PlatformOperations.ACCOUNTS_DELETE);
    }

    @Test
    @DisplayName("Leaves Only Principal Role Assignment Out Of The Authorization Editor Catalog")
    void marksPrincipalRoleAssignmentUnlisted() {
        assertThat(catalog.leavesWith(OperationAxes.Visibility.UNLISTED))
                .extracting(OperationNode.Leaf::code)
                .containsExactly(PlatformOperations.PRINCIPAL_ROLES_ASSIGN);
    }

    @Test
    @DisplayName("Grants All Platform Leaves To The Platform Domain Only")
    void grantsAllLeavesToPlatformDomainOnly() {
        assertThat(catalog.leavesWith(RbacGrantable.of(PlatformRbacRepository.PLATFORM_DOMAIN))).hasSize(9);
        assertThat(catalog.leavesUnder(RbacGrantable.GRANTABLE.root())).hasSize(9);
    }
}
