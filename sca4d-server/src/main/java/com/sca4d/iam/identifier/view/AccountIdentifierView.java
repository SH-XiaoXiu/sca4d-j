package com.sca4d.iam.identifier.view;

import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 当前用户可见且不包含规范化值和 Contact 主键的 AccountIdentifier。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountIdentifierView {

    private Long identifierId;
    private AccountIdentifierType identifierType;
    private String value;
    private AccountIdentifierStatus status;
    private Long version;

    /** @return 当前 Identifier 的安全展示字段 */
    public static @NotNull AccountIdentifierView from(@NotNull IamAccountIdentifierEntity identifier) {
        return new AccountIdentifierView(
                identifier.getId(),
                identifier.getIdentifierType(),
                identifier.getValue(),
                identifier.getStatus(),
                identifier.getVersion());
    }
}
