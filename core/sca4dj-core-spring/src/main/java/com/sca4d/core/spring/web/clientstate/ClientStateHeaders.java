package com.sca4d.core.spring.web.clientstate;

import org.springframework.http.HttpHeaders;

import java.util.stream.Collectors;

/** 将当前调用收集的 {@link ClientState} 写入 HTTP 响应头。 */
public class ClientStateHeaders {

    private ClientStateHeaders() {
    }

    /**
     * 把当前请求登记的客户端状态变化写入响应头。
     *
     * @param headers 尚未提交的 HTTP 响应头
     */
    public static void write(HttpHeaders headers) {
        ClientState.Change change = ClientState.currentOrNull();
        if (change == null) {
            return;
        }
        if (!change.targets().isEmpty()) {
            headers.set(
                    ClientState.REFRESH_HEADER,
                    change.targets()
                            .stream()
                            .map(ClientStateTarget::value)
                            .collect(Collectors.joining(",")));
        }
    }
}
