package com.sca4d.core.spring.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanWrapperImpl;

import java.time.Instant;

/** 比较 TimeRange 指定的两个 Instant 属性，并将失败定位到结束属性。 */
public class TimeRangeValidator implements ConstraintValidator<TimeRange, Object> {

    private String start;

    private String end;

    /**
     * 读取开始和结束属性名。
     *
     * @param annotation 时间范围约束
     * @throws IllegalArgumentException 属性名为空白或两个属性名相同时抛出
     */
    @Override
    public void initialize(@NotNull TimeRange annotation) {
        start = annotation.start();
        end = annotation.end();
        if (start.isBlank() || end.isBlank() || start.equals(end)) {
            throw new IllegalArgumentException("@TimeRange 需要两个不同的属性名");
        }
    }

    /**
     * 读取 start 和 end 属性并比较两者。
     *
     * <p>失败时不改变被校验对象；只有 {@code context} 不为 null 时，违规信息挂到 end 属性上。
     *
     * @param value 待校验对象
     * @param context Bean Validation 上下文，为 null 时只返回校验结果
     * @return 对象为空、任一时间为空或开始时间严格早于结束时间时返回 {@code true}
     * @throws IllegalArgumentException 属性不可读或类型不是 Instant 时抛出
     */
    @Override
    public boolean isValid(@Nullable Object value, @Nullable ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapperImpl bean = new BeanWrapperImpl(value);
        Instant from = instantProperty(bean, start);
        Instant to = instantProperty(bean, end);
        if (from == null || to == null || from.isBefore(to)) {
            return true;
        }
        if (context != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(end)
                    .addConstraintViolation();
        }
        return false;
    }

    /**
     * 读取一个 Instant 属性的值。
     *
     * @param bean 被校验对象的属性访问器
     * @param name 属性名
     * @return 属性值；属性为 null 时返回 {@code null}
     * @throws IllegalArgumentException 属性不可读或类型不是 Instant 时抛出
     */
    private @Nullable Instant instantProperty(@NotNull BeanWrapperImpl bean, @NotNull String name) {
        if (!bean.isReadableProperty(name) || !Instant.class.equals(bean.getPropertyType(name))) {
            throw new IllegalArgumentException("@TimeRange 属性必须是可读取的 Instant: " + name);
        }
        return (Instant) bean.getPropertyValue(name);
    }
}
