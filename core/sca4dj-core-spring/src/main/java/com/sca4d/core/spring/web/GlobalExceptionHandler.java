package com.sca4d.core.spring.web;

import com.sca4d.core.api.concurrent.exception.IdempotencyInProgressException;
import com.sca4d.core.api.concurrent.exception.RateLimitExceededException;
import com.sca4d.core.api.concurrent.exception.VersionPreconditionFailedException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.api.version.exception.VersionCompatibilityException;
import com.sca4d.core.spring.web.exception.HttpInputValidationException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 将业务异常和 Spring MVC 请求异常转换为携带错误码与消息参数的 {@link ApiResponse}。
 *
 * <p>业务异常保留模块声明的错误码和消息参数。未分类的服务端异常只向客户端返回固定安全文案，完整原因仅写入服务端日志。
 *
 * <p>只有当前路由声明的 produces 和请求 Accept 头都允许 JSON 时才写 ApiResponse 错误体。只生成事件流、图片或文件的路由
 * 以及 Accept 不含 JSON 的请求，只得到原来的 HTTP 状态和响应头；声明了图片和 JSON 两种类型的路由，
 * 在请求同时接受 JSON 时仍返回 ApiResponse 错误体。
 */
@RestControllerAdvice
@Hidden
public class GlobalExceptionHandler {

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
     * 把 Call 中记录的版本兼容拒绝转换为 ApiResponse 错误，HTTP 状态取自错误码。
     * 错误消息按当前请求的 Locale 生成；路由或 Accept 不接受 JSON 时只返回状态码，不返回 JSON 错误体。
     *
     * @param exception 版本兼容拒绝异常，携带错误码和判定结果
     * @param request 当前 HTTP 请求
     * @return 版本兼容拒绝对应的 HTTP 错误响应
     */
    @ExceptionHandler(VersionCompatibilityException.class)
    public ResponseEntity<ApiResponse<Object>> clientVersion(
            VersionCompatibilityException exception,
            HttpServletRequest request) {
        return failure(
                ResponseEntity.status(status(exception.errorCode())),
                exception.errorCode(),
                exception.errorCode().messageKey(),
                List.of(),
                exception.decision(),
                request);
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
     * @return 错误分类对应的 HTTP 响应；不接受 JSON 的请求只返回状态码
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
     * @return 429、`Retry-After` 和固定限流错误；不接受 JSON 的请求不返回 JSON 错误体
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
     * @return 409、`Retry-After` 和固定幂等错误；不接受 JSON 的请求不返回 JSON 错误体
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
        LOG.warn(
                "请求参数不正确 | request={} failureType={} reason={}",
                requestLine(request),
                exception.getClass().getName(),
                exception.getMessage());
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

    /**
     * 路由和 Accept 都允许 JSON 时返回固定 JSON 错误；否则（事件流、图片、文件等）只返回已设置的状态和响应头，
     * 不再写响应体，避免 Spring 因找不到可接受的表示而在异常处理中再抛 {@link HttpMediaTypeNotAcceptableException}。
     */
    private <T> ResponseEntity<ApiResponse<T>> failure(
            ResponseEntity.BodyBuilder response,
            ErrorCode errorCode,
            String messageKey,
            List<Object> arguments,
            @Nullable T data,
            HttpServletRequest request) {
        if (!acceptsJson(request)) {
            LOG.trace("当前路由或 Accept 不接受 JSON，错误只返回状态和响应头 | request={}", requestLine(request));
            return response.build();
        }
        String message = localize(messageKey, arguments);
        return response.body(ApiResponse.failure(errorCode, messageKey, arguments, message, data));
    }

    /** 把 Spring 字段绑定错误转换为字段拒绝原因；类型转换失败使用 validation.type 消息，其余无默认消息时使用 validation.invalid。 */
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

    /** 把 Bean Validation 约束违规转换为字段拒绝原因，字段名取自属性路径，消息直接使用约束默认消息。 */
    private FieldViolation fieldViolation(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        return new FieldViolation(field, violation.getMessage());
    }

    /** 按当前请求的 Locale 解析 messageKey 对应的消息文本，arguments 按顺序替换占位符。 */
    private String localize(String messageKey, List<Object> arguments) {
        return localizer.localize(
                MessageText.of(messageKey, arguments.toArray()),
                LocaleContextHolder.getLocale());
    }

    /** 返回错误码对应的 HTTP 状态：HttpErrorCode 使用自身声明的状态，其他错误码按错误分类映射。 */
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

    /** 为 ResponseStatusException 的状态选择固定错误码；未列出的 4xx 使用 BAD_REQUEST，5xx 使用 INTERNAL。 */
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

    /**
     * 判断当前路由和 Accept 头是否都允许返回 JSON 错误体。
     *
     * <p>判定表（“路由”指被选中 Handler 声明的 produces，没有声明表示不限制）：
     *
     * <pre>
     * 路由 produces                 Accept                                  结果
     * 未声明，或含 JSON 兼容类型    缺失、无法解析，或含 q&gt;0 的 JSON 兼容范围  返回 JSON 错误体
     * 只有图片、文件、事件流等类型  任意                                      只返回状态和响应头
     * 任意                          只有 JSON 不兼容的范围（如 image/png）    只返回状态和响应头
     * </pre>
     */
    private boolean acceptsJson(HttpServletRequest request) {
        List<MediaType> producible = producibleMediaTypes(request);
        if (!producible.isEmpty()
                && producible.stream().noneMatch(type -> type.isCompatibleWith(MediaType.APPLICATION_JSON))) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(accept)
                    .stream()
                    .anyMatch(
                            range -> range.getQualityValue() > 0
                                    && range.isCompatibleWith(MediaType.APPLICATION_JSON));
        } catch (IllegalArgumentException ignored) {
            // Accept 头格式无法解析时按接受 JSON 处理，继续返回 JSON 错误体。
            return true;
        }
    }

