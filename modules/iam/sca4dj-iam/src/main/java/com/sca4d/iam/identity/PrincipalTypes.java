package com.sca4d.iam.identity;

import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 将领域 Principal Java 类型映射为 Session Codec 的稳定判别值。 */
public final class PrincipalTypes {
    private static final Logger LOG = LoggerFactory.getLogger(PrincipalTypes.class);

    private final @NotNull @Unmodifiable Map<@NotNull Class<? extends Principal>, @NotNull PrincipalType> byValueType;

    /**
     * 创建 Principal 类型映射并拒绝重复判别值或 Java 类型。
     *
     * @param codecs 应用安装的全部 Principal Session Codec
     */
    public PrincipalTypes(@NotNull List<? extends @NotNull PrincipalCodec<?>> codecs) {
        Objects.requireNonNull(codecs, "Principal Codec 集合不能为空");
        Map<Class<? extends Principal>, PrincipalType> types = new HashMap<>();
        Map<PrincipalType, Class<? extends Principal>> values = new HashMap<>();
        for (PrincipalCodec<?> codec : codecs) {
            PrincipalCodec<?> checkedCodec = Objects.requireNonNull(codec, "Principal Codec 不能为空");
            PrincipalType discriminator = new PrincipalType(
                    Objects.requireNonNull(checkedCodec.type(), "Principal Codec type 不能为空"));
            Class<? extends Principal> valueType = Objects
                    .requireNonNull(checkedCodec.valueType(), "Principal Codec valueType 不能为空");
            if (types.putIfAbsent(valueType, discriminator) != null) {
                throw new IllegalStateException("同一 Principal Java 类型只能注册一个 Codec: " + valueType.getName());
            }
            if (values.putIfAbsent(discriminator, valueType) != null) {
                throw new IllegalStateException("Principal Codec type 不能重复: " + discriminator);
            }
        }
        byValueType = Map.copyOf(types);
        LOG.debug("Principal 类型映射已建立 | codecCount={}", byValueType.size());
    }

    /**
     * 返回 Principal Java 类型对应的稳定判别值。
     *
     * @param valueType Principal 精确 Java 类型
     * @return Session Codec 的稳定类型值
     * @throws BusinessException 类型未注册 Session Codec 时抛出
     */
    @CheckReturnValue
    public @NotNull PrincipalType principalType(@NotNull Class<? extends Principal> valueType) {
        PrincipalType discriminator = byValueType.get(Objects.requireNonNull(valueType, "Principal 类型不能为空"));
        if (discriminator == null) {
            throw new BusinessException(IamErrorCode.PRINCIPAL_TYPE_UNREGISTERED);
        }
        LOG.trace(
                "Principal 类型已解析 | valueType={} discriminator={}",
                valueType.getName(),
                discriminator);
        return discriminator;
    }
}
