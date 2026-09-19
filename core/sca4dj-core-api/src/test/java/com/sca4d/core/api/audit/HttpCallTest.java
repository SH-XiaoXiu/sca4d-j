package com.sca4d.core.api.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 `HttpCall` 不保存查询参数、控制字符和超长 User-Agent。 */
class HttpCallTest {

    @Test
    @DisplayName("清理 HTTP 请求快照中的敏感查询参数和 User-Agent 控制字符")
    void sanitizesHttpSnapshot() {
        HttpCall call = new HttpCall(
                "POST",
                "/system/accounts?credential=secret",
                "127.0.0.1",
                "client\r\n\t agent");

        assertEquals("/system/accounts", call.path());
        assertEquals("client agent", call.userAgent().orElseThrow());
    }

    @Test
    @DisplayName("User-Agent 最多保留 512 个字符")
    void limitsUserAgentLength() {
        HttpCall call = new HttpCall("GET", "/system/audit/records", "::1", "a".repeat(600));

        assertEquals(HttpCall.USER_AGENT_MAX_LENGTH, call.userAgent().orElseThrow().length());
    }

    @Test
    @DisplayName("长度上限只剩一个字符时优先保留有效字符")
    void keepsLastVisibleCharacterAtLengthLimit() {
        HttpCall call = new HttpCall(
                "GET",
                "/system/audit/records",
                "::1",
                "a".repeat(511) + " \tb");

        assertEquals("a".repeat(511) + "b", call.userAgent().orElseThrow());
    }
}
