package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.web.annotation.ETag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;
import java.time.Instant;

/** 验证 `ApiResponseAdvice` 只把 `ApiResponse` 保存的 HTTP 元数据写入响应。 */
class ApiResponseAdviceTest {

    @AfterEach
    void clearCall() {
        CallHolder.clear();
    }

    @Test
    @DisplayName("Writes Api Response Status And Resource Headers")
    void writesApiResponseStatusAndResourceHeaders() throws Exception {
        ApiResponseAdvice advice = new ApiResponseAdvice();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        MethodParameter returnType = new MethodParameter(
                getClass().getDeclaredMethod("writesApiResponseStatusAndResourceHeaders"),
                -1);
        ApiResponse<VersionedBody> responseBody = ApiResponse.created(
                new VersionedBody(0),
                URI.create("/resources/1"));

        Object body = advice.beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest("POST", "/resources")),
                response);
        response.flush();

        assertThat(body).isSameAs(responseBody);
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(servletResponse.getHeader("Location")).isEqualTo("/resources/1");
        assertThat(servletResponse.getHeader("ETag")).isEqualTo("\"0\"");
    }

    @Test
    @DisplayName("自动发布 ETag 并让版本化正文保持稳定")
    void writesDeclaredEtagAndOmitsDynamicMetadata() throws Exception {
        CallHolder.set(new Call("original-call", Instant.parse("2026-09-15T00:00:00Z")));
        ApiResponse<VersionedBody> responseBody = ApiResponse.ok(new VersionedBody(7));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setHeader("X-Call-Id", "current-call");
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        MethodParameter returnType = new MethodParameter(
                getClass().getDeclaredMethod("writesDeclaredEtagAndOmitsDynamicMetadata"),
                -1);

        new ApiResponseAdvice().beforeBodyWrite(
                responseBody,
                returnType,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/resources/7")),
                response);
        response.flush();

        assertThat(servletResponse.getHeader("ETag")).isEqualTo("\"7\"");
        assertThat(servletResponse.getHeader("X-Call-Id")).isEqualTo("current-call");
        assertThat(responseBody.getCallId()).isNull();
        assertThat(responseBody.getTimestamp()).isNull();
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(responseBody))
                .doesNotContain("callId", "timestamp");
    }

    /** 提供测试响应的稳定版本。 */
    static final class VersionedBody {

        private final Version version;

        /** @param version 测试响应版本 */
        private VersionedBody(long version) {
            this.version = new Version(version);
        }

        /** @return 测试响应版本 */
        @ETag
        public Version revision() {
            return version;
        }

        /** @return 稳定测试字段 */
        public String getValue() {
            return "resource";
        }
    }
}
