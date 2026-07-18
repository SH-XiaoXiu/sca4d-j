package com.sca4d.iam.profile.audit;

import com.sca4d.common.audit.AuditOperationContributor;
import com.sca4d.common.audit.AuditOperationDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/** DEFAULT Profile 修改写入的审计模块和操作定义。 */
@Component
public class DefaultProfileAuditOperations implements AuditOperationContributor {

    /** DEFAULT Profile 审计模块。 */
    public static final String DEFAULT_PROFILE = "iam.default-profile";
    /** 修改 Subject 默认展示名操作。 */
    public static final String UPDATE_DISPLAY_NAME = "update-display-name";
    /** 替换头像操作。 */
    public static final String REPLACE_AVATAR = "replace-avatar";
    /** 移除头像操作。 */
    public static final String REMOVE_AVATAR = "remove-avatar";

    @Override
    public List<AuditOperationDefinition> auditOperations() {
        return List.of(
                AuditOperationDefinition.standard(DEFAULT_PROFILE, 10, UPDATE_DISPLAY_NAME, 10),
                AuditOperationDefinition.standard(DEFAULT_PROFILE, 10, REPLACE_AVATAR, 20),
                AuditOperationDefinition.standard(DEFAULT_PROFILE, 10, REMOVE_AVATAR, 30));
    }
}
