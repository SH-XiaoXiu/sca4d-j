package com.sca4d.notification.repository;

/** 批量未读数量查询结果。 */
public class NotificationUnreadCountResult {

    private int targetIndex;
    private long unreadCount;

    /** @return 输入接收人列表中的固定下标 */
    public int getTargetIndex() {
        return targetIndex;
    }

    /** @param targetIndex 输入接收人列表中的固定下标 */
    public void setTargetIndex(int targetIndex) {
        this.targetIndex = targetIndex;
    }

    /** @return 该接收目标当前未读通知数量 */
    public long getUnreadCount() {
        return unreadCount;
    }

    /** @param unreadCount 该接收目标当前未读通知数量 */
    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
