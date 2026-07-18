package com.sca4d.file.storage;

import lombok.Data;
import lombok.experimental.Accessors;

/** 存储对象元数据。 */
@Data
@Accessors(chain = true)
public class FileStorageMetadata {
    private long sizeBytes;
    private String contentType;
}
