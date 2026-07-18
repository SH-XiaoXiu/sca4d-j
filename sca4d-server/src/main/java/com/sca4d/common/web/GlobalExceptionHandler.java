package com.sca4d.common.web;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.concurrent.idempotency.IdempotencyInProgressException;
import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.ExternalServiceException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.exception.ResponseCode;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.MessageReference;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理器。
 *
 * <p>共用把异常转换为 {@link ApiResponse}，并避免服务端异常细节泄露给前端。
 */
@RestControllerAdvice
@Hidden
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final LocalizedMessages messages;

    /**
     * 创建使用共用语言目录生成安全错误响应的异常处理器。
     *
     * @param messages 后端消息键本地化入口
     */
    public GlobalExceptionHandler(@NotNull LocalizedMessages messages) {
        this.messages = messages;
    }

    /**
     * 处理并发修改冲突。
     *
     * @param exception 并发冲突异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(ConcurrencyConflictException.class)
    public @NotNull ResponseEntity<ApiResponse<Object>> handleConcurrencyConflict(
            @NotNull ConcurrencyConflictException exception, @NotNull HttpServletRequest request) {
        if (exception.getRefreshTarget() == null) {
            ClientState.stale();
        } else {
            ClientState.refresh(exception.getRefreshTarget());
        }
        log.warn(
                "并发修改冲突，客户端应刷新后重试 | request={} code={} messageKey={}",
                requestLine(request),
                exception.getCode(),
                exception.getMessageKey());
        return failure(exception);
    }

    /**
     * 处理请求限流。
     *
     * @param exception 限流异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(RateLimitException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleRateLimit(
            @NotNull RateLimitException exception, @NotNull HttpServletRequest request) {
        log.warn("请求被限流，客户端应稍后重试 | request={} retryAfterSeconds={}",
                requestLine(request), exception.getRetryAfterSeconds());
        return ResponseEntity.status(exception.getResponseCode().getHttpStatus())
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .body(failureBody(exception));
    }

    /**
     * 处理幂等请求仍在执行中的冲突。
     *
     * @param exception 幂等处理中异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(IdempotencyInProgressException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleIdempotencyInProgress(
            @NotNull IdempotencyInProgressException exception, @NotNull HttpServletRequest request) {
        log.warn("重复请求仍在处理中，客户端应稍后查询 | request={} retryAfterSeconds={}",
                requestLine(request), exception.getRetryAfterSeconds());
        return ResponseEntity.status(exception.getResponseCode().getHttpStatus())
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .body(failureBody(exception));
    }

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(BizException.class)
    public @NotNull ResponseEntity<ApiResponse<Object>> handleBizException(
            @NotNull BizException exception, @NotNull HttpServletRequest request) {
        ResponseCode responseCode = exception.getResponseCode();
        if (responseCode.isServerError()) {
            // 业务异常如果被归类为服务端错误，说明它不应把原始消息暴露给前端。
            log.error(
                    "业务异常被归类为服务异常 | request={} code={} messageKey={}",
                    requestLine(request),
                    exception.getCode(),
                    exception.getMessageKey(),
                    exception);
            // 前端只收到响应分类对应的安全固定消息。
            return this.<Object>failure(responseCode);
        }
        // 4xx 业务异常属于可预期结果，只在需要定位请求决策时查看。
        log.debug(
                "业务请求未通过 | request={} code={} messageKey={}",
                requestLine(request),
                exception.getCode(),
                exception.getMessageKey());
        // EventSource 只接受 text/event-stream，认证或授权失败时没有可写出的 SSE 事件体。
        // 直接返回状态码，让客户端结束或按自身策略重建连接，避免 JSON 协商失败后再转发 /error。
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && MediaType.parseMediaTypes(accept).stream()
                .anyMatch(MediaType.TEXT_EVENT_STREAM::isCompatibleWith)) {
            return ResponseEntity.status(responseCode.getHttpStatus()).build();
        }
        return failure(exception);
    }

    /**
     * 处理内部服务异常。
     *
     * @param exception 内部服务异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(InternalServiceException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleInternalServiceException(
            @NotNull InternalServiceException exception, @NotNull HttpServletRequest request) {
        // 服务端日志保留原始异常便于诊断；统一响应只向客户端暴露固定错误码和文案。
        ResponseCode code = exception.getResponseCode();
        log.error("内部服务异常 | request={}", requestLine(request), exception);
        return this.<Void>failure(code);
    }

    /**
     * 处理外部服务异常。
     *
     * @param exception 外部服务异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(ExternalServiceException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleExternalServiceException(
            @NotNull ExternalServiceException exception, @NotNull HttpServletRequest request) {
        // 第三方响应可能包含签名或令牌，使用保留原始调用栈的安全异常摘要。
        ResponseCode code = exception.getResponseCode();
        log.error(
                "外部服务异常 | request={} provider={}",
                requestLine(request),
                exception.getProvider(),
                exception);
        return this.<Void>failure(code);
    }

    /**
     * 处理请求体参数校验异常。
     *
     * @param exception 校验异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            @NotNull MethodArgumentNotValidException exception, @NotNull HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        MessageReference message = validationMessage(fieldError);
        log.debug("参数校验失败 | request={} messageKey={}", requestLine(request), message.getKey());
        return this.<Void>failure(ResponseCode.BAD_REQUEST, message);
    }

    /**
     * 处理方法参数约束异常。
     *
     * @param exception 校验异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            @NotNull ConstraintViolationException exception, @NotNull HttpServletRequest request) {
        MessageReference message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(this::validationMessage)
                .orElseGet(() -> MessageReference.of("validation.request-invalid"));
        log.debug("参数校验失败 | request={} messageKey={}", requestLine(request), message.getKey());
        return this.<Void>failure(ResponseCode.BAD_REQUEST, message);
    }

    /**
     * 处理参数绑定异常。
     *
     * @param exception 绑定异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(BindException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleBindException(
            @NotNull BindException exception, @NotNull HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        MessageReference message = validationMessage(fieldError);
        log.debug("参数绑定失败 | request={} messageKey={}", requestLine(request), message.getKey());
        return this.<Void>failure(ResponseCode.BAD_REQUEST, message);
    }

    /**
     * 处理缺少请求参数异常。
     *
     * @param exception 缺参异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(
            @NotNull MissingServletRequestParameterException exception, @NotNull HttpServletRequest request) {
        MessageReference message = MessageReference.of("validation.required", exception.getParameterName());
        log.debug("参数缺失 | request={} parameter={}", requestLine(request), exception.getParameterName());
        return this.<Void>failure(ResponseCode.BAD_REQUEST, message);
    }

    /**
     * 处理参数类型错误。
     *
     * @param exception 类型错误异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            @NotNull MethodArgumentTypeMismatchException exception, @NotNull HttpServletRequest request) {
        MessageReference message = MessageReference.of("validation.type", exception.getName());
        log.debug("参数类型错误 | request={} parameter={}", requestLine(request), exception.getName());
        return this.<Void>failure(ResponseCode.BAD_REQUEST, message);
    }

    /**
     * 处理请求体解析失败。
     *
     * @param exception 请求体解析异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            @NotNull HttpMessageNotReadableException exception, @NotNull HttpServletRequest request) {
        log.debug(
                "请求体解析失败 | request={} failureType={}",
                requestLine(request),
                exception.getMostSpecificCause().getClass().getName());
        return this.<Void>failure(ResponseCode.BAD_REQUEST, MessageReference.of("validation.body-invalid"));
    }

    /**
     * 处理请求方法不允许。
     *
     * @param exception 请求方法异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupported(
            @NotNull HttpRequestMethodNotSupportedException exception, @NotNull HttpServletRequest request) {
        log.debug("请求方法不允许 | request={} method={}", requestLine(request), exception.getMethod());
        return this.<Void>failure(ResponseCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 处理媒体类型不支持。
     *
     * @param exception 媒体类型异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupported(
            @NotNull HttpMediaTypeNotSupportedException exception, @NotNull HttpServletRequest request) {
        log.debug("媒体类型不支持 | request={} contentType={}", requestLine(request), exception.getContentType());
        return this.<Void>failure(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * 处理 Controller 路由不存在。
     *
     * @param exception 路由异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(
            @NotNull NoHandlerFoundException exception, @NotNull HttpServletRequest request) {
        log.debug("路由不存在 | request={}", requestLine(request));
        return this.<Void>failure(ResponseCode.NOT_FOUND);
    }

    /**
     * 处理静态资源不存在。
     *
     * @param exception 资源异常
     * @return 共用错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            @NotNull NoResourceFoundException exception) {
        log.debug("静态资源不存在 | path={}", exception.getResourcePath());
        return this.<Void>failure(ResponseCode.NOT_FOUND);
    }

    /**
     * 处理上传大小超限。
     *
     * @param exception 上传异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            @NotNull MaxUploadSizeExceededException exception, @NotNull HttpServletRequest request) {
        log.debug("上传超限 | request={} maxSize={}", requestLine(request), exception.getMaxUploadSize());
        return this.<Void>failure(ResponseCode.PAYLOAD_TOO_LARGE);
    }

    /**
     * 处理 Spring 响应状态异常。
     *
     * @param exception 响应状态异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleResponseStatus(
            @NotNull ResponseStatusException exception, @NotNull HttpServletRequest request) {
        ResponseCode code = responseStatusCode(exception);
        log.debug("请求返回指定状态 | request={} status={}", requestLine(request), exception.getStatusCode());
        return this.<Void>failure(code);
    }

    /**
     * 处理数据库访问异常。
     *
     * @param exception 数据库异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleDataAccess(
            @NotNull DataAccessException exception, @NotNull HttpServletRequest request) {
        ResponseCode code = ResponseCode.INTERNAL_SERVICE_ERROR;
        log.error("数据库访问失败 | request={}", requestLine(request), exception);
        return this.<Void>failure(code);
    }

    /**
     * 处理非法参数异常。
     *
     * @param exception 非法参数异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            @NotNull IllegalArgumentException exception, @NotNull HttpServletRequest request) {
        log.debug("请求参数不合法 | request={} failureType={}",
                requestLine(request), exception.getClass().getName());
        return this.<Void>failure(ResponseCode.BAD_REQUEST);
    }

    /**
     * 处理未知异常。
     *
     * @param exception 异常
     * @param request 当前请求
     * @return 共用错误响应
     */
    @ExceptionHandler(Exception.class)
    public @NotNull ResponseEntity<ApiResponse<Void>> handleException(
            @NotNull Exception exception, @NotNull HttpServletRequest request) {
        ResponseCode code = ResponseCode.SERVER_ERROR;
        log.error("请求处理出现未处理异常 | request={}", requestLine(request), exception);
        return this.<Void>failure(code);
    }

    /**
     * 将 Spring 响应状态异常转换为共用响应码。
     *
     * @param exception Spring 响应状态异常
     * @return 对应的共用响应码
     */
    private @NotNull ResponseCode responseStatusCode(@NotNull ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        // Spring 的 ResponseStatusException 是 HTTP 含义，这里只做明确映射，不提供通用反查入口。
        return switch (status) {
            case 400 -> ResponseCode.BAD_REQUEST;
            case 401 -> ResponseCode.UNAUTHORIZED;
            case 403 -> ResponseCode.FORBIDDEN;
            case 404 -> ResponseCode.NOT_FOUND;
            case 405 -> ResponseCode.METHOD_NOT_ALLOWED;
            case 409 -> ResponseCode.CONFLICT;
            case 413 -> ResponseCode.PAYLOAD_TOO_LARGE;
            case 415 -> ResponseCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ResponseCode.TOO_MANY_REQUESTS;
            default -> status >= 500 ? ResponseCode.INTERNAL_SERVICE_ERROR : ResponseCode.BAD_REQUEST;
        };
    }

    /**
     * 构造不包含参数值的安全请求摘要。
     *
     * @param request 当前请求
     * @return 用于日志记录的方法、路径和参数名称
     */
    private @NotNull String requestLine(@NotNull HttpServletRequest request) {
        var parameterNames = request.getParameterMap().keySet();
        return request.getMethod() + " " + request.getRequestURI()
                + (parameterNames.isEmpty() ? "" : " queryParameters=" + parameterNames);
    }

    /**
     * 从字段校验错误中恢复固定消息引用。
     *
     * @param fieldError 首个字段错误
     * @return 不泄露 Java 和框架类型的消息键与模板参数
     */
    private @NotNull MessageReference validationMessage(@Nullable FieldError fieldError) {
        if (fieldError == null) {
            return MessageReference.of("validation.request-invalid");
        }
        String field = fieldError.getField();
        if (field.isBlank()) {
            return MessageReference.of("validation.request-invalid");
        }
        if (fieldError.contains(TypeMismatchException.class)) {
            return MessageReference.of("validation.type", field);
        }
        try {
            ConstraintViolation<?> violation = fieldError.unwrap(ConstraintViolation.class);
            return validationMessage(violation, field);
        } catch (IllegalArgumentException ignored) {
            return validationMessage(fieldError.getCode(), field, null);
        }
    }

    /**
     * 从方法参数约束错误中提取可安全展示的业务消息。
     *
     * @param violation 方法参数约束错误
     * @return 不泄露 Java 和框架类型的消息键与模板参数
     */
    private @NotNull MessageReference validationMessage(@NotNull ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        if (field.isBlank()) {
            return MessageReference.of("validation.request-invalid");
        }
        return validationMessage(violation, field);
    }

    /**
     * 从 Jakarta Validation 约束恢复显式消息键或共用约束消息。
     *
     * @param violation 已由校验框架产生的约束数据
     * @param field 对外展示的字段路径
     * @return 包含固定消息键和必要约束参数的消息引用
     */
    private @NotNull MessageReference validationMessage(
            @NotNull ConstraintViolation<?> violation, @NotNull String field) {
        String template = violation.getMessageTemplate();
        if (template != null && template.startsWith("{") && template.endsWith("}")) {
            String key = template.substring(1, template.length() - 1);
            if (!key.startsWith("jakarta.validation.") && !key.startsWith("javax.validation.")) {
                return MessageReference.of(key);
            }
        }
        String annotation = violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
        return validationMessage(annotation, field, violation);
    }

    /**
     * 把标准或项目约束类型映射为共用校验消息。
     *
     * <p>Size 读取框架约束属性形成精确范围，其余未显式声明消息键的约束使用固定的必填、格式或通用失败含义。
     *
     * @param constraintName Jakarta 或项目约束简单名称
     * @param field 对外展示的字段路径
     * @param violation 可选约束数据，用于读取 Size 属性
     * @return 可由共用语言目录解析的消息引用
     */
    private @NotNull MessageReference validationMessage(
            @Nullable String constraintName,
            @NotNull String field,
            @Nullable ConstraintViolation<?> violation) {
        if ("NotNull".equals(constraintName)
                || "NotBlank".equals(constraintName)
                || "NotEmpty".equals(constraintName)) {
            return MessageReference.of("validation.required", field);
        }
        if ("Size".equals(constraintName) && violation != null) {
            Object min = violation.getConstraintDescriptor().getAttributes().get("min");
            Object max = violation.getConstraintDescriptor().getAttributes().get("max");
            return MessageReference.of("validation.size", field, min, max);
        }
        if ("BizId".equals(constraintName)) {
            return MessageReference.of("validation.biz-id.invalid");
        }
        if ("SupportedLanguage".equals(constraintName)) {
            return MessageReference.of("validation.i18n.language-unsupported");
        }
        return MessageReference.of("validation.invalid", field);
    }

    /**
     * 把业务异常中的固定消息引用解析为当前请求语言响应。
     *
     * @param exception 已分类的业务异常
     * @param <T> 响应附加数据类型
     * @return 保留业务错误码、消息引用、附加数据和请求 traceId 的失败响应
     */
    @SuppressWarnings("unchecked")
    private <T> @NotNull ResponseEntity<ApiResponse<T>> failure(@NotNull BizException exception) {
        MessageReference reference = new MessageReference(
                exception.getMessageKey(), exception.getMessageArgs());
        return failure(exception.getCode(), reference, (T) exception.getData(), exception.getResponseCode());
    }

    /** 创建需要追加专用响应头的业务失败响应体。 */
    @SuppressWarnings("unchecked")
    private <T> @NotNull ApiResponse<T> failureBody(@NotNull BizException exception) {
        MessageReference reference = new MessageReference(
                exception.getMessageKey(), exception.getMessageArgs());
        List<Object> arguments = reference.getArguments();
        String message = messages.get(reference.getKey(), arguments.toArray());
        return ApiResponse.fail(
                exception.getCode(), reference.getKey(), arguments, message, (T) exception.getData());
    }

    /**
     * 按通用响应分类创建当前请求语言的安全失败响应。
     *
     * @param responseCode HTTP 和通用错误码分类
     * @param <T> 响应数据类型
     * @return 使用响应分类固定消息键的失败响应
     */
    private <T> @NotNull ResponseEntity<ApiResponse<T>> failure(@NotNull ResponseCode responseCode) {
        return failure(responseCode, MessageReference.of(responseCode.getMessageKey()));
    }

    /**
     * 按通用响应分类和明确消息引用创建当前请求语言的失败响应。
     *
     * @param responseCode HTTP 和通用错误码分类
     * @param reference 当前失败场景的消息引用
     * @param <T> 响应数据类型
     * @return 不包含附加数据的失败响应
     */
    private <T> @NotNull ResponseEntity<ApiResponse<T>> failure(
            @NotNull ResponseCode responseCode, @NotNull MessageReference reference) {
        return failure(responseCode.getCode(), reference, null, responseCode);
    }

    /**
     * 解析消息引用并创建共用失败响应。
     *
     * @param code 固定业务错误码
     * @param reference 当前失败场景的消息引用
     * @param data 可安全返回调用方的附加数据
     * @param <T> 响应附加数据类型
     * @return 同时包含固定消息引用和当前语言文本的失败响应
     */
    private <T> @NotNull ResponseEntity<ApiResponse<T>> failure(
            int code,
            @NotNull MessageReference reference,
            @Nullable T data,
            @NotNull ResponseCode responseCode) {
        List<Object> arguments = reference.getArguments();
        String message = messages.get(reference.getKey(), arguments.toArray());
        return ApiResponse.fail(
                code, reference.getKey(), arguments, message, data, responseCode);
    }
}
