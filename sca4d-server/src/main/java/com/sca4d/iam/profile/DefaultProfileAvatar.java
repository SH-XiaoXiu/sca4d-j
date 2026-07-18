package com.sca4d.iam.profile;

import com.sca4d.common.exception.BizException;
import com.sca4d.file.error.FileErrorCode;
import com.sca4d.iam.error.IamErrorCode;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** DEFAULT Profile 头像文件的场景和格式约束。 */
public final class DefaultProfileAvatar {

    /** DEFAULT Profile 头像在文件签名入口使用的场景。 */
    public static final String FILE_SCENE = "iam.default-profile.avatar";

    /** DEFAULT Profile 头像允许的最大字节数。 */
    public static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private DefaultProfileAvatar() {
    }

    /**
     * 校验待绑定文件是否可以作为 DEFAULT Profile 头像。
     *
     * @param contentType 文件保存时记录的 MIME type
     * @param sizeBytes 文件保存时记录的实际字节数
     * @throws BizException 文件不是 JPEG、PNG、WebP 或超过 5 MiB 时抛出
     */
    public static void requireSupportedFile(@Nullable String contentType, long sizeBytes) {
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new BizException(IamErrorCode.DEFAULT_PROFILE_AVATAR_FILE_TYPE_INVALID);
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new BizException(FileErrorCode.FILE_TOO_LARGE);
        }
    }
}
