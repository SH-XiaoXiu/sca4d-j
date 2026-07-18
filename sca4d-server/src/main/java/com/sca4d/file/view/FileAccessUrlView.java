package com.sca4d.file.view;

import com.sca4d.file.FileAccessUrl;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** 文件访问地址视图。 */
@Data
@Accessors(chain = true)
public class FileAccessUrlView {
    private Long fileId;
    private String url;
    private OffsetDateTime expiresAt;

    /** @return 包含文件主键、短期访问地址和过期时间的响应视图 */
    public static @NotNull FileAccessUrlView from(@NotNull FileAccessUrl value) {
        return new FileAccessUrlView()
                .setFileId(value.getFileId())
                .setUrl(value.getUrl())
                .setExpiresAt(value.getExpiresAt());
    }
}
