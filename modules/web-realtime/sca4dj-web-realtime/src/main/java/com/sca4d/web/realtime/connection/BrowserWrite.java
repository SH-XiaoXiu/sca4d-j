package com.sca4d.web.realtime.connection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存发送给本机连接的浏览器信封以及发送后是否关闭连接。 */
@ApiStatus.Internal
public class BrowserWrite {

    private final @NotNull BrowserEnvelope envelope;

    private final boolean closeAfter;

    public BrowserWrite(@NotNull BrowserEnvelope envelope, boolean closeAfter) {
        this.envelope = Objects.requireNonNull(envelope, "浏览器信封不能为空");
        this.closeAfter = closeAfter;
    }

    /** @return 浏览器信封 */
    @NotNull
    public BrowserEnvelope envelope() {
        return envelope;
    }

    /** @return 写出完成后是否关闭连接 */
    public boolean closeAfter() {
        return closeAfter;
    }
}
