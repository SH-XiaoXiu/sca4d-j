package com.sca4d.iot.persistence.mapper;

import com.sca4d.iot.persistence.ConvergedDesiredMessage;
import com.sca4d.iot.persistence.EncodedDesiredProperty;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 读写 `iot_desired_property` 中每个 Device 当前持续期望的属性值。 */
public interface DesiredPropertyMapper {

    /** 读取 Device 当前绑定的全部或指定编码的 desired 属性。 */
    @NotNull
    List<EncodedDesiredProperty> select(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("propertyCodes") @Nullable Collection<@NotNull String> propertyCodes);

    /** 读取 Device 当前 desired sequence。 */
    @Nullable
    Long selectDeviceDesiredSequence(@Param("deviceId") long deviceId);

    /** 当 desired sequence 仍等于预期值时更新它。 */
    int updateDeviceDesiredSequence(
            @Param("deviceId") long deviceId,
            @Param("expectedDesiredSequence") long expectedDesiredSequence,
            @Param("desiredSequence") long desiredSequence);

    /** 新增或替换当前 desired property，并清除旧收敛时间。 */
    int upsert(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("propertyCode") @NotNull String propertyCode,
            @Param("desiredSequence") long desiredSequence,
            @Param("valueJson") @NotNull String valueJson);

    /** 删除一个当前 desired property。 */
    int delete(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("propertyCode") @NotNull String propertyCode);

    /** 删除 Device 指定绑定的全部 desired 当前投影。 */
    int deleteBinding(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration);

    /** 批量标记与 reported 值相等的属性，并返回各属性当前仍未结束的最新 desired 消息。 */
    @NotNull
    List<ConvergedDesiredMessage> updateConvergedAtIfValuesMatch(
            @Param("deviceId") long deviceId,
            @Param("bindingGeneration") long bindingGeneration,
            @Param("values") @NotNull Map<@NotNull String, @NotNull String> values,
            @Param("now") @NotNull Instant now);
}
