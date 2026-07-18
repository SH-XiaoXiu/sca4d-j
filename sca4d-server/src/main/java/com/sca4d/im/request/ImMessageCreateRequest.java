package com.sca4d.im.request;

import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdMode;
import com.sca4d.common.validation.SafeText;
import com.sca4d.im.ImMessageContentType;
import lombok.Data;

import java.util.Map;

/**
 * IM 消息发送请求。
 */
@Data
public class ImMessageCreateRequest {

    private ImMessageContentType contentType;

    @SafeText(max = 4000)
    private String content;

    @BizId(mode = BizIdMode.NUM)
    private Long attachmentFileId;

    @SafeText(max = 4096)
    private String attachmentUploadGrant;

    private Map<String, Object> payload;
}
