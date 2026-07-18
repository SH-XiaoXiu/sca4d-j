package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.file.FileStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** 文件资源实体。 */
@Data
@Accessors(chain = true)
@Table("sys_file_resource")
public class FileResourceEntity {
    private Long id;
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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
