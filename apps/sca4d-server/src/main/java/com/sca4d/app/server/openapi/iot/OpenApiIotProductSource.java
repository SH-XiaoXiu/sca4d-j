package com.sca4d.app.server.openapi.iot;

import com.sca4d.iot.api.ProductSource;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示开放 API Device 所属 Product 定义的维护位置。 */
@Schema(name = "IotProductSource", description = "Product 定义的维护位置。")
public enum OpenApiIotProductSource {
    /** Product 和物模型保存在数据库中。 */
    DATABASE,

    /** Product 和物模型由应用代码提供。 */
    CODE;

    /**
     * 把内部 Product 来源转换为开放 API 枚举。
     *
     * @param source 内部 Product 来源
     * @return 对外稳定的 Product 来源
     */
    @CheckReturnValue
    public static @NotNull OpenApiIotProductSource from(@NotNull ProductSource source) {
        return valueOf(Objects.requireNonNull(source, "Product 来源不能为空").name());
    }
}
