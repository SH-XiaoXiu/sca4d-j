package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.spring.call.CallHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** 验证 ApiResponse 的成功响应与 callId 来源、无 HTTP 请求时的构造、REST 状态码和资源响应头，以及幂等重放时恢复 HTTP 元数据。 */
class ApiResponseTest {

    @AfterEach
    void clearRequest() {
        CallHolder.clear();
    }

    @Test
    @DisplayName("Creates Success With Current Request Number")
    void createsSuccessWithCurrentRequestNumber() {
        CallHolder.set(new Call("call-1", Instant.parse("2026-07-20T00:00:00Z")));

        ApiResponse<String> response = ApiResponse.ok("result");

        assertThat(response.getCode()).isEqualTo(ApiResponse.SUCCESS_CODE);
        assertThat(response.getMessageKey()).isNull();
        assertThat(response.getMessageArgs()).isEmpty();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getData()).isEqualTo("result");
        assertThat(response.getCallId()).isEqualTo("call-1");
        assertThat(response.success()).isTrue();
    }

    @Test
    @DisplayName("Creates Response Outside Http Request")
    void createsResponseOutsideHttpRequest() {
        ApiResponse<Void> response = ApiResponse.ok();
        ApiResponse<Void> noContent = ApiResponse.noContent();

        assertThat(response.getCallId()).isNull();
        assertThat(response.success()).isTrue();
        assertThat(noContent.responseStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(noContent.getData()).isNull();
        assertThat(noContent.success()).isTrue();
    }

    @Test
    @DisplayName("Owns Rest Status And Resource Headers")
    void ownsRestStatusAndResourceHeaders() throws Exception {
        ApiResponse<String> current = ApiResponse.ok("resource");
        ApiResponse<String> created = ApiResponse.created(
                "resource",
                URI.create("/resources/42"));
        ApiResponse<String> accepted = ApiResponse.accepted(
                "execution",
                URI.create("/resources/42/executions/9"));
        ApiResponse<String> permissionSensitive = ApiResponse.ok("access").noStore();

        assertThat(current.responseStatus()).isNull();
        assertThat(current.responseHeaders().getETag()).isNull();
        assertThat(created.responseStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.responseHeaders().getLocation()).isEqualTo(URI.create("/resources/42"));
        assertThat(created.responseHeaders().getETag()).isNull();
        assertThat(accepted.responseStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.responseHeaders().getLocation())
                .isEqualTo(URI.create("/resources/42/executions/9"));
        assertThat(permissionSensitive.responseHeaders().getETag()).isNull();
        assertThat(permissionSensitive.responseHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(created))
                .doesNotContain("responseStatus", "responseHeaders", "Location", "ETag");
    }

    @Test
    @DisplayName("Restores Http Metadata For Idempotent Replay")
    void restoresHttpMetadataForIdempotentReplay() {
        ApiResponse<Void> response = ApiResponse.failure(
                CoreErrorCode.CONFLICT,
                List.of("account"),
                "当前状态已经发生变化",
                null);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("/operations/1"));

        response.restoreResponse(HttpStatus.CONFLICT, headers);
        headers.clear();

        assertThat(response.responseStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.responseHeaders().getLocation()).isEqualTo(URI.create("/operations/1"));
        assertThat(response.getMessageKey()).isEqualTo(CoreErrorCode.CONFLICT.messageKey());
        assertThat(response.getMessageArgs()).containsExactly("account");
    }
}
