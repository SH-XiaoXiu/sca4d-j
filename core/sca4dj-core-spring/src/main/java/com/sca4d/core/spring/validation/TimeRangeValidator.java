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

    @Override
    public void initialize(@NotNull TimeRange annotation) {
        start = annotation.start();
        end = annotation.end();
        if (start.isBlank() || end.isBlank() || start.equals(end)) {
            throw new IllegalArgumentException("@TimeRange 需要两个不同的属性名");
        }
    }

    /** @return 任一时间为空或开始时间严格早于结束时间时返回 true */
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

    private @Nullable Instant instantProperty(@NotNull BeanWrapperImpl bean, @NotNull String name) {
        if (!bean.isReadableProperty(name) || !Instant.class.equals(bean.getPropertyType(name))) {
            throw new IllegalArgumentException("@TimeRange 属性必须是可读取的 Instant: " + name);
        }
        return (Instant) bean.getPropertyValue(name);
    }
}
