package com.sca4d.iot.device;

import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.api.query.SortOrder;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.ProductBinding;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 描述 Device 台账支持的有界标识查询或分页列表查询。 */
public class DeviceQuery {

    private static final @NotNull @Unmodifiable Set<@NotNull Sort> SORT_FIELDS = Set.of(
            Sort.CREATED_AT,
            Sort.UPDATED_AT,
            Sort.NAME,
            Sort.SERIAL);

    private final @Nullable Boolean directConnection;

    private final @Nullable @Unmodifiable List<@NotNull Long> deviceIds;

    private final @NotNull ListRequest request;

    private final @Nullable ProductBinding product;

    private final @Nullable String protocolCode;

    private final @Nullable String search;

    /** 保存 Builder 已校验的 Device 查询条件。 */
    private DeviceQuery(@NotNull Builder builder) {
        this.deviceIds = builder.deviceIds;
        this.request = builder.request;
        this.product = builder.product;
        this.search = builder.search;
        this.protocolCode = builder.protocolCode;
        this.directConnection = builder.directConnection;
    }

    /**
     * 声明一次有界 Device 标识查询。
     *
     * <p>结果页固定为第一页，页大小等于去重后的输入数量；空输入使用页大小一并返回空结果。
     *
     * @param deviceIds 待读取的正数 Device 标识；重复值只保留第一次出现的位置
     * @return 查询 Builder
     */
    @Contract(value = "_ -> new", pure = true)
    @CheckReturnValue
    public static @NotNull Builder forIds(@NotNull Collection<@NotNull Long> deviceIds) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long deviceId : Objects.requireNonNull(deviceIds, "Device 标识集合不能为空")) {
            long checked = Objects.requireNonNull(deviceId, "Device 标识不能为空");
            if (checked < 1) {
                throw new IllegalArgumentException("Device 标识必须为正数");
            }
            unique.add(checked);
        }
        List<Long> ids = List.copyOf(unique);
        ListRequest request = new ListRequest(
                new PageRequest(1, Math.max(1, ids.size())),
                List.of(Sort.SERIAL.asc()));
        return new Builder(ids, request);
    }

    /**
     * 声明一次分页 Device 列表查询。
     *
     * @param request 页大小不超过 100，排序字段只能使用创建时间、修改时间、ASCII 名称或稳定序号
     * @return 查询 Builder
     */
    @Contract(value = "_ -> new", pure = true)
    @CheckReturnValue
    public static @NotNull Builder list(@NotNull ListRequest request) {
        ListRequest checked = Objects.requireNonNull(request, "Device 列表要求不能为空");
        PageRequest page = checked.getPage().verifyMaxSize(100);
        for (SortOrder order : checked.getSort()) {
            if (!SORT_FIELDS.contains(order.getField())) {
                throw new IllegalArgumentException("Device 列表包含不支持的排序字段: " + order.getField());
            }
        }
        return new Builder(null, new ListRequest(page, checked.getSort()));
    }

    /** @return 有界 Device 标识；分页列表查询时为空 */
    @Contract(pure = true)
    @Nullable
    @Unmodifiable
    List<@NotNull Long> deviceIds() {
        return deviceIds;
    }

    /** @return 分页和排序要求 */
    @Contract(pure = true)
    @NotNull
    ListRequest request() {
        return request;
    }

    /** @return Product 条件；不限制 Product 时为空 */
    @Contract(pure = true)
    @Nullable
    ProductBinding product() {
        return product;
    }

    /** @return 匹配 Device code、序列号或展示名称的搜索词；不搜索时为空 */
    @Contract(pure = true)
    @Nullable
    String search() {
        return search;
    }

    /** @return 协议 code 条件；不限制协议时为空 */
    @Contract(pure = true)
    @Nullable
    String protocolCode() {
        return protocolCode;
    }

    /** @return 是否要求直接连接；不限制连接方式时为空 */
    @Contract(pure = true)
    @Nullable
    Boolean directConnection() {
        return directConnection;
    }

    /** 逐项声明 Device 台账筛选条件。 */
    public static class Builder {

        private final @Nullable @Unmodifiable List<@NotNull Long> deviceIds;

        private final @NotNull ListRequest request;

        private @Nullable ProductBinding product;

        private @Nullable String search;

        private @Nullable String protocolCode;

        private @Nullable Boolean directConnection;

        /** 创建已经确定有界或分页方式的 Builder。 */
        private Builder(
                @Nullable List<@NotNull Long> deviceIds,
                @NotNull ListRequest request) {
            this.deviceIds = deviceIds;
            this.request = request;
        }

        /** @param product Product 来源、code 和可选数据库标识 @return 当前 Builder */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder product(@NotNull ProductBinding product) {
            this.product = Objects.requireNonNull(product, "Device Product 条件不能为空");
            return this;
        }

        /** @param value 匹配 Device code、序列号或展示名称的非空白文本 @return 当前 Builder */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder search(@NotNull String value) {
            this.search = TextUtils.requireText(value, "Device 搜索词");
            return this;
        }

        /** @param value 待匹配的非空白设备协议 code @return 当前 Builder */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder protocol(@NotNull String value) {
            this.protocolCode = TextUtils.requireText(value, "Device 协议 code");
            return this;
        }

        /** @param value {@code true} 只查询直接连接 Device，{@code false} 只查询间接 Device @return 当前 Builder */
        @Contract(value = "_ -> this", mutates = "this")
        public @NotNull Builder directConnection(boolean value) {
            this.directConnection = value;
            return this;
        }

        /** @return 使用当前条件创建的不可变 Device 查询 */
        @Contract(value = "-> new", pure = true)
        @CheckReturnValue
        public @NotNull DeviceQuery build() {
            return new DeviceQuery(this);
        }
    }
}
