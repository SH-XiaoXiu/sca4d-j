package com.sca4d.schedule.notification;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** notification.sse 持久化的明确 Subject 与 DEFAULT Principal 接收人快照。 */
public class NotificationScheduleRecipient {

    private Long subjectId;
    private PrincipalType principalType;
    private Long principalId;

    /** 供 Jackson 按持久化 JSON 恢复字段。 */
    public NotificationScheduleRecipient() {
    }

    /**
     * 固化一个 Subject 的 DEFAULT Principal 接收人快照。
     *
     * @param subjectId 接收人所属 Subject 主键
     * @param principalId 接收人 DEFAULT Principal 主键
     * @return 接收人快照
     */
    public static @NotNull NotificationScheduleRecipient defaultPrincipal(
            @NotNull Long subjectId, @NotNull Long principalId) {
        NotificationScheduleRecipient recipient = new NotificationScheduleRecipient();
        recipient.subjectId = subjectId;
        recipient.principalType = PrincipalType.DEFAULT;
        recipient.principalId = principalId;
        return recipient;
    }

    /** @return 保存时明确选择的 Subject 主键 */
    public @Nullable Long getSubjectId() {
        return subjectId;
    }

    /** @param subjectId 持久化 JSON 中的 Subject 主键 */
    public void setSubjectId(@NotNull Long subjectId) {
        this.subjectId = subjectId;
    }

    /** @return 固定为 DEFAULT 的 Principal 类型 */
    public @Nullable PrincipalType getPrincipalType() {
        return principalType;
    }

    /** @param principalType 持久化 JSON 中的 Principal 类型 */
    public void setPrincipalType(@NotNull PrincipalType principalType) {
        this.principalType = principalType;
    }

    /** @return 已显式保存的 DEFAULT Principal 主键 */
    public @Nullable Long getPrincipalId() {
        return principalId;
    }

    /** @param principalId 持久化 JSON 中的 Principal 主键 */
    public void setPrincipalId(@NotNull Long principalId) {
        this.principalId = principalId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NotificationScheduleRecipient recipient)) {
            return false;
        }
        return Objects.equals(subjectId, recipient.subjectId)
                && principalType == recipient.principalType
                && Objects.equals(principalId, recipient.principalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, principalType, principalId);
    }
}
