package com.sca4d.openapi.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 保存 OpenAPI 请求体，使 MAC 校验读取后 Spring 仍能绑定同一份请求体。 */
public final class OpenApiCachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte @NotNull [] body;

    /** 从原始请求读取并保存请求体。 */
    public OpenApiCachedBodyHttpServletRequest(@NotNull HttpServletRequest request) throws IOException {
        super(Objects.requireNonNull(request, "HTTP 请求不能为空"));
        this.body = request.getInputStream().readAllBytes();
    }

    /** @return 请求体副本 */
    public byte @NotNull [] body() {
        return body.clone();
    }

    /** 每次绑定都返回新的请求体流。 */
    @Override
    public @NotNull ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte @NotNull [] buffer, int offset, int length) {
                return input.read(buffer, offset, length);
            }

            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                Objects.requireNonNull(listener, "ReadListener 不能为空");
            }
        };
    }

    /** 按请求字符集返回新的 Reader。 */
    @Override
    public @NotNull BufferedReader getReader() {
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
