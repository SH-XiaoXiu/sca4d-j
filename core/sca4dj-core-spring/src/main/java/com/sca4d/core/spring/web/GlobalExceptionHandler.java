package com.sca4d.core.spring.web;

import com.sca4d.core.api.concurrent.IdempotencyInProgressException;
import com.sca4d.core.api.concurrent.RateLimitExceededException;
import com.sca4d.core.api.concurrent.VersionPreconditionFailedException;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TextLocalizer;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 将业务异常和 Spring MVC 请求异常转换为携带错误码与消息参数的 {@link ApiResponse}。
 *
 * <p>业务异常保留模块声明的错误码和消息参数。未分类的服务端异常只向客户端返回固定安全文案，完整原因仅写入服务端日志。
 */
@RestControllerAdvice
@Hidden
public final class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final TextLocalizer localizer;

    /**
     * 创建使用 Spring 国际化消息源的异常处理器。
     *
     * @param localizer 已组合各模块语言目录的文本本地化入口
     */
    public GlobalExceptionHandler(TextLocalizer localizer) {
        this.localizer = Objects.requireNonNull(localizer, "TextLocalizer 不能为空");
    }

    /**
     * 处理调用方提交的版本前置条件失败。
     *
     * @param exception 调用方预期版本与当前版本不一致
     * @param request 当前 HTTP 请求
     * @return 412 HTTP 错误响应
     */
    @ExceptionHandler(VersionPreconditionFailedException.class)
    public ResponseEntity<ApiResponse<Object>> versionPreconditionFailed(
            VersionPreconditionFailedException exception,
            HttpServletRequest request) {
        LOG.trace("版本前置条件不满足 | request={} code={}", requestLine(request), exception.errorCode().code());
        List<Object> arguments = Arrays.asList(exception.messageArguments());
        return failure(
                ResponseEntity.status(HttpStatus.PRECONDITION_FAILED),
                exception.errorCode(),
                exception.errorCode().messageKey(),
                arguments,
                null,
                request);
    }

    /**
     * 处理 Core 或业务模块报告的预期失败。
     *
     * @param exception 携带模块错误码的业务异常
     * @param request 当前 HTTP 请求
     * @return 错误分类对应的 HTTP 响应；SSE 请求只返回状态码
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> business(
            BusinessException exception,
            HttpServletRequest request) {
        if (exception.errorCode().category() == ErrorCategory.INTERNAL
                || exception.errorCode().category() == ErrorCategory.UNAVAILABLE) {
            LOG.error(
                    "请求处理失败 | request={} code={} messageKey={}",
                    requestLine(request),
                    exception.errorCode().code(),
                    exception.errorCode().messageKey(),
                    exception);
        } else {
            LOG.trace(
                    "请求未通过 | request={} code={} messageKey={}",
                    requestLine(request),
                    exception.errorCode().code(),
                    exception.errorCode().messageKey());
        }
        HttpStatus status = status(exception.errorCode());
        return failure(
                ResponseEntity.status(status),
                exception.errorCode(),
                exception.errorCode().messageKey(),
                Arrays.asList(exception.messageArguments()),
                null,
                request);
    }

    /**
     * 处理令牌桶额度已经耗尽的调用。
     *
     * @param exception 携带剩余等待秒数的限流异常
     * @param request 当前 HTTP 请求
     * @return 429、`Retry-After` 和固定限流错误；SSE 请求不返回 JSON
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Object>> rateLimited(
            RateLimitExceededException exception,
            HttpServletRequest request) {
        LOG.trace(
                "请求超过限流额度 | request={} retryAfterSeconds={}",
                requestLine(request),
                exception.retryAfterSeconds());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
        List<Object> arguments = Arrays.asList(exception.messageArguments());
        return failure(
                response,
                exception.errorCode(),
                exception.errorCode().messageKey(),
                arguments,
                null,
                request);
    }

    /**
     * 处理仍由其他执行者负责的幂等请求。
     *
     * @param exception 携带建议等待秒数的幂等处理中异常
     * @param request 当前 HTTP 请求
     * @return 409、`Retry-After` 和固定幂等错误；SSE 请求不返回 JSON
     */
    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ApiResponse<Object>> idempotencyInProgress(
            IdempotencyInProgressException exception,
            HttpServletRequest request) {
        LOG.trace(
                "幂等请求仍在处理中 | request={} retryAfterSeconds={}",
                requestLine(request),
                exception.retryAfterSeconds());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
        List<Object> arguments = Arrays.asList(exception.messageArguments());
        return failure(
                response,
                exception.errorCode(),
                exception.errorCode().messageKey(),
                arguments,
                null,
                request);
    }

    /**
     * 处理请求体字段校验失败。
     *
     * @param exception 请求体字段校验异常
     * @param request 当前 HTTP 请求
     * @return 400 和全部字段拒绝原因
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldViolation>>> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::fieldViolation)
                .toList();
        LOG.trace("请求字段校验失败 | request={} fields={}", requestLine(request), fieldNames(violations));
        return failure(HttpErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, violations, request);
    }

    /**
     * 处理查询参数和表单字段绑定失败。
     *
     * @param exception 参数绑定异常
     * @param request 当前 HTTP 请求
     * @return 400 和全部字段拒绝原因
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<List<FieldViolation>>> binding(
            BindException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::fieldViolation)
                .toList();
        LOG.trace("请求参数绑定失败 | request={} fields={}", requestLine(request), fieldNames(violations));
        return failure(HttpErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, violations, request);
    }

    /**
     * 处理方法参数约束失败。
     *
     * @param exception 方法参数约束异常
     * @param request 当前 HTTP 请求
     * @return 400 和全部参数拒绝原因
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<FieldViolation>>> constraint(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations()
                .stream()
                .map(this::fieldViolation)
                .toList();
        LOG.trace("请求参数约束失败 | request={} fields={}", requestLine(request), fieldNames(violations));
        return failure(HttpErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, violations, request);
    }

    /**
     * 处理按 HTTP 输入数据类型执行的默认校验失败。
     *
     * @param exception 默认校验失败结果
     * @param request 当前 HTTP 请求
     * @return 400 和全部字段拒绝原因
     */
    @ExceptionHandler(HttpInputValidationException.class)
    public ResponseEntity<ApiResponse<List<FieldViolation>>> defaultValidation(
            HttpInputValidationException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.violations();
        LOG.trace("请求默认校验失败 | request={} fields={}", requestLine(request), fieldNames(violations));
        return failure(HttpErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, violations, request);
    }

    /**
     * 处理 Spring 方法校验失败。
     *
     * @param request 当前 HTTP 请求
     * @return 400 通用输入错误
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> methodValidation(HttpServletRequest request) {
        LOG.trace("请求参数校验失败 | request={}", requestLine(request));
        return failure(HttpErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, null, request);
    }

    /**
     * 处理缺少必需查询参数。
     *
     * @param exception 缺少查询参数异常
     * @param request 当前 HTTP 请求
     * @return 400 和缺少的参数名称
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> missingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        LOG.trace("请求缺少参数 | request={} parameter={}", requestLine(request), exception.getParameterName());
        return badRequest(
                "validation.required",
                List.of(exception.getParameterName()),
                request);
    }

    /**
     * 处理参数类型转换失败。
     *
     * @param exception 参数类型异常
     * @param request 当前 HTTP 请求
     * @return 400 和类型错误的参数名称
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> argumentType(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        LOG.trace("请求参数类型不正确 | request={} parameter={}", requestLine(request), exception.getName());
        return badRequest(
                "validation.type",
                List.of(exception.getName()),
                request);
    }

    /**
     * 处理无法解析的请求体。
     *
     * @param exception 请求体解析异常
     * @param request 当前 HTTP 请求
     * @return 400 安全错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        LOG.trace(
                "请求体格式不正确 | request={} failureType={}",
                requestLine(request),
                exception.getMostSpecificCause().getClass().getName());
        return badRequest(
                "validation.body-invalid",
                List.of(),
                request);
    }

    /**
     * 处理当前路由不支持的 HTTP 方法。
     *
     * @param exception HTTP 方法异常
     * @param request 当前 HTTP 请求
     * @return 405 HTTP 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        LOG.trace("请求方法不允许 | request={} method={}", requestLine(request), exception.getMethod());
        return failure(HttpErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED, null, request);
    }

    /**
     * 处理无法生成客户端接受格式的响应。
     *
     * @param request 当前 HTTP 请求
     * @return 不包含响应体的 406 HTTP 错误响应
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> notAcceptable(HttpServletRequest request) {
        LOG.trace("无法生成客户端接受的响应格式 | request={}", requestLine(request));
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    /**
     * 处理服务端不支持的请求媒体类型。
     *
     * @param exception 请求媒体类型异常
     * @param request 当前 HTTP 请求
     * @return 415 HTTP 错误响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        LOG.trace("请求媒体类型不支持 | request={} contentType={}", requestLine(request), exception.getContentType());
        return failure(HttpErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE, null, request);
    }

    /**
     * 处理不存在的 Controller 路由。
     *
     * @param request 当前 HTTP 请求
     * @return 404 HTTP 错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> noHandler(HttpServletRequest request) {
        LOG.trace("请求路由不存在 | request={}", requestLine(request));
        return failure(CoreErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, null, request);
    }

    /**
     * 处理不存在的静态资源。
     *
     * @param exception 静态资源不存在异常
     * @param request 当前 HTTP 请求
     * @return 404 HTTP 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> noResource(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        LOG.trace("静态资源不存在 | path={}", exception.getResourcePath());
        return failure(CoreErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, null, request);
    }

    /**
     * 处理超过服务端限制的请求体或上传文件。
     *
     * @param exception 上传大小异常
     * @param request 当前 HTTP 请求
     * @return 413 HTTP 错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> uploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        LOG.trace("上传内容超过限制 | request={} maxSize={}", requestLine(request), exception.getMaxUploadSize());
        return failure(HttpErrorCode.PAYLOAD_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, null, request);
    }

    /**
     * 处理业务代码显式声明的 HTTP 状态异常。
     *
     * @param exception Spring HTTP 状态异常
     * @param request 当前 HTTP 请求
     * @return 与异常状态一致的安全响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorCode errorCode = errorCode(status);
        if (status.is5xxServerError()) {
            LOG.error("请求返回服务端错误状态 | request={} status={}", requestLine(request), status, exception);
        } else {
            LOG.trace("请求返回指定状态 | request={} status={}", requestLine(request), status);
        }
        return failure(errorCode, status, null, request);
    }

    /**
     * 处理请求参数构造时报告的非法值。
     *
     * @param exception 非法参数异常
     * @param request 当前 HTTP 请求
     * @return 400 安全错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> illegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        LOG.trace(
                "请求参数不正确 | request={} failureType={}",
                requestLine(request),
                exception.getClass().getName());
        return failure(HttpErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, null, request);
    }

    /**
     * 结束客户端已经断开的异步响应，不再尝试向不可写的响应输出错误对象。
     *
     * @param exception Servlet 容器报告的异步响应不可写异常
     * @param request 当前 HTTP 请求
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void disconnectedAsyncRequest(
            AsyncRequestNotUsableException exception,
            HttpServletRequest request) {
        LOG.debug(
                "异步响应客户端已断开 | request={} reason={}",
                requestLine(request),
                exception.getMessage());
    }

    /**
     * 处理未被明确分类的服务端异常。
     *
     * @param exception 未处理异常
     * @param request 当前 HTTP 请求
     * @return 500 固定安全响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> internal(
            Exception exception,
            HttpServletRequest request) {
        LOG.error("请求处理出现未分类异常 | request={}", requestLine(request), exception);
        return failure(CoreErrorCode.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR, null, request);
    }

    /** 使用错误码默认消息创建状态响应。 */
    private <T> ResponseEntity<ApiResponse<T>> failure(
            ErrorCode errorCode,
            HttpStatus status,
            @Nullable T data,
            HttpServletRequest request) {
        return failure(
                ResponseEntity.status(status),
                errorCode,
                errorCode.messageKey(),
                List.of(),
                data,
                request);
    }

    /** 使用指定消息创建 400 响应。 */
    private ResponseEntity<ApiResponse<Void>> badRequest(
            String messageKey,
            List<Object> arguments,
            HttpServletRequest request) {
        return failure(
                ResponseEntity.status(HttpStatus.BAD_REQUEST),
                HttpErrorCode.BAD_REQUEST,
                messageKey,
                arguments,
                null,
                request);
    }

    /** 为事件流返回空错误响应，为普通 HTTP API 返回固定 JSON 错误。 */
    private <T> ResponseEntity<ApiResponse<T>> failure(
            ResponseEntity.BodyBuilder response,
            ErrorCode errorCode,
            String messageKey,
            List<Object> arguments,
            @Nullable T data,
            HttpServletRequest request) {
        if (expectsEventStream(request)) {
            return response.build();
        }
        String message = localize(messageKey, arguments);
        return response.body(ApiResponse.failure(errorCode, messageKey, arguments, message, data));
    }

    private FieldViolation fieldViolation(FieldError error) {
        if (error.contains(TypeMismatchException.class)) {
            return new FieldViolation(
                    error.getField(),
                    localize("validation.type", List.of(error.getField())));
        }
        String message = Objects.requireNonNullElseGet(
                error.getDefaultMessage(),
                () -> localize("validation.invalid", List.of(error.getField())));
        return new FieldViolation(error.getField(), message);
    }

    private FieldViolation fieldViolation(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        return new FieldViolation(field, violation.getMessage());
    }

    private String localize(String messageKey, List<Object> arguments) {
        return localizer.localize(
                MessageText.of(messageKey, arguments.toArray()),
                LocaleContextHolder.getLocale());
    }

    private HttpStatus status(ErrorCode errorCode) {
        if (errorCode instanceof HttpErrorCode httpErrorCode) {
            return httpErrorCode.httpStatus();
        }
        return switch (errorCode.category()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION -> HttpStatus.UNAUTHORIZED;
            case PERMISSION -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private ErrorCode errorCode(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> CoreErrorCode.AUTHENTICATION_REQUIRED;
            case FORBIDDEN -> CoreErrorCode.PERMISSION_DENIED;
            case NOT_FOUND -> CoreErrorCode.NOT_FOUND;
            case CONFLICT -> CoreErrorCode.CONFLICT;
            case TOO_MANY_REQUESTS -> CoreErrorCode.RATE_LIMITED;
            case SERVICE_UNAVAILABLE -> CoreErrorCode.UNAVAILABLE;
            case METHOD_NOT_ALLOWED -> HttpErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> HttpErrorCode.NOT_ACCEPTABLE;
            case UNSUPPORTED_MEDIA_TYPE -> HttpErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case PAYLOAD_TOO_LARGE -> HttpErrorCode.PAYLOAD_TOO_LARGE;
            case PRECONDITION_REQUIRED -> HttpErrorCode.PRECONDITION_REQUIRED;
            case PRECONDITION_FAILED -> HttpErrorCode.PRECONDITION_FAILED;
            default -> status.is5xxServerError() ? CoreErrorCode.INTERNAL : HttpErrorCode.BAD_REQUEST;
        };
    }

    /** 判断当前 Handler 或明确的 Accept 约束是否只生成 SSE。 */
    private boolean expectsEventStream(HttpServletRequest request) {
        Object producible = request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
        if (producible instanceof Collection<?> values && !values.isEmpty()) {
            return onlyEventStream(
                    values.stream()
                            .filter(MediaType.class::isInstance)
                            .map(MediaType.class::cast)
                            .toList());
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null) {
            return false;
        }
        try {
            return onlyEventStream(MediaType.parseMediaTypes(accept));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** 判断媒体类型集合是否明确包含事件流且不包含其他具体响应类型。 */
    private boolean onlyEventStream(List<MediaType> mediaTypes) {
        boolean eventStream = false;
        for (MediaType mediaType : mediaTypes) {
            if (mediaType.getQualityValue() <= 0 || mediaType.isWildcardType()) {
                continue;
            }
            if (!MediaType.TEXT_EVENT_STREAM.getType().equals(mediaType.getType())) {
                return false;
            }
            if (MediaType.TEXT_EVENT_STREAM.getSubtype().equals(mediaType.getSubtype())) {
                eventStream = true;
            } else if (!mediaType.isWildcardSubtype()) {
                return false;
            }
        }
        return eventStream;
    }

    private String requestLine(HttpServletRequest request) {
        String parameters = request.getParameterMap().keySet().toString();
        return request.getMethod() + " " + request.getRequestURI()
                + (parameters.equals("[]") ? "" : " parameters=" + parameters);
    }

    private List<String> fieldNames(List<FieldViolation> violations) {
        return violations.stream().map(FieldViolation::getField).toList();
    }
}
