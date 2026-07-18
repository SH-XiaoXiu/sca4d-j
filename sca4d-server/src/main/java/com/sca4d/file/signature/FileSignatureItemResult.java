package com.sca4d.file.signature;

import com.sca4d.file.FileAccessUrl;
import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.FileUpload;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 单项文件签名结果。
 */
@Data
@Accessors(chain = true)
public class FileSignatureItemResult {

    private String requestId;
    private FileSignatureOperation operation;
    private List<FileAccessUrl> files = List.of();
    private FileUpload upload;
    private String uploadGrant;
}
