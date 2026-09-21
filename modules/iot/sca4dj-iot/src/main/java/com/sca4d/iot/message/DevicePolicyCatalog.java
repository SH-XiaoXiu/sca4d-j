package com.sca4d.iot.message;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 校验并索引一个 App 登记的 ACK 与 retry 策略。 */
public final class DevicePolicyCatalog {

    private final @NotNull Map<TreeCode, AcknowledgementPolicy> acknowledgements;

    private final @NotNull Map<TreeCode, RetryPolicy> retries;

    /** @param acknowledgements 全部 ACK 策略 @param retries 全部 retry 策略 */
    public DevicePolicyCatalog(
            @NotNull Collection<AcknowledgementPolicy> acknowledgements,
            @NotNull Collection<RetryPolicy> retries) {
        this.acknowledgements = index(acknowledgements, AcknowledgementPolicy::code, "ACK");
        this.retries = index(retries, RetryPolicy::code, "retry");
    }

    /** @param code 策略 code @return 匹配 ACK 策略 @throws IllegalArgumentException 没有登记时抛出 */
    public @NotNull AcknowledgementPolicy acknowledgement(@NotNull String code) {
        return acknowledgement(TreeCode.of(code));
    }

    /** @param code 规范化策略 code @return 匹配 ACK 策略 @throws IllegalArgumentException 没有登记时抛出 */
    public @NotNull AcknowledgementPolicy acknowledgement(@NotNull TreeCode code) {
        AcknowledgementPolicy policy = acknowledgements.get(code);
        if (policy == null)
            throw new IllegalArgumentException("ACK 策略未登记: " + code);
        return policy;
    }

    /** @param code 策略 code @return 匹配 retry 策略 @throws IllegalArgumentException 没有登记时抛出 */
    public @NotNull RetryPolicy retry(@NotNull String code) {
        return retry(TreeCode.of(code));
    }

    /** @param code 规范化策略 code @return 匹配 retry 策略 @throws IllegalArgumentException 没有登记时抛出 */
    public @NotNull RetryPolicy retry(@NotNull TreeCode code) {
        RetryPolicy policy = retries.get(code);
        if (policy == null)
            throw new IllegalArgumentException("retry 策略未登记: " + code);
        return policy;
    }

    /** 建立唯一 code 索引并拒绝空值和重复值。 */
    private static <T> Map<TreeCode, T> index(
            Collection<T> values,
            Function<T, TreeCode> code,
            String name) {
        try {
            return Objects.requireNonNull(values, name + " 策略集合不能为空")
                    .stream()
                    .map(value -> Objects.requireNonNull(value, name + " 策略不能为空"))
                    .collect(Collectors.toUnmodifiableMap(code, Function.identity()));
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException(name + " 策略 code 重复", exception);
        }
    }
}
