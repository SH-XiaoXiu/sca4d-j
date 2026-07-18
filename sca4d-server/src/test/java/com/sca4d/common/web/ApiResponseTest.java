package com.sca4d.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.ResponseCode;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

/** 验证共用 HTTP 响应的成功、失败和消息字段。 */
class ApiResponseTest {

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void apiResponseShouldCarryTraceIdAndTimestamp() {
        CurrentRequestHolder.set(new CurrentRequest("trace-1", "GET", "/demo", "127.0.0.1", OffsetDateTime.now()));

        ApiResponse<String> response = ApiResponse.ok("ok");

        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getMessageKey()).isNull();
        assertThat(response.getMessageArgs()).isEmpty();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getTraceId()).isEqualTo("trace-1");
        assertThat(response.getTimestamp()).isPositive();
        assertThat(response.success()).isTrue();
    }

    @Test
    void apiResponseShouldCreateClassifiedFailureResponse() {
        var entity = ApiResponse.<Void>fail(
                ResponseCode.FORBIDDEN.getCode(),
                ResponseCode.FORBIDDEN.getMessageKey(),
                List.of(),
                "Access denied",
                null,
                ResponseCode.FORBIDDEN);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getCode()).isEqualTo(ResponseCode.FORBIDDEN.getCode());
    }

    @Test
    void apiResponseShouldOwnVersionedResourceHeaders() throws Exception {
        var current = ApiResponse.ok("resource", 7L);
        var created = ApiResponse.created("resource", URI.create("/resources/42"), 0L);
        var createdWithoutLocation = ApiResponse.created("resource", 0L);
        var unversioned = ApiResponse.created("resource", URI.create("/resources/43"));
        var accepted = ApiResponse.accepted("execution", URI.create("/resources/42/executions/9"));

        assertThat(current.responseStatus()).isNull();
        assertThat(current.responseHeaders().getETag()).isEqualTo("\"7\"");
        assertThat(created.responseStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.responseHeaders().getLocation()).isEqualTo(URI.create("/resources/42"));
        assertThat(created.responseHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(createdWithoutLocation.responseHeaders().getLocation()).isNull();
        assertThat(unversioned.responseStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(unversioned.responseHeaders().getLocation()).isEqualTo(URI.create("/resources/43"));
        assertThat(unversioned.responseHeaders().getETag()).isNull();
        assertThat(accepted.responseStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.responseHeaders().getLocation())
                .isEqualTo(URI.create("/resources/42/executions/9"));
        assertThat(new ObjectMapper().writeValueAsString(created))
                .doesNotContain("responseStatus", "responseHeaders", "Location", "ETag");
    }
}
