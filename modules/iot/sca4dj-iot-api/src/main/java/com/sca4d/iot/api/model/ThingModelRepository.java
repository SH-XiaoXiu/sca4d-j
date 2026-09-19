package com.sca4d.iot.api.model;

import com.sca4d.core.api.i18n.LocalizedFields;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** 读取和发布一个 App 唯一选择的物模型来源。 */
public interface ThingModelRepository {

    /** @param productCode Product code @param version 物模型版本 @return 匹配物模型 */
    @CheckReturnValue
    @NotNull
    Optional<ThingModelPresentation> find(@NotNull String productCode, int version);

    /** @param productCode Product code @return 按版本递增的全部已发布物模型 */
    @CheckReturnValue
    @NotNull
    List<ThingModelPresentation> findPublished(@NotNull String productCode);

    /**
     * 发布不可修改物模型；实现必须保证版本连续和相同版本内容幂等。
     *
     * @param model 待发布的纯技术物模型
     * @param i18n 与技术模型一起首次发布的完整动态翻译
     * @return 实际保存或已经存在的物模型
     */
    @CheckReturnValue
    @NotNull
    ThingModelPresentation publish(@NotNull ThingModel model, @NotNull LocalizedFields i18n);

    /**
     * 完整替换一个已发布物模型版本的动态翻译，不改变技术内容和版本。
     *
     * @param productCode Product code
     * @param version 物模型版本
     * @param i18n 新的完整动态翻译
     * @return 修改后的物模型展示结果
     */
    @CheckReturnValue
    @NotNull
    ThingModelPresentation replaceI18n(
            @NotNull String productCode,
            int version,
            @NotNull LocalizedFields i18n);
}
