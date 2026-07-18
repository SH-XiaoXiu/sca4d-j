package com.sca4d.file;

import com.sca4d.file.storage.FileUploadInstruction;
import lombok.Data;
import lombok.experimental.Accessors;

/** 文件上传票据。 */
@Data
@Accessors(chain = true)
public class FileUpload {
    private Long fileId;
    private FileUploadInstruction instruction;
}
