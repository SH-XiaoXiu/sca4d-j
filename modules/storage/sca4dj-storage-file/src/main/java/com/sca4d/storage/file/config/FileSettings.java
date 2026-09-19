package com.sca4d.storage.file.config;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 声明客户可以管理的文件传输有效期、待绑定保留期和大小上限。 */
public final class FileSettings {

    private static final SettingGroup GROUP = new SettingGroup("storage.file", "setting.group.storage-file", 300);

    /** 上传申请和上传地址有效期。 */
    public static final @NotNull SystemSetting<Duration> UPLOAD_TTL = new SystemSetting<>(
            "storage.file.upload-ttl",
            1,
            GROUP,
            "setting.storage.file.upload-ttl",
            "setting.storage.file.upload-ttl.description",
            "SCA4DJ_STORAGE_UPLOAD_TTL",
            Duration.ofMinutes(10),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            100);

    /** 文件读取地址有效期。 */
    public static final @NotNull SystemSetting<Duration> ACCESS_TTL = new SystemSetting<>(
            "storage.file.access-ttl",
            1,
            GROUP,
            "setting.storage.file.access-ttl",
            "setting.storage.file.access-ttl.description",
            "SCA4DJ_STORAGE_ACCESS_TTL",
            Duration.ofMinutes(5),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            200);

    /** 上传完成但尚未绑定的文件保留期。 */
    public static final @NotNull SystemSetting<Duration> READY_RETENTION = new SystemSetting<>(
            "storage.file.ready-retention",
            1,
            GROUP,
            "setting.storage.file.ready-retention",
            "setting.storage.file.ready-retention.description",
            "SCA4DJ_STORAGE_READY_RETENTION",
            Duration.ofHours(24),
            SettingCodec.positiveDuration(),
            Pipeline.identity(Duration.class),
            false,
            300);

    /** 所有文件场景共同遵守的最大申请字节数。 */
    public static final @NotNull SystemSetting<Long> MAXIMUM_SIZE_BYTES = new SystemSetting<>(
            "storage.file.maximum-size-bytes",
            1,
            GROUP,
            "setting.storage.file.maximum-size-bytes",
            "setting.storage.file.maximum-size-bytes.description",
            "SCA4DJ_STORAGE_MAX_SIZE_BYTES",
            20L * 1024 * 1024,
            SettingCodec.positiveLong(),
            Pipeline.identity(Long.class),
            false,
            400);

    private FileSettings() {
    }
}
