package com.sca4d.common.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sca4d.common.exception.ResponseCode;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

/**
 * 共用接口响应体。
 *
 * <p>Controller 默认返回该类型。业务失败同时返回固定 messageKey、模板参数和当前请求语言 message；成功响应不制造无意义消息。
 * HTTP 状态由成功响应工厂、全局异常处理或特殊 Controller 响应显式决定。
 *
 * @param <T> 响应数据类型
 */
public class ApiResponse<T> {

    private int code;
    private String messageKey;
    private List<Object> messageArgs = List.of();
    private String message;
    private T data;
    private String traceId;
    private long timestamp;
    @JsonIgnore
    private HttpStatus responseStatus;
    @JsonIgnore
    private HttpHeaders responseHeaders = new HttpHeaders();

    /** 创建供 Jackson 反序列化后通过 setter 恢复字段的空响应体。 */
    public ApiResponse() {
    }

    /**
     * 创建字段已经确定的共用接口响应体。
     *
     * @param code 固定业务响应码
     * @param messageKey 固定消息键；无消息的成功响应为空
     * @param messageArgs 消息模板参数
     * @param message 当前请求语言的安全展示消息；无消息的成功响应为空
     * @param data 可选响应数据
     * @param traceId 可选请求追踪标识
     * @param timestamp 响应创建时的 Unix 毫秒时间戳
     */
    public ApiResponse(
            int code,
            @Nullable String messageKey,
            @NotNull List<Object> messageArgs,
            @Nullable String message,
            @Nullable T data,
            @Nullable String traceId,
            long timestamp) {
        this.code = code;
        this.messageKey = messageKey;
        this.messageArgs = List.copyOf(messageArgs);
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> @NotNull ApiResponse<T> ok(@Nullable T data) {
        return new ApiResponse<>(
                ResponseCode.SUCCESS.getCode(),
                null,
                List.of(),
                null,
                data,
                currentTraceId(),
                System.currentTimeMillis());
    }

    /**
     * 创建无数据成功响应。
     *
     * @return 成功响应
     */
    public static @NotNull ApiResponse<Void> ok() {
        return ok(null);
    }

    /**
     * 创建携带当前资源强 ETag 的 200 成功响应。
     *
     * @param data 当前资源
     * @param version 当前资源非负乐观锁版本
     * @param <T> 资源类型
     * @return 200、ETag 和共用成功响应体
     */
    public static <T> @NotNull ApiResponse<T> ok(@Nullable T data, long version) {
        if (version < 0) {
            throw new IllegalArgumentException("资源版本不能为负数");
        }
        ApiResponse<T> response = ok(data);
        response.responseHeaders.setETag(ETag.create(Long.toString(version)).formattedTag());
        return response;
    }

    /**
     * 创建携带资源地址和初始强 ETag 的 201 成功响应。
     *
     * @param data 新资源
     * @param location 新资源地址
     * @param version 新资源非负乐观锁版本
     * @param <T> 资源类型
     * @return 201、显式 Location、ETag 和共用成功响应体
     */
    public static <T> @NotNull ApiResponse<T> created(
            @Nullable T data, @NotNull URI location, long version) {
        if (version < 0) {
            throw new IllegalArgumentException("资源版本不能为负数");
        }
        ApiResponse<T> response = created(data, version);
        response.responseHeaders.setLocation(location);
        return response;
    }

    /**
     * 创建携带资源地址但没有版本语义的 201 成功响应。
     *
     * @param data 新资源
     * @param location 新资源地址
     * @param <T> 资源类型
     * @return 201、显式 Location 和共用成功响应体
     */
    public static <T> @NotNull ApiResponse<T> created(
            @Nullable T data, @NotNull URI location) {
        ApiResponse<T> response = created(data);
        response.responseHeaders.setLocation(location);
        return response;
    }

    /** 创建不要求返回资源地址和版本的 201 成功响应。 */
    public static <T> @NotNull ApiResponse<T> created(@Nullable T data) {
        ApiResponse<T> response = ok(data);
        response.responseStatus = HttpStatus.CREATED;
        return response;
    }

    /** 创建携带初始强 ETag 但不要求返回资源地址的 201 成功响应。 */
    public static <T> @NotNull ApiResponse<T> created(@Nullable T data, long version) {
        ApiResponse<T> response = ok(data, version);
        response.responseStatus = HttpStatus.CREATED;
        return response;
    }

    /**
     * 创建携带已接受资源地址的 202 成功响应。
     *
     * @param data 已接受处理的资源
     * @param location 已接受处理的资源地址
     * @param <T> 资源类型
     * @return 202、显式 Location 和共用成功响应体
     */
    public static <T> @NotNull ApiResponse<T> accepted(
            @Nullable T data, @NotNull URI location) {
        ApiResponse<T> response = accepted(data);
        response.responseHeaders.setLocation(location);
        return response;
    }

    /** 创建不要求返回轮询地址的 202 成功响应。 */
    public static <T> @NotNull ApiResponse<T> accepted(@Nullable T data) {
        ApiResponse<T> response = ok(data);
        response.responseStatus = HttpStatus.ACCEPTED;
        return response;
    }

    /**
     * 创建失败响应。
     *
     * @param code 错误码
     * @param messageKey 固定消息键
     * @param messageArgs 消息模板参数
     * @param message 当前请求语言的安全展示消息
     * @param data 错误数据
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> @NotNull ApiResponse<T> fail(
            int code,
            @NotNull String messageKey,
            @NotNull List<Object> messageArgs,
            @NotNull String message,
            @Nullable T data) {
        return new ApiResponse<>(
                code, messageKey, messageArgs, message, data, currentTraceId(), System.currentTimeMillis());
    }

    /**
     * 创建具有明确 HTTP 分类的失败响应。
     *
     * @param code 固定业务响应码
     * @param messageKey 固定消息键
     * @param messageArgs 消息模板参数
     * @param message 当前请求语言的安全展示消息
     * @param data 错误数据
     * @param responseCode HTTP 响应分类
     * @param <T> 响应数据类型
     * @return 分类对应的 HTTP 状态和失败响应体
     */
    public static <T> @NotNull ResponseEntity<ApiResponse<T>> fail(
            int code,
            @NotNull String messageKey,
            @NotNull List<Object> messageArgs,
            @NotNull String message,
            @Nullable T data,
            @NotNull ResponseCode responseCode) {
        return ResponseEntity.status(responseCode.getHttpStatus())
                .body(fail(code, messageKey, messageArgs, message, data));
    }

    /**
     * 判断是否为成功响应。
     *
     * @return code 等于成功码时返回 true
     */
    public boolean success() {
        return code == ResponseCode.SUCCESS.getCode();
    }

    /** @return Controller 显式声明的 HTTP 状态；未声明时由 Spring 使用默认状态 */
    @JsonIgnore
    public @Nullable HttpStatus responseStatus() {
        return responseStatus;
    }

    /** @return Controller 显式声明且不进入 JSON 响应体的 HTTP Header */
    @JsonIgnore
    public @NotNull HttpHeaders responseHeaders() {
        return HttpHeaders.readOnlyHttpHeaders(responseHeaders);
    }

    /** 恢复幂等重放中保存的 HTTP 状态和 Header。 */
    public void restoreResponse(
            @Nullable HttpStatus status, @NotNull HttpHeaders headers) {
        responseStatus = status;
        responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
    }

    /**
     * 从当前请求读取响应可传播的 traceId。
     *
     * @return 当前请求 traceId；请求外创建响应时返回 {@code null}
     */
    private static @Nullable String currentTraceId() {
        CurrentRequest context = CurrentRequestHolder.currentOrNull();
        return context == null ? null : context.getTraceId();
    }

    /** @return 固定业务响应码 */
    public int getCode() {
        return code;
    }

    /** @param code 反序列化恢复的固定业务响应码 */
    public void setCode(int code) {
        this.code = code;
    }

    /** @return 固定消息键；无消息的成功响应为 {@code null} */
    public @Nullable String getMessageKey() {
        return messageKey;
    }

    /** @param messageKey 反序列化恢复的可选固定消息键 */
    public void setMessageKey(@Nullable String messageKey) {
        this.messageKey = messageKey;
    }

    /** @return 保持模板顺序的消息参数 */
    public @NotNull List<Object> getMessageArgs() {
        return messageArgs;
    }

    /** @param messageArgs 反序列化恢复的消息模板参数 */
    public void setMessageArgs(@NotNull List<Object> messageArgs) {
        this.messageArgs = List.copyOf(messageArgs);
    }

    /** @return 当前请求语言的安全展示消息；无消息的成功响应为 {@code null} */
    public @Nullable String getMessage() {
        return message;
    }

    /** @param message 反序列化恢复的可选展示消息 */
    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    /** @return 响应数据；无数据成功或失败响应可以为 {@code null} */
    public @Nullable T getData() {
        return data;
    }

    /** @param data 反序列化恢复的可选响应数据 */
    public void setData(@Nullable T data) {
        this.data = data;
    }

    /** @return 当前响应关联的请求 traceId；请求外创建时为 {@code null} */
    public @Nullable String getTraceId() {
        return traceId;
    }

    /** @param traceId 反序列化恢复的可选请求追踪标识 */
    public void setTraceId(@Nullable String traceId) {
        this.traceId = traceId;
    }

    /** @return 响应创建时的 Unix 毫秒时间戳 */
    public long getTimestamp() {
        return timestamp;
    }

    /** @param timestamp 反序列化恢复的 Unix 毫秒时间戳 */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
