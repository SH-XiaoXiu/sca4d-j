package com.sca4d.common.web;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.BizException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ETag;

import java.util.List;

/** 从 HTTP If-Match 前置条件读取资源乐观锁版本。 */
public final class IfMatchVersion {

    private IfMatchVersion() {
    }

    /**
     * 读取单个强 ETag 中的非负版本。
     *
     * @param ifMatch If-Match 请求头原值
     * @param requiredError 缺少前置条件时使用的业务错误码
     * @param changedError 弱标签、通配符、多标签或无效版本使用的业务错误码
     * @return Repository 原子更新条件使用的非负版本
     */
    public static long require(
            @Nullable String ifMatch,
            @NotNull BizErrorCode requiredError,
            @NotNull BizErrorCode changedError) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BizException(requiredError);
        }
        try {
            List<ETag> tags = ETag.parse(ifMatch);
            if (tags.size() != 1 || tags.getFirst().weak() || tags.getFirst().isWildcard()) {
                throw new BizException(changedError);
            }
            long version = Long.parseLong(tags.getFirst().tag());
            if (version < 0) {
                throw new NumberFormatException("negative version");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw new BizException(changedError);
        }
    }
}
