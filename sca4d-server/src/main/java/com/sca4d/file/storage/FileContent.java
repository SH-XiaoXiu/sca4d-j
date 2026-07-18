package com.sca4d.file.storage;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.InputStream;

/** 本地存储文件内容。 */
@Data
@Accessors(chain = true)
public class FileContent {
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private InputStream inputStream;
}
