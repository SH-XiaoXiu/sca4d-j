package com.sca4d.core.spring.web;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.spring.call.CallHolder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.http.CacheControl;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * HTTP 接口返回的固定字段响应。
 *
 * <p>JSON 响应体同时保留错误码、消息键、消息参数和当前语言文案。HTTP 状态与响应头由成功工厂方法明确设置，并由
 * {@link com.sca4d.core.spring.web.clientstate.ClientStateResponseAdvice} 在序列化前写入真实响应。
 *
 * @param <T> 响应数据类型
 */
public class ApiResponse<T> {

    /** 成功响应使用的固定响应码。 */
    public static final String SUCCESS_CODE = "0";

    private final Instant timestamp;

    private final List<Object> messageArgs;

    private final @Nullable String callId;

    private final String code;

    private final @Nullable String message;

    private final @Nullable String messageKey;

    private final @Nullable T data;

    private boolean dynamicMetadataVisible = true;

    private @Nullable HttpStatus responseStatus;

    private HttpHeaders responseHeaders;

    /**
     * 创建字段已经确定的 HTTP 响应。
     *
     * @param code 响应码
     * @param messageKey 固定消息键；成功响应没有消息时为空
     * @param messageArgs 消息模板参数
     * @param message 当前语言的安全展示文案；成功响应没有消息时为空
     * @param data 可选响应数据
     * @param callId 当前请求编号；请求外创建时为空
     * @param timestamp 响应创建时间
     */
    @JsonCreator
    public ApiResponse(
            @JsonProperty("code") String code,
            @JsonProperty("messageKey") @Nullable String messageKey,
            @JsonProperty("messageArgs") List<Object> messageArgs,
            @JsonProperty("message") @Nullable String message,
            @JsonProperty("data") @Nullable T data,
            @JsonProperty("callId") @Nullable String callId,
            @JsonProperty("timestamp") Instant timestamp) {
        if (code.isBlank()) {
            throw new IllegalArgumentException("响应码不能为空");
        }
        this.code = code;
        this.messageKey = messageKey;
        this.messageArgs = List.copyOf(messageArgs);
        this.message = message;
        this.data = data;
        this.callId = callId;
        this.timestamp = timestamp;
        this.responseHeaders = new HttpHeaders();
    }

    /**
     * 创建 200 成功响应。
     *
     * @param data 可选响应数据
     * @param <T> 响应数据类型
     * @return 200 成功响应
     */
    @Contract("_ -> new")
    public static <T> ApiResponse<T> ok(@Nullable T data) {
        return new ApiResponse<>(SUCCESS_CODE, null, List.of(), null, data, currentCallId(), Instant.now());
    }

    /**
     * 创建不包含数据的 200 成功响应。
     *
     * @return 不包含数据的 200 成功响应
     */
    @Contract("-> new")
    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    /**
     * 创建不包含响应体的 204 成功响应。
     *
     * @return 204 成功响应
     */
    @Contract("-> new")
    public static ApiResponse<Void> noContent() {
        ApiResponse<Void> response = ok(null);
        response.responseStatus = HttpStatus.NO_CONTENT;
        return response;
    }

    /**
     * 创建携带资源强 ETag、不包含响应体的 204 成功响应。
     *
     * @param version 响应资源当前版本
     * @return 携带强 ETag 的 204 成功响应
     */
    @Contract("_ -> new")
    public static ApiResponse<Void> noContent(Version version) {
        return withVersion(noContent(), version);
    }

    /**
     * 创建 201 成功响应。
     *
     * @param data 新建资源
     * @param <T> 响应数据类型
     * @return 201 成功响应
     */
    @Contract("_ -> new")
    public static <T> ApiResponse<T> created(@Nullable T data) {
        ApiResponse<T> response = ok(data);
        response.responseStatus = HttpStatus.CREATED;
        return response;
    }

    /**
     * 创建携带资源地址的 201 成功响应。
     *
     * @param data 新建资源
     * @param location 新建资源地址
     * @param <T> 响应数据类型
     * @return 携带 Location 的 201 成功响应
     */
    @Contract("_, _ -> new")
    public static <T> ApiResponse<T> created(@Nullable T data, URI location) {
        ApiResponse<T> response = created(data);
        response.responseHeaders.setLocation(location);
        return response;
    }

    /**
     * 创建 202 成功响应。
     *
     * @param data 已接受处理的资源
     * @param <T> 响应数据类型
     * @return 202 成功响应
     */
    @Contract("_ -> new")
    public static <T> ApiResponse<T> accepted(@Nullable T data) {
        ApiResponse<T> response = ok(data);
        response.responseStatus = HttpStatus.ACCEPTED;
        return response;
    }

    /**
     * 创建携带状态查询地址的 202 成功响应。
     *
     * @param data 已接受处理的资源
     * @param location 状态查询地址
     * @param <T> 响应数据类型
     * @return 携带 Location 的 202 成功响应
     */
    @Contract("_, _ -> new")
    public static <T> ApiResponse<T> accepted(@Nullable T data, URI location) {
        ApiResponse<T> response = accepted(data);
        response.responseHeaders.setLocation(location);
        return response;
    }

