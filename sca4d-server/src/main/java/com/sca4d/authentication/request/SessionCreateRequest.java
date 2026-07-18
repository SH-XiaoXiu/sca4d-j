package com.sca4d.authentication.request;

import com.sca4d.common.validation.Required;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 使用已经完成验证的 Authentication 创建登录会话。 */
@Data
@NoArgsConstructor
public class SessionCreateRequest {
    @Required
    private String authenticationId;
}
