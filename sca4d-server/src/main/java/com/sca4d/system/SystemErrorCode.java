package com.sca4d.system;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/** 系统字典、菜单和操作日志操作使用的错误码。 */
@CodeRange(start = 3400, end = 3499)
public enum SystemErrorCode implements BizErrorCode {
    /** 指定操作日志不存在。 */
    AUDIT_LOG_NOT_FOUND(3400, "error.audit.log-not-found", ResponseCode.NOT_FOUND),
    /** 一次字典排序或批量操作超过允许数量。 */
    DICTIONARY_BATCH_SIZE_EXCEEDED(3401, "error.dictionary.batch-size-exceeded", ResponseCode.BAD_REQUEST),
    /** 业务字典项的固定值不允许修改。 */
    DICTIONARY_BUSINESS_VALUE_IMMUTABLE(3402, "error.dictionary.business-value-immutable", ResponseCode.BAD_REQUEST),
    /** 业务字典项的固定值不符合编码规则。 */
    DICTIONARY_BUSINESS_VALUE_INVALID(3403, "error.dictionary.business-value-invalid", ResponseCode.BAD_REQUEST),
    /** 代码枚举字典项的编码由源码维护，管理端不能修改。 */
    DICTIONARY_CODE_ENUM_ITEM_CODE_OWNED(3404, "error.dictionary.code-enum-item-code-owned", ResponseCode.BAD_REQUEST),
    /** 代码枚举字典项不能从管理端删除。 */
    DICTIONARY_CODE_ENUM_ITEM_DELETE_FORBIDDEN(3405, "error.dictionary.code-enum-item-delete-forbidden", ResponseCode.BAD_REQUEST),
    /** 代码枚举字典项除展示字段外不能修改。 */
    DICTIONARY_CODE_ENUM_ITEM_READONLY(3406, "error.dictionary.code-enum-item-readonly", ResponseCode.BAD_REQUEST),
    /** 代码枚举字典类型的编码由源码维护，管理端不能修改。 */
    DICTIONARY_CODE_ENUM_TYPE_CODE_OWNED(3407, "error.dictionary.code-enum-type-code-owned", ResponseCode.BAD_REQUEST),
    /** 代码枚举字典类型除展示字段外不能修改。 */
    DICTIONARY_CODE_ENUM_TYPE_READONLY(3408, "error.dictionary.code-enum-type-readonly", ResponseCode.BAD_REQUEST),
    /** 字典编码为空或不符合编码格式。 */
    DICTIONARY_CODE_INVALID(3409, "error.dictionary.code-invalid", ResponseCode.BAD_REQUEST),
    /** 代码来源字典项不能从管理端删除。 */
    DICTIONARY_CODE_ITEM_DELETE_FORBIDDEN(3410, "error.dictionary.code-item-delete-forbidden", ResponseCode.BAD_REQUEST),
    /** 同一可见范围已经存在相同字典编码。 */
    DICTIONARY_CODE_OCCUPIED(3411, "error.dictionary.code-occupied", ResponseCode.CONFLICT),
    /** 代码来源字典类型不能从管理端删除。 */
    DICTIONARY_CODE_TYPE_DELETE_FORBIDDEN(3412, "error.dictionary.code-type-delete-forbidden", ResponseCode.BAD_REQUEST),
    /** 指定字典项不存在。 */
    DICTIONARY_ITEM_NOT_FOUND(3413, "error.dictionary.item-not-found", ResponseCode.NOT_FOUND),
    /** 字典项仍被业务数据引用，不能删除。 */
    DICTIONARY_ITEM_REFERENCED(3414, "error.dictionary.item-referenced", ResponseCode.CONFLICT),
    /** 同一字典中已经存在相同固定值。 */
    DICTIONARY_ITEM_VALUE_OCCUPIED(3415, "error.dictionary.item-value-occupied", ResponseCode.CONFLICT),
    /** 创建字典项时没有提供固定值。 */
    DICTIONARY_ITEM_VALUE_REQUIRED(3416, "error.dictionary.item-value-required", ResponseCode.BAD_REQUEST),
    /** 多语言标签包含系统不支持的语言。 */
    DICTIONARY_LABEL_LANGUAGE_UNSUPPORTED(3417, "error.dictionary.label-language-unsupported", ResponseCode.BAD_REQUEST),
    /** 指定字典类型不存在。 */
    DICTIONARY_NOT_FOUND(3418, "error.dictionary.not-found", ResponseCode.NOT_FOUND),
    /** 字典类型或其字典项仍被业务模块引用，不能删除。 */
    DICTIONARY_TYPE_REFERENCED(3419, "error.dictionary.type-referenced", ResponseCode.CONFLICT),
    /** 字典或字典项缺少系统要求的语言标签。 */
    DICTIONARY_REQUIRED_LABEL_MISSING(3420, "error.dictionary.required-label-missing", ResponseCode.BAD_REQUEST),
    /** 移动菜单后会形成父子循环。 */
    MENU_CYCLE(3421, "error.menu.cycle", ResponseCode.BAD_REQUEST),
    /** 菜单分组仍有子节点，不能删除。 */
    MENU_GROUP_HAS_CHILDREN(3422, "error.menu.group-has-children", ResponseCode.BAD_REQUEST),
    /** 指定菜单不存在。 */
    MENU_NOT_FOUND(3423, "error.menu.not-found", ResponseCode.NOT_FOUND),
    /** 指定父菜单不是分组类型。 */
    MENU_PARENT_MUST_BE_GROUP(3424, "error.menu.parent-must-be-group", ResponseCode.BAD_REQUEST),
    /** 指定父菜单不存在。 */
    MENU_PARENT_NOT_FOUND(3425, "error.menu.parent-not-found", ResponseCode.NOT_FOUND),
    /** 菜单使用了管理端没有注册的路由。 */
    MENU_ROUTE_NOT_REGISTERED(3426, "error.menu.route-not-registered", ResponseCode.BAD_REQUEST),
    /** 菜单路由已经被其他菜单使用。 */
    MENU_ROUTE_OCCUPIED(3427, "error.menu.route-occupied", ResponseCode.CONFLICT),
    /** 菜单多语言标题为空或格式无效。 */
    MENU_TITLES_INVALID(3428, "error.menu.titles-invalid", ResponseCode.BAD_REQUEST),
    /** 字典类型版本已经变化。 */
    DICTIONARY_CHANGED(3429, "error.dictionary.changed", ResponseCode.CONFLICT),
    /** 字典项版本已经变化。 */
    DICTIONARY_ITEM_CHANGED(3430, "error.dictionary.item-changed", ResponseCode.CONFLICT),
    /** 字典类型排序版本已经变化。 */
    DICTIONARY_SORT_CHANGED(3431, "error.dictionary.sort-changed", ResponseCode.CONFLICT),
    /** 字典项排序版本已经变化。 */
    DICTIONARY_ITEM_SORT_CHANGED(3432, "error.dictionary.item-sort-changed", ResponseCode.CONFLICT),
    /** 菜单版本已经变化。 */
    MENU_CHANGED(3433, "error.menu.changed", ResponseCode.CONFLICT);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    SystemErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
