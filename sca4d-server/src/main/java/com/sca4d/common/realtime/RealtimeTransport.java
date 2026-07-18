package com.sca4d.common.realtime;

/**
 * 实时消息投递通道。
 */
public enum RealtimeTransport {
    /** WebSocket/STOMP 通道。 */
    WEBSOCKET,
    /** Server-Sent Events 通道。 */
    SSE
}
