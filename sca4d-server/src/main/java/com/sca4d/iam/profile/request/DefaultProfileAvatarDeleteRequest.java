package com.sca4d.iam.profile.request;

import com.sca4d.common.validation.Required;
import lombok.Data;

/** 清空 DEFAULT Profile 头像时提交的默认资料版本。 */
@Data
public class DefaultProfileAvatarDeleteRequest {

    @Required
    private Long profileVersion;
}
