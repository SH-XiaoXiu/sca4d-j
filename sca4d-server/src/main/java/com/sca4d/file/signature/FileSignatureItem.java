package com.sca4d.file.signature;

import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdMode;
import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import com.sca4d.file.FileSignatureOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 单项文件签名意图。
 *
 * <p>该对象由 HTTP 请求绑定后进入文件签名策略，包含读取已有文件或创建上传授权所需的请求字段。
 * {@code scene} 必须命中业务模块通过 {@code FileSignaturePolicy} 注册的固定场景编码。
 */
@Data
public class FileSignatureItem {

    /** 文件签名场景编码的最大长度。 */
    public static final int SCENE_MAX_LENGTH = 128;

    /** 业务模块文件签名场景共用使用至少三级的点分编码。 */
    public static final String SCENE_REGEXP = "^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){2,}$";

    @NotBlank
    @BizId(max = 128)
    private String requestId;

    @Required
    private FileSignatureOperation operation;

    @NotBlank
    @BizId(mode = BizIdMode.CUSTOM, max = SCENE_MAX_LENGTH, regexp = SCENE_REGEXP)
    private String scene;

    @SafeText(max = 256)
    private String resourceId;

    @BizId(mode = BizIdMode.NUM)
    private Long fileId;

    @SafeText(max = 255)
    private String originalFilename;

    @SafeText(max = 128)
    private String contentType;

    @Positive
    private Long sizeBytes;
}
