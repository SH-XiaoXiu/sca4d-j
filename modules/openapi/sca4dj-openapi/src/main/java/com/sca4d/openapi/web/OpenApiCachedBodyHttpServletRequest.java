package com.sca4d.openapi.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 保存 OpenAPI 请求体，使 MAC 校验读取后 Spring 仍能绑定同一份请求体。 */
public class OpenApiCachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /** OpenAPI 签名校验允许缓存的请求体上限，单位为字节。 */
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private final byte @NotNull [] body;

    /**
     * 从原始请求读取并保存请求体，不依赖 Content-Length 判断实际长度。
     *
     * @throws MaxUploadSizeExceededException 实际请求体超过 8 MiB 时抛出，最多读取上限加一字节
     * @throws IOException 请求流读取失败时抛出
     */
    public OpenApiCachedBodyHttpServletRequest(@NotNull HttpServletRequest request) throws IOException {
        super(Objects.requireNonNull(request, "HTTP 请求不能为空"));
        this.body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BODY_BYTES);
        }
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
