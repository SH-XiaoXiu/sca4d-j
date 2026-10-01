package com.sca4d.storage.file.config;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明客户可以管理的文件传输有效期、待绑定保留期和大小上限。 */
public class FileSettings {

    private static final SettingGroup GROUP = new SettingGroup(
            TreeCode.of("STORAGE:FILE"),
            "setting.group.storage-file",
            300);

    /** 上传申请和上传地址有效期。 */
    public final @NotNull SystemSetting<Duration> UPLOAD_TTL;

    /** 文件读取地址有效期。 */
    public final @NotNull SystemSetting<Duration> ACCESS_TTL;

    /** 上传完成但尚未绑定的文件保留期。 */
    public final @NotNull SystemSetting<Duration> READY_RETENTION;

    /** 所有文件场景共同遵守的最大申请字节数。 */
    public final @NotNull SystemSetting<Long> MAXIMUM_SIZE_BYTES;

    /** 构造模块设置，每个应用上下文独立保存 Source。 */
    public FileSettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.UPLOAD_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("STORAGE:FILE:UPLOAD_TTL"),
                GROUP,
                "setting.storage.file.upload-ttl",
                "setting.storage.file.upload-ttl.description",
                "STORAGE_UPLOAD_TTL",
                Duration.ofMinutes(10),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                100);
        this.ACCESS_TTL = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("STORAGE:FILE:ACCESS_TTL"),
                GROUP,
                "setting.storage.file.access-ttl",
                "setting.storage.file.access-ttl.description",
                "STORAGE_ACCESS_TTL",
                Duration.ofMinutes(5),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                200);
        this.READY_RETENTION = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("STORAGE:FILE:READY_RETENTION"),
                GROUP,
                "setting.storage.file.ready-retention",
                "setting.storage.file.ready-retention.description",
                "STORAGE_READY_RETENTION",
                Duration.ofHours(24),
                SettingCodec.positiveDuration(),
                Pipeline.identity(Duration.class),
                false,
                300);
        this.MAXIMUM_SIZE_BYTES = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("STORAGE:FILE:MAXIMUM_SIZE_BYTES"),
                GROUP,
                "setting.storage.file.maximum-size-bytes",
                "setting.storage.file.maximum-size-bytes.description",
                "STORAGE_MAX_SIZE_BYTES",
                20L * 1024 * 1024,
                SettingCodec.positiveLong(),
                Pipeline.identity(Long.class),
                false,
                400);
    }
}