    /**
     * 返回被选中 Handler 声明的 produces；没有声明或无法确定时返回空列表。
     *
     * <p>Spring MVC 在调用 {@code @ExceptionHandler} 之前会移除请求上的 {@code PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE}，
     * 因此优先读取该属性（直接调用本类方法或 Spring 未移除时存在），否则读取最佳匹配 Handler 方法上的
     * {@link RequestMapping#produces()}，方法上没有声明时读取类上的声明，与 Spring 合并映射条件的规则一致。
     * 以 {@code !} 开头的排除项和无法解析的值被忽略。
     */
    private List<MediaType> producibleMediaTypes(HttpServletRequest request) {
        Object producible = request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
        if (producible instanceof Collection<?> values && !values.isEmpty()) {
            return values.stream()
                    .filter(MediaType.class::isInstance)
                    .map(MediaType.class::cast)
                    .toList();
        }
        if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler)) {
            return List.of();
        }
        RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(
                handler.getMethod(),
                RequestMapping.class);
        String[] produces = methodMapping == null ? new String[0] : methodMapping.produces();
        if (produces.length == 0) {
            RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getBeanType(),
                    RequestMapping.class);
            produces = typeMapping == null ? new String[0] : typeMapping.produces();
        }
        List<MediaType> result = new ArrayList<>();
        for (String value : produces) {
            if (value.startsWith("!")) {
                continue;
            }
            try {
                result.add(MediaType.parseMediaType(value));
            } catch (InvalidMediaTypeException ignored) {
                // 含占位符等无法在此解析的声明不参与判断，按未声明处理。
            }
        }
        return result;
    }

    /** 生成日志用的请求行，包含方法、URI 和请求参数名称；参数值不写入日志。 */
    private String requestLine(HttpServletRequest request) {
        String parameters = request.getParameterMap().keySet().toString();
        return request.getMethod() + " " + request.getRequestURI()
                + (parameters.equals("[]") ? "" : " parameters=" + parameters);
    }

    private List<String> fieldNames(List<FieldViolation> violations) {
        return violations.stream().map(FieldViolation::getField).toList();
    }
}