    /**
     * 创建失败响应体。
     *
     * @param errorCode 错误码
     * @param messageArgs 消息模板参数
     * @param message 当前语言的安全展示文案
     * @param data 可安全返回的错误详情
     * @param <T> 错误详情类型
     * @return 保留错误码和消息引用的失败响应
     */
    @Contract("_, _, _, _ -> new")
    public static <T> ApiResponse<T> failure(
            ErrorCode errorCode,
            List<Object> messageArgs,
            String message,
            @Nullable T data) {
        return failure(errorCode, errorCode.messageKey(), messageArgs, message, data);
    }

    /**
     * 创建使用具体消息键的失败响应体。
     *
     * <p>错误码表示固定失败类型，具体消息键可进一步说明缺少字段、类型错误等同类失败原因。
     *
     * @param errorCode 错误码
     * @param messageKey 当前失败使用的固定消息键
     * @param messageArgs 消息模板参数
     * @param message 当前语言的安全展示文案
     * @param data 可安全返回的错误详情
     * @param <T> 错误详情类型
     * @return 保留错误码和具体消息引用的失败响应
     */
    @Contract("_, _, _, _, _ -> new")
    public static <T> ApiResponse<T> failure(
            ErrorCode errorCode,
            String messageKey,
            List<Object> messageArgs,
            String message,
            @Nullable T data) {
        return new ApiResponse<>(
                errorCode.code(),
                messageKey,
                messageArgs,
                message,
                data,
                currentCallId(),
                Instant.now());
    }

    /**
     * 恢复幂等重放保存的 HTTP 状态和响应头。
     *
     * @param status 首次执行使用的 HTTP 状态；未显式指定时为空
     * @param headers 首次执行使用的响应头
     */
    @Contract(mutates = "this")
    public void restoreResponse(@Nullable HttpStatus status, HttpHeaders headers) {
        responseStatus = status;
        responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
    }

    /**
     * 判断响应是否成功。
     *
     * @return 响应码为 {@value #SUCCESS_CODE} 时返回 {@code true}
     */
    @Contract(pure = true)
    public boolean success() {
        return SUCCESS_CODE.equals(code);
    }

    /**
     * 显式 HTTP 状态。
     *
     * @return 显式 HTTP 状态；未指定时为空并由 Spring 使用默认状态
     */
    @JsonIgnore
    @Contract(pure = true)
    public @Nullable HttpStatus responseStatus() {
        return responseStatus;
    }

    /**
     * 返回额外 HTTP 响应头。
     *
     * @return 不进入 JSON 响应体的只读 HTTP 响应头
     */
    @JsonIgnore
    @Contract(pure = true)
    public HttpHeaders responseHeaders() {
        return HttpHeaders.readOnlyHttpHeaders(responseHeaders);
    }

    /**
     * 禁止浏览器和中间 HTTP 缓存保存当前响应。
     *
     * <p>响应包含随 Session、Principal 或权限变化，但没有包含在资源 Version 中的数据时使用。
     *
     * @return 当前响应，供 Controller 继续返回
     */
    @Contract(value = "-> this", mutates = "this")
    public @NotNull ApiResponse<T> noStore() {
        responseHeaders.setCacheControl(CacheControl.noStore());
        return this;
    }

    /**
     * 响应码。
     *
     * @return 响应码
     */
    public String getCode() {
        return code;
    }

    /**
     * 固定消息键。
     *
     * @return 固定消息键；成功响应没有消息时为空
     */
    public @Nullable String getMessageKey() {
        return messageKey;
    }

    /**
     * 消息模板参数。
     *
     * @return 保持模板顺序的消息参数
     */
    public @Unmodifiable List<Object> getMessageArgs() {
        return messageArgs;
    }

    /**
     * 当前语言的安全展示文案。
     *
     * @return 当前语言的安全展示文案；成功响应没有消息时为空
     */
    public @Nullable String getMessage() {
        return message;
    }

    /**
     * 响应数据。
     *
     * @return 响应数据；没有数据时为空
     */
    public @Nullable T getData() {
        return data;
    }

    /**
     * 返回当前请求编号。
     *
     * @return 当前请求编号；请求外创建或响应发布强 ETag 时为空
     */
    @JsonInclude(NON_NULL)
    public @Nullable String getCallId() {
        return dynamicMetadataVisible ? callId : null;
    }

    /**
     * 返回响应创建时间。
     *
     * @return 响应创建时间；响应发布强 ETag 时为空
     */
    @SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化固定响应字段。
    @JsonInclude(NON_NULL)
    public @Nullable Instant getTimestamp() {
        return dynamicMetadataVisible ? timestamp : null;
    }

    /** 让带强 ETag 的成功响应省略不属于资源表示的动态字段。 */
    void omitDynamicMetadata() {
        dynamicMetadataVisible = false;
    }

    private static @Nullable String currentCallId() {
        Call call = CallHolder.currentOrNull();
        return call == null ? null : call.callId();
    }

    /** 把资源当前版本写为强 ETag。 */
    private static <T> ApiResponse<T> withVersion(ApiResponse<T> response, Version version) {
        Version checked = Objects.requireNonNull(version, "响应资源版本不能为空");
        response.responseHeaders.setETag(ETag.create(checked.toString()).formattedTag());
        return response;
    }
}
