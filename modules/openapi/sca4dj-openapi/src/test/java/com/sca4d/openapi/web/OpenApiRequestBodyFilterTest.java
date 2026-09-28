package com.sca4d.openapi.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** 验证已知和未知长度的 OpenAPI 请求体都受实际字节上限约束。 */
class OpenApiRequestBodyFilterTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 32, OpenApiCachedBodyHttpServletRequest.MAX_BODY_BYTES})
    void preservesBodyWithinLimit(int size) throws Exception {
        GeneratedRequest request = new GeneratedRequest(-1, size);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        new OpenApiRequestBodyFilter().doFilter(request, response, (cached, result) -> {
            invoked.set(true);
            OpenApiCachedBodyHttpServletRequest body = (OpenApiCachedBodyHttpServletRequest) cached;
            byte[] bytes = body.body();
            assertThat(bytes).hasSize(size);
            assertThat(body.getInputStream().readAllBytes()).isEqualTo(bytes);
            assertThat(body.getInputStream().readAllBytes()).isEqualTo(bytes);
            if (size > 0) {
                bytes[0] = 0;
                assertThat(body.body()[0]).isEqualTo((byte) 'a');
            }
        });
        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 16})
    void stopsReadingUnknownOrUnderstatedBodyAtLimit(long declared) throws Exception {
        GeneratedRequest request = new GeneratedRequest(declared, Long.MAX_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        new OpenApiRequestBodyFilter().doFilter(request, response, (cached, result) -> invoked.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(invoked).isFalse();
        assertThat(request.read).isEqualTo(OpenApiCachedBodyHttpServletRequest.MAX_BODY_BYTES + 1L);
    }

    @ParameterizedTest
    @ValueSource(longs = {8388609, Long.MAX_VALUE})
    void rejectsDeclaredOversizeWithoutReading(long declared) throws Exception {
        GeneratedRequest request = new GeneratedRequest(declared, declared);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new OpenApiRequestBodyFilter().doFilter(request, response, (cached, result) -> {
            throw new AssertionError("超限请求不得进入认证或 Controller");
        });
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(request.read).isZero();
    }

    /** 按需产生请求字节，测试本身不分配一个与声明长度相同的数组。 */
    private static final class GeneratedRequest extends MockHttpServletRequest {
        private final long declared;

        private final long size;

        private long read;

        private GeneratedRequest(long declared, long size) {
            super("POST", "/openapi/devices");
            this.declared = declared;
            this.size = size;
        }

        @Override
        public long getContentLengthLong() {
            return declared;
        }

        @Override
        public @NotNull ServletInputStream getInputStream() {
            return new ServletInputStream() {
                @Override
                public int read() {
                    if (read == size) {
                        return -1;
                    }
                    read++;
                    return 'a';
                }

                @Override
                public int read(byte @NotNull [] bytes, int offset, int length) {
                    if (length == 0) {
                        return 0;
                    }
                    if (read == size) {
                        return -1;
                    }
                    int count = (int) Math.min(length, size - read);
                    Arrays.fill(bytes, offset, offset + count, (byte) 'a');
                    read += count;
                    return count;
                }

                @Override
                public boolean isFinished() {
                    return read == size;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
