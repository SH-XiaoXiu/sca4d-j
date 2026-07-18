package com.sca4d.iam.profile.request;

import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import lombok.Data;

/** 修改 Subject 默认展示名时提交的资料。 */
@Data
public class DefaultProfileUpdateRequest {

    @Required
    @SafeText(max = 128)
    private String displayName;

    @Required
    private Long subjectVersion;
}
