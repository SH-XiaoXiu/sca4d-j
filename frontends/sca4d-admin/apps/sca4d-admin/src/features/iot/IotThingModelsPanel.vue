<script setup lang="ts">
import type {
  IotDatabaseProductCatalogItem,
  IotProductCatalogItem,
  LocalizedFields,
} from '@sca4d/admin';

import { onScopeDispose as disposeEditor, watch as watchEditor } from 'vue';
import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';
import { Languages, ListTree, Upload } from 'lucide-vue-next';

import { SjResourceEditor, SjResourceState, SjTabs } from '#/components/sj';
import {
  collectThingModelTranslationFields,
  type ThingModelTranslationField,
} from '#/features/iot/thing-model-i18n';
import ThingValueTypeView from '#/features/iot/ThingValueTypeView.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const { t, errorText } = useSjPresentation();

let editorGeneration = 0;
disposeEditor(() => {
  editorGeneration++;
});

const iotProducts = useSjControllerState(businessApplication.iotProducts);
const session = useSjAccess();
const detailsOpen = ref(false);
const selectedProduct = ref<IotProductCatalogItem | null>(null);
const selectedVersion = ref<null | number>(null);
const publishOpen = ref(false);
const publishSaving = ref(false);
const modelJson = ref('');
const modelJsonError = ref<null | string>(null);
const translationFields = ref<readonly ThingModelTranslationField[]>([]);
const translationDraft = ref<Record<string, Record<string, string>>>({});
const modelI18nOpen = ref(false);
const modelI18nSaving = ref(false);

const canManage = computed(() => session.value.capabilities.iotProductsManage);
const supportedLocales = computed(
  () => iotProducts.value.localeCatalog?.supportedLocales ?? [],
);
const defaultLocale = computed(
  () => iotProducts.value.localeCatalog?.defaultLocale ?? '',
);
const selectedModel = computed(() => iotProducts.value.selectedThingModel);
const selectedDatabaseProduct = computed(() => {
  const product = selectedProduct.value;
  return product?.source === 'DATABASE' ? product : null;
});

/** 打开任意来源 Product 的物模型详情，并默认选择最新版本。 */
async function openThingModels(product: IotProductCatalogItem): Promise<void> {
  const read = ++editorGeneration;
  selectedProduct.value = product;
  selectedVersion.value = null;
  detailsOpen.value = true;
  try {
    await businessApplication.iotProducts.loadThingModels(product.code);
    if (read !== editorGeneration) return;
    const latest = iotProducts.value.thingModels.at(-1);
    if (latest) await selectThingModel(latest.version);
    if (read !== editorGeneration) return;
  } catch {
    if (read !== editorGeneration) return;
    // ResourceState 显示 Controller 保存的失败结果。
  }
}

/** 读取当前 Product 指定版本的本地化物模型。 */
async function selectThingModel(version: number): Promise<void> {
  const read = ++editorGeneration;
  const product = selectedProduct.value;
  if (!product) return;
  selectedVersion.value = version;
  try {
    await businessApplication.iotProducts.loadThingModel(product.code, version);
    if (read !== editorGeneration) return;
  } catch {
    if (read !== editorGeneration) return;
    // ResourceState 显示 Controller 保存的失败结果。
  }
}

/** 关闭详情并清除不再使用的物模型状态。 */
function closeDetails(): void {
  detailsOpen.value = false;
  selectedProduct.value = null;
  selectedVersion.value = null;
  businessApplication.iotProducts.clearDetails();
}

/** 使用当前 Product code 和下一个版本生成最小物模型草稿。 */
function openPublishModel(product: IotDatabaseProductCatalogItem): void {
  const nextVersion =
    Math.max(
      0,
      ...iotProducts.value.thingModels.map((model) => model.version),
    ) + 1;
  modelJson.value = JSON.stringify(
    {
      commands: [],
      events: [],
      formatVersion: 2,
      productCode: product.code,
      properties: [],
      version: nextVersion,
    },
    null,
    2,
  );
  modelJsonError.value = null;
  translationFields.value = [];
  translationDraft.value = {};
  publishOpen.value = true;
  preparePublishTranslations();
}

/** 解析物模型 JSON，并按稳定路径生成所有 Locale 输入项。 */
function preparePublishTranslations(): void {
  try {
    const model = parseModelJson();
    const fields = collectThingModelTranslationFields(model);
    translationFields.value = fields;
    translationDraft.value = translationValues(
      fields,
      translationDraft.value,
      true,
    );
    modelJsonError.value = null;
  } catch (error) {
    modelJsonError.value =
      error instanceof Error ? error.message : t('invalidThingModelJson');
  }
}

