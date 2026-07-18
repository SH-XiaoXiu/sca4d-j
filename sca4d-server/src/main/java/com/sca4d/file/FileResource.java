package com.sca4d.file;

import com.sca4d.file.storage.FileStorageProvider;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** 文件资源及其存储映射。 */
@Data
@Accessors(chain = true)
public class FileResource {
    private Long fileId;
    private Long ownerPrincipalId;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private FileStatus status;
    private OffsetDateTime uploadExpiresAt;
    private OffsetDateTime readyAt;
    private OffsetDateTime consumedAt;
    private OffsetDateTime deleteRequestedAt;
    private OffsetDateTime deletedAt;
    private Long version;
    private FileStorageProvider provider;
    private String bucket;
    private String objectKey;
    private String temporaryObjectKey;
}
