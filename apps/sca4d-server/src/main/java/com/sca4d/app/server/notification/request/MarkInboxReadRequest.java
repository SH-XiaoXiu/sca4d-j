package com.sca4d.app.server.notification.request;

import jakarta.validation.constraints.PositiveOrZero;

/** 接收站内信已读写入使用的当前版本。 */
public final class MarkInboxReadRequest {
    @PositiveOrZero
    private long version;

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
