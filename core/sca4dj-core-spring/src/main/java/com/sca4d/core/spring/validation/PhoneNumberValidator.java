package com.sca4d.core.spring.validation;

import com.sca4d.core.api.condition.DnfClause;
import com.sca4d.core.api.condition.DnfExpression;
import com.sca4d.core.api.region.Continent;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.PhoneNumberCondition;
import com.sca4d.core.api.region.PhoneNumberType;
import com.sca4d.core.api.region.Region;
import com.sca4d.core.api.region.RegionCatalog;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** 使用 libphonenumber 和 PhoneNumberCondition Bean 执行 PhoneNumber 约束。 */
public final class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private final RegionCatalog regions;

    private final PhoneNumberConditionRegistry conditionRegistry;

    private DnfExpression<ParsedPhoneNumber> expression = DnfExpression.always();

    /** 创建不包含自定义条件的校验器，供非 Spring Bean Validation 环境使用。 */
    public PhoneNumberValidator() {
        this(RegionCatalog.standard(), new PhoneNumberConditionRegistry(List.of()));
    }

    /**
     * 创建使用 Spring 条件 Bean 的校验器。
     *
     * @param regions 地区和号码元数据
     * @param conditionRegistry 显式条件 Bean 目录
     */
    @Autowired
    public PhoneNumberValidator(
            @NotNull RegionCatalog regions,
            @NotNull PhoneNumberConditionRegistry conditionRegistry) {
        this.regions = regions;
        this.conditionRegistry = conditionRegistry;
    }

    /** @param annotation 电话号码约束 */
    @Override
    public void initialize(@NotNull PhoneNumber annotation) {
        if (annotation.anyOf().length == 0) {
            expression = DnfExpression.always();
            return;
        }
        List<DnfClause<ParsedPhoneNumber>> clauses = Arrays.stream(annotation.anyOf())
                .map(this::clause)
                .toList();
        expression = new DnfExpression<>(clauses);
    }

    /**
     * @param value 待校验电话号码
     * @param context Bean Validation 上下文
     * @return 空白值或有效规范 E.164 且满足任意 Rule 时返回 {@code true}
     */
    @Override
    public boolean isValid(@Nullable String value, @Nullable ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        ParsedPhoneNumber phoneNumber = regions.parse(value);
        return phoneNumber != null && expression.test(phoneNumber);
    }

    /** 把一个注解 Rule 转换成按声明顺序执行的 AND 条件。 */
    private DnfClause<ParsedPhoneNumber> clause(PhoneNumber.Rule rule) {
        List<Predicate<? super ParsedPhoneNumber>> predicates = new ArrayList<>();

        // 先校验并复制 Rule 声明，后续 Predicate 不再读取可变数组。
        Set<Region> includedRegions = regions(rule.regions());
        Set<Continent> includedContinents = new HashSet<>(Arrays.asList(rule.continents()));
        Set<Integer> includedCallingCodes = callingCodes(rule.countryCallingCodes());
        Set<PhoneNumberType> includedTypes = new HashSet<>(Arrays.asList(rule.types()));
        Set<Region> excludedRegions = regions(rule.excludeRegions());
        Set<Continent> excludedContinents = new HashSet<>(Arrays.asList(rule.excludeContinents()));
        Set<Integer> excludedCallingCodes = callingCodes(rule.excludeCountryCallingCodes());
        Set<PhoneNumberType> excludedTypes = new HashSet<>(Arrays.asList(rule.excludeTypes()));

        // 包含集合要求命中，排除集合要求不命中；非空条件全部加入当前 AND 条件组。
        addIncluded(predicates, includedRegions, ParsedPhoneNumber::region);
        addIncluded(predicates, includedContinents, ParsedPhoneNumber::continent);
        addIncluded(predicates, includedCallingCodes, ParsedPhoneNumber::countryCallingCode);
        addIncluded(predicates, includedTypes, ParsedPhoneNumber::type);
        addExcluded(predicates, excludedRegions, ParsedPhoneNumber::region);
        addExcluded(predicates, excludedContinents, ParsedPhoneNumber::continent);
        addExcluded(predicates, excludedCallingCodes, ParsedPhoneNumber::countryCallingCode);
        addExcluded(predicates, excludedTypes, ParsedPhoneNumber::type);

        // 自定义条件按注解声明顺序执行，每种类型必须恰好匹配一个 Spring Bean。
        for (Class<? extends PhoneNumberCondition> conditionType : rule.conditions()) {
            PhoneNumberCondition condition = conditionRegistry.resolve(conditionType);
            predicates.add(condition::test);
        }
        return new DnfClause<>(predicates);
    }

    /** 添加一个值必须包含在非空集合中的条件。 */
    private static <T> void addIncluded(
            List<Predicate<? super ParsedPhoneNumber>> predicates,
            Set<T> allowed,
            Function<ParsedPhoneNumber, T> value) {
        if (!allowed.isEmpty()) {
            predicates.add(facts -> allowed.contains(value.apply(facts)));
        }
    }

    /** 添加一个值不能包含在非空集合中的条件。 */
    private static <T> void addExcluded(
            List<Predicate<? super ParsedPhoneNumber>> predicates,
            Set<T> excluded,
            Function<ParsedPhoneNumber, T> value) {
        if (!excluded.isEmpty()) {
            predicates.add(facts -> !excluded.contains(value.apply(facts)));
        }
    }

    /** 规范化注解中的地区代码。 */
    private static Set<Region> regions(String[] values) {
        Set<Region> result = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("@PhoneNumber 地区代码不能为空白");
            }
            result.add(new Region(value.toUpperCase(Locale.ROOT)));
        }
        return result;
    }

    /** 校验并复制注解中的国际电话代码。 */
    private static Set<Integer> callingCodes(int[] values) {
        Set<Integer> result = new HashSet<>();
        for (int value : values) {
            if (value <= 0) {
                throw new IllegalArgumentException("@PhoneNumber 国际电话代码必须大于 0");
            }
            result.add(value);
        }
        return result;
    }

}