/** 发布技术 JSON 和所有已填写的 Locale 展示字段。 */
async function publishThingModel(): Promise<void> {
  const product = selectedDatabaseProduct.value;
  if (!product) return;
  preparePublishTranslations();
  if (modelJsonError.value) return;
  publishSaving.value = true;
  try {
    await businessApplication.iotProducts.publishThingModel(product, {
      i18n: modelI18n(),
      model: parseModelJson(),
    });
    selectedVersion.value =
      iotProducts.value.selectedThingModel?.version ?? null;
    publishOpen.value = false;
    message.success(t('thingModelPublished'));
  } catch (error) {
    message.error(
      error instanceof Error
        ? error.message
        : errorText(iotProducts.value.error),
    );
  } finally {
    publishSaving.value = false;
  }
}

/** 读取数据库物模型的动态翻译并打开编辑窗口。 */
async function openThingModelI18n(): Promise<void> {
  const read = ++editorGeneration;
  const product = selectedDatabaseProduct.value;
  const model = selectedModel.value;
  if (!product || !model) return;
  try {
    const fields = collectThingModelTranslationFields(model);
    await businessApplication.iotProducts.loadThingModelI18n(
      product,
      model.version,
    );
    if (read !== editorGeneration) return;
    translationFields.value = fields;
    translationDraft.value = translationValues(
      fields,
      iotProducts.value.thingModelI18n?.i18n ?? {},
      false,
    );
    modelI18nOpen.value = true;
  } catch {
    if (read !== editorGeneration) return;
    message.error(errorText(iotProducts.value.error));
  }
}

/** 完整替换当前数据库物模型版本的多语言字段。 */
async function saveThingModelI18n(): Promise<void> {
  const product = selectedDatabaseProduct.value;
  const model = selectedModel.value;
  if (!product || !model) return;
  modelI18nSaving.value = true;
  try {
    await businessApplication.iotProducts.replaceThingModelI18n(
      product,
      model.version,
      modelI18n(),
    );
    modelI18nOpen.value = false;
    message.success(t('updateSuccess'));
  } catch (error) {
    message.error(
      error instanceof Error
        ? error.message
        : errorText(iotProducts.value.error),
    );
  } finally {
    modelI18nSaving.value = false;
  }
}

function parseModelJson(): Readonly<Record<string, unknown>> {
  const value: unknown = JSON.parse(modelJson.value);
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(t('invalidThingModelJson'));
  }
  return value as Readonly<Record<string, unknown>>;
}

function translationValues(
  fields: readonly ThingModelTranslationField[],
  current: LocalizedFields | Record<string, Record<string, string>>,
  fillDefault: boolean,
): Record<string, Record<string, string>> {
  return Object.fromEntries(
    supportedLocales.value.map((locale) => [
      locale,
      Object.fromEntries(
        fields.map((field) => [
          field.path,
          current[locale]?.[field.path] ??
            (fillDefault && locale === defaultLocale.value
              ? field.fallback
              : ''),
        ]),
      ),
    ]),
  );
}

function modelI18n(): LocalizedFields {
  const result: Record<string, Record<string, string>> = {};
  for (const locale of supportedLocales.value) {
    const values: Record<string, string> = {};
    for (const field of translationFields.value) {
      const value = translationDraft.value[locale]?.[field.path]?.trim();
      if (value) values[field.path] = value;
      else if (locale === defaultLocale.value) {
        throw new Error(t('defaultLocaleRequired'));
      }
    }
    if (Object.keys(values).length > 0) result[locale] = values;
  }
  return result;
}

function propertyAccess(access: string): string {
  if (access === 'READ_ONLY') return t('readOnly');
  if (access === 'READ_WRITE') return t('readWrite');
  return access;
}
/** 提供 产品物模型版本查看、发布与翻译 的独立工作区入口。 */
defineExpose({ open: openThingModels });

