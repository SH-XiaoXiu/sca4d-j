package com.sca4d.iot.persistence.mapper;

import com.sca4d.iot.persistence.EncodedReportedGroup;
import com.sca4d.iot.persistence.EncodedReportedGroupSnapshot;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 读写 latest reported state 投影。 */
public interface ReportedStateMapper {

    /** 读取 Device 当前绑定的全部 reported 字段组。 */
    @NotNull
    List<EncodedReportedGroupSnapshot> selectReportedGroups(
            @Param("bindings") @NotNull Map<Long, Long> bindings,
            @Param("groupCode") @Nullable String groupCode);

    /**
     * 读取 reported 字段组的当前投影。
     *
     * @param deviceId Device 标识
     * @param bindingGeneration 物模型绑定版本
     * @param groupCode 字段组编码
     * @return reported 查询结果；尚无投影时返回 {@code null}
     */
    @Nullable
    EncodedReportedGroup selectReportedGroup(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("groupCode") @NotNull String groupCode);

    /**
     * 新增 reported 字段组的当前投影。
     *
     * @param deviceId Device 标识
     * @param bindingGeneration 物模型绑定版本
     * @param groupCode 字段组编码
     * @param orderScope 顺序值生效范围
     * @param orderValue 可比较顺序值
     * @param contentHash 状态内容摘要
     * @param stateJson 字段组状态 JSON
     * @param observedAt 设备观测时间
     * @param receivedAt 平台接收时间
     * @return 新增行数；同一字段组已经存在时为 0
     */
    int insertReportedGroupIfAbsent(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("groupCode") @NotNull String groupCode,
            @Param("orderScope") @NotNull String orderScope,
            @Param("orderValue") @NotNull String orderValue,
            @Param("contentHash") @NotNull String contentHash,
            @Param("stateJson") @NotNull String stateJson,
            @Param("observedAt") @NotNull Instant observedAt,
            @Param("receivedAt") @NotNull Instant receivedAt);

    /**
     * 按当前投影版本替换 reported 字段组投影。
     *
     * @param deviceId Device 标识
     * @param bindingGeneration 物模型绑定版本
     * @param groupCode 字段组编码
     * @param expectedVersion 修改前投影版本
     * @param orderScope 顺序值生效范围
     * @param orderValue 可比较顺序值
     * @param contentHash 状态内容摘要
     * @param stateJson 字段组状态 JSON
     * @param observedAt 设备观测时间
     * @param receivedAt 平台接收时间
     * @return 影响行数
     */
    int updateReportedGroup(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("groupCode") @NotNull String groupCode,
            @Param("expectedVersion") long expectedVersion,
            @Param("orderScope") @NotNull String orderScope,
            @Param("orderValue") @NotNull String orderValue,
            @Param("contentHash") @NotNull String contentHash,
            @Param("stateJson") @NotNull String stateJson,
            @Param("observedAt") @NotNull Instant observedAt,
            @Param("receivedAt") @NotNull Instant receivedAt);

    /** 删除 Device 指定绑定的全部 reported 当前投影。 */
    int deleteReportedGroups(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration);

}
