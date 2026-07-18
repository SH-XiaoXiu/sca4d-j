package com.sca4d.file.storage;

import com.sca4d.file.FileUploadMethod;
import com.sca4d.file.FileUploadMode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 文件存储为客户端签发的完整上传指令。
 *
 * <p>客户端必须在 {@code expiresAt} 前按类型安全的 HTTP 方法调用目标地址，并原样携带要求的 headers 与
 * formFields。指令只描述传输协议，不代表文件已经上传完成或被业务资源消费。
 */
@Data
@Accessors(chain = true)
public class FileUploadInstruction {
    private FileUploadMode mode;
    private FileUploadMethod method;
    private String url;
    private Map<String, String> headers = Map.of();
    private Map<String, String> formFields = Map.of();
    private OffsetDateTime expiresAt;
}
