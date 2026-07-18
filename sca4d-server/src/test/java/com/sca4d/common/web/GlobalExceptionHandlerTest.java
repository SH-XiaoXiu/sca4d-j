package com.sca4d.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.concurrent.idempotency.IdempotencyInProgressException;
import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.exception.ExternalServiceException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.iam.error.IamErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/** 验证业务、校验、权限和内部异常转换为共用 HTTP 响应。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(LocalizedMessagesFixture.create());
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo");

    @BeforeEach
    void useChineseMessages() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void bizExceptionShouldExposeSafeBusinessMessage() {
        var response = handler.handleBizException(
                new BizException(IamErrorCode.CREDENTIAL_ACCOUNT_TOO_LONG, 128),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessageKey()).isEqualTo("error.iam.credential-account-too-long");
        assertThat(response.getBody().getMessageArgs()).containsExactly(128);
        assertThat(response.getBody().getMessage()).isEqualTo("登录账号长度不能超过 128");
    }

    @Test
    void resourceAccessDenialShouldUseStableForbiddenResponse() {
        var response = handler.handleBizException(new ResourceAccessDeniedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
        assertThat(response.getBody().getMessageKey()).isEqualTo("error.common.forbidden");
        assertThat(response.getBody().getMessage()).isEqualTo("无权访问");
    }

    @Test
    void serviceExceptionShouldHideDetails() {
        var internal = handler.handleInternalServiceException(new InternalServiceException("数据库密码错误"), request)
                .getBody();
        var external = handler.handleExternalServiceException(new ExternalServiceException("pay", "签名失败"), request)
                .getBody();

        assertThat(internal).isNotNull();
        assertThat(internal.getMessage()).isEqualTo("内部服务异常，请稍后重试");
        assertThat(external).isNotNull();
        assertThat(external.getMessage()).isEqualTo("外部服务异常，请稍后重试");
    }

    @Test
    void validationShouldUseStableFallbackWhenConstraintMetadataIsUnavailable() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DemoRequest(), "demo");
        bindingResult.addError(new FieldError("demo", "name", "名称不能为空"));
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("demo", DemoRequest.class), 0);

        var response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, bindingResult), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessageKey()).isEqualTo("validation.invalid");
        assertThat(response.getBody().getMessage()).isEqualTo("参数 name 校验失败");
    }

    @Test
    void validationShouldFallbackToFieldMessageWhenTechnical() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DemoRequest(), "demo");
        bindingResult.addError(new FieldError("demo", "age", "Failed to convert property value"));
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("demo", DemoRequest.class), 0);

        var response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, bindingResult), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("参数 age 校验失败");
    }

    @Test
    void commonSpringExceptionsShouldMapToFriendlyMessages() throws Exception {
        assertThat(handler.handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("name", "String"), request)
                .getBody()
                .getMessage())
                .isEqualTo("参数 name 不能为空");

        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("demo", DemoRequest.class), 0);
        assertThat(handler.handleMethodArgumentTypeMismatch(
                new MethodArgumentTypeMismatchException("x", Integer.class, "age", parameter, null),
                request)
                .getBody()
                .getMessage())
                .isEqualTo("参数 age 类型错误");
    }

    @Test
    void responseStatusAndUnknownExceptionShouldBeHandled() {
        var notFound = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "secret-provider-detail"), request);
        var database = handler.handleDataAccess(new DataAccessResourceFailureException("connection refused"), request);
        var unknown = handler.handleException(new RuntimeException("secret"), request);

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNotNull();
        assertThat(notFound.getBody().getMessageKey()).isEqualTo("response.not-found");
        assertThat(notFound.getBody().getMessage()).doesNotContain("secret-provider-detail");
        assertThat(database.getBody()).isNotNull();
        assertThat(database.getBody().getMessage()).isEqualTo("内部服务异常，请稍后重试");
        assertThat(unknown.getBody()).isNotNull();
        assertThat(unknown.getBody().getMessage()).isEqualTo("服务器内部错误");
    }

    @Test
    void concurrencyConflictShouldMarkClientStateRefresh() {
        ClientState.clear();

        var response = handler.handleConcurrencyConflict(
                new ConcurrencyConflictException(
                        IamErrorCode.DEFAULT_PROFILE_CHANGED, ClientStateTarget.of("me-profile")),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(IamErrorCode.DEFAULT_PROFILE_CHANGED.getCode());
        assertThat(response.getBody().getMessageKey()).isEqualTo(IamErrorCode.DEFAULT_PROFILE_CHANGED.getMessageKey());
        assertThat(ClientState.currentOrNull()).isNotNull();
        assertThat(ClientState.currentOrNull().getRefreshTargets())
                .containsExactly(ClientStateTarget.of("me-profile"));

        ClientState.clear();
    }

    @Test
    void rateLimitShouldReturnRetryAfterHeader() {
        var response = handler.handleRateLimit(
                new RateLimitException(CommonErrorCode.TOO_MANY_REQUESTS, 3), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("3");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS.getCode());
    }

    @Test
    void idempotencyInProgressShouldReturnRetryAfterHeader() {
        var response = handler.handleIdempotencyInProgress(
                new IdempotencyInProgressException(Duration.ofSeconds(4)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("4");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS.getCode());
    }

    @Test
    void emptyConstraintViolationShouldUseLocalizedRequestMessageWithoutChineseArgument() {
        LocaleContextHolder.setLocale(Locale.US);

        var response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of()), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessageKey()).isEqualTo("validation.request-invalid");
        assertThat(response.getBody().getMessageArgs()).isEmpty();
        assertThat(response.getBody().getMessage()).isEqualTo("The request parameters are invalid");
    }

    /**
     * 为 Spring {@link MethodParameter} 提供含字段约束的方法元数据。
     *
     * @param request 只用于反射获取参数类型的测试请求
     */
    @SuppressWarnings("unused")
    private void demo(@NotNull DemoRequest request) {
    }

    /** 包含 Bean Validation 字段的最小异常处理请求。 */
    private static class DemoRequest {

        private String name;
    }

}
