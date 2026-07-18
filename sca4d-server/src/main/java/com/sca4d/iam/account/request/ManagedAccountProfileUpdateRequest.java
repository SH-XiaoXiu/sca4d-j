package com.sca4d.iam.account.request;

import com.sca4d.common.validation.DisplayName;
import com.sca4d.common.validation.PhoneNumber;
import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import jakarta.validation.constraints.Email;
import lombok.Data;

/** 修改 GLOBAL Account 对应 Subject 通用资料时提交的字段。 */
@Data
public class ManagedAccountProfileUpdateRequest {
    @Required
    @DisplayName
    private String displayName;
    @PhoneNumber
    private String phone;
    @Email
    @SafeText(max = 128)
    private String email;
    @Required
    private Long subjectVersion;
}
