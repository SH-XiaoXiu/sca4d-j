package com.sca4d.iot.emqx.protocol;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 执行 MQTT 上行转换，并把转换结果投影为 reported state 或业务 ACK。 */
public class MqttUplinkDefinition {

    private final @NotNull MqttUplinkKind kind;

    private final @Nullable Pipeline<byte[], DataValues> reportedPipeline;

    private final @Nullable Pipeline<byte[], DecodedMqttAcknowledgement> acknowledgementPipeline;

    private final @Nullable String groupCode;

    private final @Nullable ReportedMergeMode mergeMode;

    private final @NotNull List<DataKey<?>> keys;

    /** 创建 reported state 上行定义。 */
    MqttUplinkDefinition(
            @NotNull Pipeline<byte[], DataValues> pipeline,
            @NotNull String groupCode,
            @NotNull ReportedMergeMode mergeMode,
            @NotNull List<DataKey<?>> keys) {
        this.kind = MqttUplinkKind.REPORTED;
        this.reportedPipeline = Objects.requireNonNull(pipeline, "reported Pipeline 不能为空");
        this.acknowledgementPipeline = null;
        this.groupCode = Objects.requireNonNull(groupCode, "reported group code 不能为空");
        this.mergeMode = Objects.requireNonNull(mergeMode, "reported merge mode 不能为空");
        this.keys = List.copyOf(Objects.requireNonNull(keys, "reported DataKey 不能为空"));
    }

    /** 创建业务 ACK 上行定义。 */
    MqttUplinkDefinition(@NotNull Pipeline<byte[], DecodedMqttAcknowledgement> pipeline) {
        this.kind = MqttUplinkKind.ACKNOWLEDGEMENT;
        this.reportedPipeline = null;
        this.acknowledgementPipeline = Objects.requireNonNull(pipeline, "ACK Pipeline 不能为空");
        this.groupCode = null;
        this.mergeMode = null;
        this.keys = List.of();
    }

    /**
     * 返回当前上行消息产生的业务结果类型。
     *
     * @return reported state 或业务 ACK
     */
    public @NotNull MqttUplinkKind kind() {
        return kind;
    }

    /**
     * 返回 reported 转换结果中允许写入的字段。
     *
     * @return 业务 ACK 返回空列表
     */
    public @NotNull List<DataKey<?>> keys() {
        return keys;
    }

    /**
     * 执行 reported Pipeline 并生成没有设备顺序号的平台观察结果。
     *
     * @param payload MQTT payload；执行前复制
     * @param observedAt 当前实例收到消息的时间
     * @return 只包含当前上行定义允许字段的 reported 更新
     * @throws IllegalStateException 当前定义处理业务 ACK 时抛出
     */
    public @NotNull ReportedStateUpdate decodeReported(
            byte @NotNull [] payload,
            @NotNull Instant observedAt) {
        if (kind != MqttUplinkKind.REPORTED) {
            throw new IllegalStateException("当前 MQTT 上行定义不是 reported state");
        }
        DataValues values = Objects.requireNonNull(reportedPipeline)
                .run(Objects.requireNonNull(payload, "MQTT payload 不能为空").clone());
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (DataKey<?> key : keys) {
            values.find(key).ifPresent(value -> result.put(key.name(), value));
        }
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        keys.stream().map(DataKey::name).forEach(fields::add);
        return new ReportedStateUpdate(
                Objects.requireNonNull(groupCode),
                Objects.requireNonNull(mergeMode),
                fields,
                result,
                null,
                null,
                Objects.requireNonNull(observedAt, "MQTT 消息观察时间不能为空"));
    }

    /**
     * 执行业务 ACK Pipeline。
     *
     * @param payload MQTT payload；执行前复制
     * @return 尚未写入数据库的标准业务 ACK
     * @throws IllegalStateException 当前定义处理 reported state 时抛出
     */
    public @NotNull DecodedMqttAcknowledgement decodeAcknowledgement(
            byte @NotNull [] payload) {
        if (kind != MqttUplinkKind.ACKNOWLEDGEMENT) {
            throw new IllegalStateException("当前 MQTT 上行定义不是业务 ACK");
        }
        return Objects.requireNonNull(acknowledgementPipeline)
                .run(Objects.requireNonNull(payload, "MQTT payload 不能为空").clone());
    }
}
