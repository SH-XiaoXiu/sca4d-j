package com.sca4d.im.request;

import com.sca4d.common.validation.DisplayName;
import com.sca4d.im.ImConversationType;
import com.sca4d.im.ImMemberReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * IM 会话创建请求。
 */
@Data
public class ImConversationCreateRequest {

    private ImConversationType conversationType;

    @DisplayName(max = 128)
    private String title;

    @NotEmpty
    private List<@Valid ImMemberReference> members;
}
