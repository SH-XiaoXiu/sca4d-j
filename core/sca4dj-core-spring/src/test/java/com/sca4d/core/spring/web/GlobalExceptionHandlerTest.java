package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.api.concurrent.exception.RateLimitExceededException;
import com.sca4d.core.api.concurrent.exception.VersionPreconditionFailedException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.spring.web.clientstate.ClientState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 验证 GlobalExceptionHandler 把业务异常、限流、版本前置条件失败和乐观锁冲突转换为 HTTP 状态与 ApiResponse，并按路由 produces 和 Accept 区分 JSON 错误体与只返回状态的响应（事件流、图片）。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(localizer());

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EventStreamController(), new BinaryController())
            .setControllerAdvice(handler)
            .build();

    @AfterEach
    void clearClientState() {
        ClientState.clear();
    }

    @Test
    @DisplayName("Keeps Business Error Code And Localized Message")
    void keepsBusinessErrorCodeAndLocalizedMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts");

        var response = handler.business(
                new BusinessException(CoreErrorCode.PERMISSION_DENIED),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CoreErrorCode.PERMISSION_DENIED.code());
        assertThat(response.getBody().getMessageKey())
                .isEqualTo(CoreErrorCode.PERMISSION_DENIED.messageKey());
        assertThat(response.getBody().getMessage()).isEqualTo("当前身份没有执行权限");
    }

    @Test
    @DisplayName("Returns Status Only For Event Stream Business Failure")
    void returnsStatusOnlyForEventStreamBusinessFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);

        var response = handler.business(
                new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Keeps JSON Body When Accept Header Also Contains Wildcard")
    void keepsJsonBodyWhenAcceptHeaderAlsoContainsWildcard() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/attempts");
        request.addHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*");

        var response = handler.business(
                new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("请先完成登录");
    }

    @Test
    @DisplayName("Keeps Json Body For Json Handler Accepting Event Stream As An Alternative")
    void keepsJsonBodyForJsonHandlerWithMixedAcceptHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resource");
        request.setAttribute(
                HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE,
                Set.of(MediaType.APPLICATION_JSON));
        request.addHeader(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.TEXT_EVENT_STREAM_VALUE);

        var response = handler.business(
                new BusinessException(CoreErrorCode.AUTHENTICATION_REQUIRED),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Returns Retry After And Stable Error For Rate Limited JSON Request")
    void returnsRetryAfterAndStableErrorForRateLimitedJsonRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/limited");

        var response = handler.rateLimited(
                new RateLimitExceededException(Duration.ofMillis(1501)),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CoreErrorCode.RATE_LIMITED.code());
        assertThat(response.getBody().getMessage()).isEqualTo("操作过于频繁");
    }

    @Test
    @DisplayName("Returns Retry After Without JSON Body For Rate Limited Event Stream")
    void returnsRetryAfterWithoutJsonBodyForRateLimitedEventStream() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);

        var response = handler.rateLimited(
                new RateLimitExceededException(Duration.ofSeconds(7)),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Returns Status Only When No Response Media Type Is Acceptable")
    void returnsStatusOnlyWhenNoResponseMediaTypeIsAcceptable() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/image");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_OCTET_STREAM_VALUE);

        var response = handler.notAcceptable(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Does Not Write Another Response After An Async Client Disconnects")
    void doesNotWriteAnotherResponseAfterAsyncClientDisconnects() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(
                () -> handler.disconnectedAsyncRequest(
                        new AsyncRequestNotUsableException("client disconnected"),
                        request))
                .doesNotThrowAnyException();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("Does Not Negotiate Json For Internal Event Stream Failure")
    void doesNotNegotiateJsonForInternalEventStreamFailure() throws Exception {
        mockMvc.perform(get("/test-events/failure").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Does Not Negotiate Json For Invalid Event Stream Request")
    void doesNotNegotiateJsonForInvalidEventStreamRequest() throws Exception {
        mockMvc.perform(get("/test-events/required").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Returns Business Status Without Media Negotiation Failure On An Image Only Route")
    void returnsBusinessStatusWithoutMediaNegotiationFailureOnImageOnlyRoute() throws Exception {
        // 路由只生成图片：即使 Accept 是通配，也不能写 JSON 错误体。
        mockMvc.perform(get("/test-binary/image-only").accept(MediaType.ALL))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
        mockMvc.perform(get("/test-binary/image-only").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Returns Structured Json Error On A Route Producing Both Image And Json")
    void returnsStructuredJsonErrorOnImageAndJsonRoute() throws Exception {
        mockMvc.perform(get("/test-binary/image-or-json").accept(MediaType.IMAGE_PNG, MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(CoreErrorCode.CONFLICT.code()))
                .andExpect(jsonPath("$.message").value("当前状态已经发生变化"));
    }

    @Test
    @DisplayName("Returns Status Only When Accept Excludes Json On An Image And Json Route")
    void returnsStatusOnlyWhenAcceptExcludesJsonOnImageAndJsonRoute() throws Exception {
        mockMvc.perform(get("/test-binary/image-or-json").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Maps Version Precondition Failure To Precondition Failed")
    void mapsVersionPreconditionFailureToPreconditionFailed() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/accounts/1");

        var response = handler.versionPreconditionFailed(
                new VersionPreconditionFailedException("account:1"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(ClientState.currentOrNull()).isNull();
    }

    @Test
    @DisplayName("Treats Internal Optimistic Conflict As Normal Business Conflict")
    void treatsInternalOptimisticConflictAsNormalBusinessConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/flows/1/events");

        var response = handler.business(new OptimisticLockException("flow:1"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ClientState.currentOrNull()).isNull();
    }

    /** 创建只包含 zh-CN 测试消息的 TextLocalizer，未登记的消息键返回 Optional.empty。 */
    private static TextLocalizer localizer() {
        Locale zhCn = Locale.forLanguageTag("zh-CN");
        Map<String, String> messages = Map.of(
                "error.core.permission-denied",
                "当前身份没有执行权限",
                "error.core.authentication-required",
                "请先完成登录",
                "error.core.rate-limited",
                "操作过于频繁",
                "error.core.conflict",
                "当前状态已经发生变化");
        return new TextLocalizer(
                new LocaleCatalog(List.of(zhCn), zhCn),
                (key, locale, arguments) -> Optional.ofNullable(messages.get(key))
                        .map(pattern -> new MessageFormat(pattern, locale).format(arguments)));
    }

    /** 提供真实 Spring MVC 图片映射，用于验证非 JSON 路由抛出业务异常时的媒体协商。 */
    @RestController
    private static class BinaryController {

        /** 只声明图片，并抛出冲突业务异常。 */
        @GetMapping(value = "/test-binary/image-only", produces = MediaType.IMAGE_PNG_VALUE)
        private byte[] imageOnly() {
            throw new BusinessException(CoreErrorCode.CONFLICT);
        }

        /** 声明图片和 JSON，并抛出冲突业务异常。 */
        @GetMapping(value = "/test-binary/image-or-json", produces = {MediaType.IMAGE_PNG_VALUE,
                MediaType.APPLICATION_JSON_VALUE})
        private byte[] imageOrJson() {
            throw new BusinessException(CoreErrorCode.CONFLICT);
        }
    }

    /** 提供真实 Spring MVC 事件流映射，用于验证异常响应媒体协商。 */
    @RestController
    private static class EventStreamController {

        /** 抛出建连前服务端异常。 */
        @GetMapping(value = "/test-events/failure", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        private SseEmitter failure() {
            throw new IllegalStateException("event stream failure");
        }

        /** 接收一个必需参数并建立测试事件流。 */
        @GetMapping(value = "/test-events/required", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        private SseEmitter required(@RequestParam String token) {
            Objects.requireNonNull(token, "测试 Token 不能为空");
            return new SseEmitter();
        }
    }
}