watchEditor(detailsOpen, (open) => {
  if (!open) editorGeneration++;
});
watchEditor(publishOpen, (open) => {
  if (!open) editorGeneration++;
});
watchEditor(modelI18nOpen, (open) => {
  if (!open) editorGeneration++;
});
</script>
<template>
  <SjResourceEditor
    read-only
    size="xl"
    v-model:open="detailsOpen"
    :title="selectedProduct?.displayName ?? t('iotProducts')"
    @closed="closeDetails"
  >
    <template #extra>
      <div class="flex items-center gap-2">
        <a-select
          v-if="iotProducts.thingModels.length > 0"
          :value="selectedVersion"
          :options="
            iotProducts.thingModels.map((model) => ({
              label: `v${model.version} · ${model.displayName}`,
              value: model.version,
            }))
          "
          class="w-56 max-w-[45vw]"
          @change="selectThingModel"
        />
        <a-tooltip
          v-if="canManage && selectedDatabaseProduct"
          :title="t('publishThingModel')"
        >
          <a-button
            type="primary"
            :disabled="selectedDatabaseProduct.status !== 'DISABLED'"
            :aria-label="t('publishThingModel')"
            @click="openPublishModel(selectedDatabaseProduct)"
          >
            <Upload :size="17" aria-hidden="true" />
          </a-button>
        </a-tooltip>
      </div>
    </template>

    <SjResourceState
      :error="iotProducts.error"
      :phase="iotProducts.detailsPhase"
      @retry="selectedProduct && openThingModels(selectedProduct)"
    >
      <a-empty
        v-if="iotProducts.thingModels.length === 0"
        :description="t('noThingModels')"
      />
      <div v-else-if="selectedModel" class="space-y-5">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="min-w-0">
            <h2 class="truncate text-lg font-semibold">
              {{ selectedModel.displayName }}
            </h2>
            <div
              class="text-muted-foreground mt-1 flex flex-wrap gap-3 text-xs"
            >
              <span>{{ selectedModel.productCode }}</span>
              <span>
                {{ t('thingModelVersion') }}
                {{ selectedModel.version }}
              </span>
              <span>
                {{ t('formatVersion') }}
                {{ selectedModel.formatVersion }}
              </span>
            </div>
          </div>
          <a-button
            v-if="canManage && selectedDatabaseProduct"
            @click="openThingModelI18n"
          >
            <Languages :size="17" aria-hidden="true" />
            {{ t('editThingModelI18n') }}
          </a-button>
        </div>

        <SjTabs>
          <a-tab-pane
            key="properties"
            :tab="`${t('properties')} (${selectedModel.properties.length})`"
          >
            <a-empty
              v-if="selectedModel.properties.length === 0"
              :description="t('noData')"
            />
            <div v-else class="divide-border divide-y">
              <section
                v-for="property in selectedModel.properties"
                :key="property.code"
                class="space-y-3 py-4 first:pt-0"
              >
                <div class="flex flex-wrap items-center gap-2">
                  <strong>{{ property.displayName }}</strong>
                  <code class="text-muted-foreground text-xs">
                    {{ property.code }}
                  </code>
                  <a-tag>{{ propertyAccess(property.access) }}</a-tag>
                  <a-tag v-if="property.telemetry" color="green">
                    {{ t('telemetry') }}
                  </a-tag>
                  <a-tag v-if="property.acknowledgementPolicy" color="purple">
                    ACK: {{ property.acknowledgementPolicy }}
                  </a-tag>
                  <a-tag v-if="property.retryPolicy">
                    Retry: {{ property.retryPolicy }}
                  </a-tag>
                </div>
                <ThingValueTypeView :value="property.valueType" />
              </section>
            </div>
          </a-tab-pane>

          <a-tab-pane
            key="events"
            :tab="`${t('events')} (${selectedModel.events.length})`"
          >
            <a-empty
              v-if="selectedModel.events.length === 0"
              :description="t('noData')"
            />
            <div v-else class="divide-border divide-y">
              <section
                v-for="event in selectedModel.events"
                :key="event.code"
                class="space-y-3 py-4 first:pt-0"
              >
                <div class="flex flex-wrap items-center gap-2">
                  <strong>{{ event.displayName }}</strong>
                  <code class="text-muted-foreground text-xs">
                    {{ event.code }}
                  </code>
                  <a-tag>{{ event.delivery }}</a-tag>
                </div>
                <div
                  v-for="output in event.outputs"
                  :key="output.code"
                  class="border-border space-y-2 border-l pl-3"
                >
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="font-medium">{{ output.displayName }}</span>
                    <code class="text-muted-foreground text-xs">
                      {{ output.code }}
                    </code>
                  </div>
                  <ThingValueTypeView :value="output.valueType" />
                </div>
              </section>
            </div>
          </a-tab-pane>

          <a-tab-pane
            key="commands"
            :tab="`${t('commands')} (${selectedModel.commands.length})`"
          >
            <a-empty
              v-if="selectedModel.commands.length === 0"
              :description="t('noData')"
            />
            <div v-else class="divide-border divide-y">
              <section
                v-for="command in selectedModel.commands"
                :key="command.code"
                class="space-y-3 py-4 first:pt-0"
              >
                <div class="flex flex-wrap items-center gap-2">
                  <strong>{{ command.displayName }}</strong>
                  <code class="text-muted-foreground text-xs">
                    {{ command.code }}
                  </code>
                  <a-tag color="purple">
                    ACK: {{ command.acknowledgementPolicy }}
                  </a-tag>
                  <a-tag>Retry: {{ command.retryPolicy }}</a-tag>
                </div>
                <div
                  v-for="input in command.inputs"
                  :key="input.code"
                  class="border-border space-y-2 border-l pl-3"
                >
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="font-medium">{{ input.displayName }}</span>
                    <code class="text-muted-foreground text-xs">
                      {{ input.code }}
                    </code>
                  </div>
                  <ThingValueTypeView :value="input.valueType" />
                </div>
              </section>
            </div>
          </a-tab-pane>
        </SjTabs>
      </div>
    </SjResourceState>
  </SjResourceEditor>
  <SjResourceEditor
    v-model:open="publishOpen"
    :saving="publishSaving"
    :title="t('publishThingModel')"
    @submit="publishThingModel"
    :create="true"
    size="xl"
  >
    <div class="max-h-[70vh] space-y-4 overflow-y-auto pr-1">
      <a-form layout="vertical">
        <a-form-item
          :label="t('thingModelJson')"
          :validate-status="modelJsonError ? 'error' : undefined"
          :help="modelJsonError ?? undefined"
        >
          <a-textarea
            v-model:value="modelJson"
            class="font-mono"
            :auto-size="{ minRows: 10, maxRows: 20 }"
          />
        </a-form-item>
      </a-form>
      <a-button @click="preparePublishTranslations">
        <ListTree :size="17" aria-hidden="true" />
        {{ t('generateTranslationFields') }}
      </a-button>
      <SjTabs v-if="translationFields.length > 0">
        <a-tab-pane
          v-for="locale in supportedLocales"
          :key="locale"
          :tab="
            locale === defaultLocale
              ? `${locale} · ${t('defaultLocale')}`
              : locale
          "
        >
          <div class="space-y-3">
            <label
              v-for="field in translationFields"
              :key="field.path"
              class="block space-y-1"
            >
              <span class="flex min-w-0 flex-wrap items-center gap-2 text-sm">
                <strong>{{ field.fallback }}</strong>
                <code class="text-muted-foreground break-all text-xs">
                  {{ field.path }}
                </code>
              </span>
              <a-input
                v-model:value="translationDraft[locale]![field.path]"
                :maxlength="128"
                :placeholder="field.fallback"
              />
            </label>
          </div>
        </a-tab-pane>
      </SjTabs>
    </div>
  </SjResourceEditor>
  <SjResourceEditor
    v-model:open="modelI18nOpen"
    :saving="modelI18nSaving"
    :title="t('editThingModelI18n')"
    @submit="saveThingModelI18n"
    :create="false"
    size="lg"
  >
    <div class="max-h-[70vh] overflow-y-auto pr-1">
      <SjTabs>
        <a-tab-pane
          v-for="locale in supportedLocales"
          :key="locale"
          :tab="
            locale === defaultLocale
              ? `${locale} · ${t('defaultLocale')}`
              : locale
          "
        >
          <div class="space-y-3">
            <label
              v-for="field in translationFields"
              :key="field.path"
              class="block space-y-1"
            >
              <span class="flex min-w-0 flex-wrap items-center gap-2 text-sm">
                <strong>{{ field.fallback }}</strong>
                <code class="text-muted-foreground break-all text-xs">
                  {{ field.path }}
                </code>
              </span>
              <a-input
                v-model:value="translationDraft[locale]![field.path]"
                :maxlength="128"
                :placeholder="field.fallback"
              />
            </label>
          </div>
        </a-tab-pane>
      </SjTabs>
    </div>
  </SjResourceEditor>
</template>
