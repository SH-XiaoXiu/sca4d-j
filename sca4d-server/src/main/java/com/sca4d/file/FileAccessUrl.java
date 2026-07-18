package com.sca4d.file;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** 文件短期访问地址。 */
@Data
@Accessors(chain = true)
public class FileAccessUrl {
    private Long fileId;
    private String url;
    private OffsetDateTime expiresAt;
}
