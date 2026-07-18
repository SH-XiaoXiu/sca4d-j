package com.sca4d.iam.contact;

import com.sca4d.entity.IamContactEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 管理 Subject 当前使用的 Contact 及其验证事实。 */
public interface ContactService {

    /**
     * 替换一种当前 Contact；值发生变化后新 Contact 保持未验证。
     *
     * @param subjectId Contact 所属 Subject
     * @param type EMAIL 或 PHONE
     * @param value 新值；为 {@code null} 或空白时删除当前值
     */
    void replace(@NotNull Long subjectId, @NotNull ContactType type, @Nullable String value);

    /**
     * 读取 Subject 当前指定类型的主要 Contact。
     *
     * @return 当前启用 Contact；尚未设置时为 {@code null}
     */
    @Nullable
    IamContactEntity current(@NotNull Long subjectId, @NotNull ContactType type);

    /** @return 首次确认后带 verifiedAt 的当前 Contact */
    @NotNull
    IamContactEntity verify(
            @NotNull Long subjectId,
            @NotNull Long contactId,
            @NotNull ContactType type,
            long expectedVersion);

    /** @return 与调用方持有版本完全匹配的当前已验证 Contact */
    @NotNull
    IamContactEntity requireVerified(
            @NotNull Long subjectId,
            @NotNull Long contactId,
            @NotNull ContactType type,
            long version);
}
