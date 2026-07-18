package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.file.storage.FileStorageProvider;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** 文件存储对象映射实体。 */
@Data
@Accessors(chain = true)
@Table("sys_file_object")
public class FileObjectEntity {
    private Long id;
    private Long fileId;
    private FileStorageProvider provider;
    private String bucket;
    private String objectKey;
    private String temporaryObjectKey;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
