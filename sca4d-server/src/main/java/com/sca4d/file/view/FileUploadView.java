package com.sca4d.file.view;

import com.sca4d.file.FileUpload;
import com.sca4d.file.FileUploadMethod;
import com.sca4d.file.FileUploadMode;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/** 文件上传票据视图，向客户端公开上传指令、有效期和后续业务消费所需的授权。 */
@Data
@Accessors(chain = true)
public class FileUploadView {
    private Long fileId;
    private FileUploadMode uploadMode;
    private FileUploadMethod method;
    private String url;
    private Map<String, String> headers;
    private Map<String, String> formFields;
    private OffsetDateTime expiresAt;
    private String uploadGrant;

    /**
     * 将文件业务模块生成的上传票据转换为 HTTP 响应。
     *
     * @param value 已持久化文件主键及其存储上传指令
     * @param uploadGrant 文件完成上传后绑定业务资源时提交的短期授权
     * @return 保持上传方法、地址、请求字段和到期时间不变的客户端视图
     */
    public static @NotNull FileUploadView from(@NotNull FileUpload value, @NotNull String uploadGrant) {
        return new FileUploadView()
                .setFileId(value.getFileId())
                .setUploadMode(value.getInstruction().getMode())
                .setMethod(value.getInstruction().getMethod())
                .setUrl(value.getInstruction().getUrl())
                .setHeaders(value.getInstruction().getHeaders())
                .setFormFields(value.getInstruction().getFormFields())
                .setExpiresAt(value.getInstruction().getExpiresAt())
                .setUploadGrant(uploadGrant);
    }
}
