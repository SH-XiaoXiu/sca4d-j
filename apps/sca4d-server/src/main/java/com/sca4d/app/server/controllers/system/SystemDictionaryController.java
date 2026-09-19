package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.audit.ApplicationAuditValues;
import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.SystemDictionaryService;
import com.sca4d.app.server.system.request.CreateDictionaryOptionRequest;
import com.sca4d.app.server.system.request.CreateDictionaryRequest;
import com.sca4d.app.server.system.request.UpdateDictionaryOptionRequest;
import com.sca4d.app.server.system.request.UpdateDictionaryRequest;
import com.sca4d.app.server.system.view.DictionarySelectionView;
import com.sca4d.app.server.system.view.SystemDictionariesView;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.dictionary.Dictionaries;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 查询 Java 枚举和数据库选项聚合后的字典。 */
@RestController
@RequestMapping("/system/dictionaries")
@Tag(name = "System Dictionaries")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class SystemDictionaryController {

    private final Dictionaries dictionaries;

    private final SystemDictionaryService management;

    /** 创建字典查询 HTTP 入口。 */
    public SystemDictionaryController(
            @NotNull Dictionaries dictionaries,
            @NotNull SystemDictionaryService management) {
        this.dictionaries = Objects.requireNonNull(dictionaries, "Dictionaries 不能为空");
        this.management = Objects.requireNonNull(management, "系统字典服务不能为空");
    }

    /** @return 代码字典、动态字典及其全部选项的管理结果 */
    @GetMapping
    @Operation(summary = "查询系统字典")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_READ)
    public @NotNull ApiResponse<SystemDictionariesView> findAll() {
        return ApiResponse.ok(SystemDictionariesView.from(management.findAll()));
    }

    /**
     * 查询一个字典的聚合选择项。
     *
     * @param key Java Dictionary 或数据库动态字典键
     * @return 已按固定顺序合并的选择项
     */
    @GetMapping("/{key}/options")
    @Operation(summary = "查询字典选择项")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_READ)
    public @NotNull ApiResponse<DictionarySelectionView> options(@PathVariable @NotNull String key) {
        return ApiResponse.ok(new DictionarySelectionView(key, dictionaries.options(key)));
    }

    /** 创建一个数据库动态字典。 */
    @PostMapping
    @Operation(summary = "创建动态字典")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_MANAGE)
    @Audit(operation = "system/dictionary/create", payloadFactory = ApplicationAuditValues.DictionaryMutationDetails.class)
    public @NotNull ApiResponse<SystemDictionariesView> createDictionary(
            @RequestBody @Valid @NotNull CreateDictionaryRequest request) {
        management.createDictionary(
                request.getKey(),
                request.getLabel(),
                request.isEnabled(),
                request.getSortOrder());
        return ApiResponse.ok(SystemDictionariesView.from(management.findAll()));
    }

    /** 按乐观版本修改一个数据库动态字典。 */
    @PutMapping("/{key}")
    @Operation(summary = "修改动态字典")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_MANAGE)
    @Audit(operation = "system/dictionary/update", payloadFactory = ApplicationAuditValues.DictionaryMutationDetails.class)
    public @NotNull ApiResponse<SystemDictionariesView> updateDictionary(
            @PathVariable @NotNull String key,
            @RequestBody @Valid @NotNull UpdateDictionaryRequest request) {
        management.updateDictionary(
                key,
                request.getLabel(),
                request.isEnabled(),
                request.getSortOrder(),
                request.getVersion());
        return ApiResponse.ok(SystemDictionariesView.from(management.findAll()));
    }

    /** 按乐观版本删除一个数据库动态字典及其数据库选项。 */
    @DeleteMapping("/{key}")
    @Operation(summary = "删除动态字典")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_MANAGE)
    @Audit(operation = "system/dictionary/delete", payloadFactory = ApplicationAuditValues.DictionaryMutationDetails.class)
    public @NotNull ApiResponse<Void> deleteDictionary(
            @PathVariable @NotNull String key,
            @RequestParam @Min(0) long version) {
        management.deleteDictionary(key, version);
        return ApiResponse.noContent();
    }

    /** 给动态字典或可扩展代码字典新增数据库选项。 */
    @PostMapping("/{key}/options")
    @Operation(summary = "创建数据库字典项")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_MANAGE)
    @Audit(operation = "system/dictionary/option/create", payloadFactory = ApplicationAuditValues.DictionaryMutationDetails.class)
    public @NotNull ApiResponse<SystemDictionariesView> createOption(
            @PathVariable @NotNull String key,
            @RequestBody @Valid @NotNull CreateDictionaryOptionRequest request) {
        management.createOption(
                key,
                request.getValue(),
                request.getLabel(),
                request.getI18n(),
                request.isEnabled(),
                request.getSortOrder());
        return ApiResponse.ok(SystemDictionariesView.from(management.findAll()));
    }

    /** 按乐观版本修改一个数据库字典项；Java 代码项只读。 */
    @PutMapping("/{key}/options/{value}")
    @Operation(summary = "修改数据库字典项")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_MANAGE)
    @Audit(operation = "system/dictionary/option/update", payloadFactory = ApplicationAuditValues.DictionaryMutationDetails.class)
    public @NotNull ApiResponse<SystemDictionariesView> updateOption(
            @PathVariable @NotNull String key,
            @PathVariable @NotNull String value,
            @RequestBody @Valid @NotNull UpdateDictionaryOptionRequest request) {
        management.updateOption(
                key,
                value,
                request.getLabel(),
                request.getI18n(),
                request.isEnabled(),
                request.getSortOrder(),
                request.getVersion());
        return ApiResponse.ok(SystemDictionariesView.from(management.findAll()));
    }

    /** 按乐观版本删除一个数据库字典项；Java 代码项只读。 */
    @DeleteMapping("/{key}/options/{value}")
    @Operation(summary = "删除数据库字典项")
    @RequirePermission(operation = SystemOperations.DICTIONARIES_MANAGE)
    @Audit(operation = "system/dictionary/option/delete", payloadFactory = ApplicationAuditValues.DictionaryMutationDetails.class)
    public @NotNull ApiResponse<Void> deleteOption(
            @PathVariable @NotNull String key,
            @PathVariable @NotNull String value,
            @RequestParam @Min(0) long version) {
        management.deleteOption(key, value, version);
        return ApiResponse.noContent();
    }
}
