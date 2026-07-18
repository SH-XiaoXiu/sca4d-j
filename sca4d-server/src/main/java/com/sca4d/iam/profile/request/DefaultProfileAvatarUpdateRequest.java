package com.sca4d.iam.profile.request;

import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdMode;
import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import lombok.Data;

/** 替换 DEFAULT Profile 头像时提交的文件引用和版本。 */
@Data
public class DefaultProfileAvatarUpdateRequest {

    @Required
    @BizId(mode = BizIdMode.NUM)
    private Long fileId;

    @Required
    @SafeText(max = 4096)
    private String uploadGrant;

    @Required
    private Long profileVersion;
}
