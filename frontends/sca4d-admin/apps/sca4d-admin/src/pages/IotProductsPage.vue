<script setup lang="ts">
import type {
  IotDatabaseProductCatalogItem,
  IotProductCatalogItem,
  LocalizedFields,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import {
  Boxes,
  Languages,
  ListTree,
  Pencil,
  Plus,
  RefreshCw,
  Upload,
} from 'lucide-vue-next';

import ResourceState from '../components/ResourceState.vue';
import {
  collectThingModelTranslationFields,
  type ThingModelTranslationField,
} from '../features/iot/thing-model-i18n';
import ThingValueTypeView from '../features/iot/ThingValueTypeView.vue';
import { useI18n } from '../i18n';
import { useAdminStore } from '../store/business';

type ProductModalMode = 'create' | 'i18n' | 'technical';

const store = useAdminStore();
const { errorText, language, t } = useI18n();
const productModalOpen = ref(false);
const productModalMode = ref<ProductModalMode>('create');
const productFormRef = ref<FormInstance>();
const productSaving = ref(false);
const editingProduct = ref<IotDatabaseProductCatalogItem | null>(null);
const productForm = reactive({
  accessMethods: [] as string[],
  code: '',
  names: {} as Record<string, string>,
  protocolCodes: [] as string[],
});
const statusSavingCode = ref<null | string>(null);
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

const canManage = computed(() => store.session.capabilities.iotProductsManage);
const catalog = computed(() => store.iotProducts.catalog);
const products = computed(() => catalog.value?.products ?? []);
const supportedLocales = computed(
  () => store.iotProducts.localeCatalog?.supportedLocales ?? [],
);
const defaultLocale = computed(
  () => store.iotProducts.localeCatalog?.defaultLocale ?? '',
);
const selectedModel = computed(() => store.iotProducts.selectedThingModel);
const selectedDatabaseProduct = computed(() => {
  const product = selectedProduct.value;
  return product?.source === 'DATABASE' ? product : null;
});
const accessMethodOptions = computed(() =>
  (catalog.value?.accessMethods ?? []).map((value) => ({
    label: value,
    value,
  })),
);
const protocolOptions = computed(() =>
  (catalog.value?.protocolCodes ?? []).map((value) => ({
    label: value,
    value,
  })),
);
const productModalTitle = computed(() => {
  if (productModalMode.value === 'create') return t('createIotProduct');
  if (productModalMode.value === 'technical') {
    return t('editProductDefinition');
  }
  return t('editProductNames');
});
const productColumns = computed(() => [
  { key: 'identity', title: t('iotProduct'), width: 240 },
  { key: 'source', title: t('productSource'), width: 120 },
  { key: 'accessMethods', title: t('accessMethods'), width: 220 },
  { key: 'protocolCodes', title: t('protocolCodes'), width: 220 },
  { key: 'status', title: t('status'), width: 120 },
  { key: 'actions', title: t('actions'), width: 160 },
]);

onMounted(() => void reload());
onBeforeUnmount(() => store.clearIotProductDetails());

/** 重新读取当前语言和当前页的双来源 Product 目录。 */
async function reload(): Promise<void> {
  try {
    await store.loadIotProducts();
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 切换 Product 页码或页大小，并由后端重新执行有界查询。 */
async function changeProductPage(pagination: {
  current?: number;
  pageSize?: number;
}): Promise<void> {
  try {
    await store.loadIotProducts(
      pagination.current ?? catalog.value?.page ?? 1,
      pagination.pageSize ?? catalog.value?.size ?? 20,
    );
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 打开数据库 Product 创建窗口。 */
function openCreateProduct(): void {
  editingProduct.value = null;
  productModalMode.value = 'create';
  productForm.code = '';
  productForm.accessMethods = [];
  productForm.protocolCodes = [];
  replaceProductNames({});
  productModalOpen.value = true;
}

/** 打开停用数据库 Product 的技术定义编辑窗口。 */
function openProductDefinition(product: IotDatabaseProductCatalogItem): void {
  editingProduct.value = product;
  productModalMode.value = 'technical';
  productForm.code = product.code;
  productForm.accessMethods = [...product.accessMethods];
  productForm.protocolCodes = [...product.protocolCodes];
  productModalOpen.value = true;
}

/** 读取并打开数据库 Product 的完整多语言名称。 */
async function openProductI18n(
  product: IotDatabaseProductCatalogItem,
): Promise<void> {
  editingProduct.value = product;
  productModalMode.value = 'i18n';
  productForm.code = product.code;
  try {
    await store.loadIotProductI18n(product);
    replaceProductNames(store.iotProducts.productI18n?.i18n ?? {});
    productModalOpen.value = true;
  } catch (error) {
    message.error(
      error instanceof Error
        ? error.message
        : errorText(store.iotProducts.error),
    );
  }
}

/** 校验当前窗口，并执行一种数据库 Product 写操作。 */
async function saveProduct(): Promise<void> {
  await productFormRef.value?.validate();
  productSaving.value = true;
  try {
    const mode = productModalMode.value;
    const product = editingProduct.value;
    if (mode === 'create') {
      await store.createIotProduct({
        accessMethods: productForm.accessMethods,
        code: productForm.code.trim(),
        i18n: productI18n(),
        protocolCodes: productForm.protocolCodes,
      });
      message.success(t('createSuccess'));
    } else if (mode === 'technical' && product) {
      await store.updateIotProduct(product, {
        accessMethods: productForm.accessMethods,
        protocolCodes: productForm.protocolCodes,
      });
      message.success(t('updateSuccess'));
    } else if (mode === 'i18n' && product) {
      await store.replaceIotProductI18n(product, productI18n());
      message.success(t('updateSuccess'));
    }
    productModalOpen.value = false;
  } catch {
    message.error(errorText(store.iotProducts.error));
  } finally {
    productSaving.value = false;
  }
}

/** 启用或停用数据库 Product，并保持表格状态与后端一致。 */
async function changeStatus(
  product: IotDatabaseProductCatalogItem,
  checked: boolean,
): Promise<void> {
  statusSavingCode.value = product.code;
  try {
    await store.changeIotProductStatus(
      product,
      checked ? 'ENABLED' : 'DISABLED',
    );
    message.success(t('updateSuccess'));
  } catch {
    message.error(errorText(store.iotProducts.error));
  } finally {
    statusSavingCode.value = null;
  }
}

/** 打开任意来源 Product 的物模型详情，并默认选择最新版本。 */
async function openThingModels(product: IotProductCatalogItem): Promise<void> {
  selectedProduct.value = product;
  selectedVersion.value = null;
  detailsOpen.value = true;
  try {
    await store.loadIotThingModels(product.code);
    const latest = store.iotProducts.thingModels.at(-1);
    if (latest) await selectThingModel(latest.version);
  } catch {
    // ResourceState 显示 Controller 保存的失败结果。
  }
}

/** 读取当前 Product 指定版本的本地化物模型。 */
async function selectThingModel(version: number): Promise<void> {
  const product = selectedProduct.value;
  if (!product) return;
  selectedVersion.value = version;
  try {
    await store.loadIotThingModel(product.code, version);
  } catch {
    // ResourceState 显示 Controller 保存的失败结果。
  }
}

/** 关闭详情并清除不再使用的物模型状态。 */
function closeDetails(): void {
  detailsOpen.value = false;
  selectedProduct.value = null;
  selectedVersion.value = null;
  store.clearIotProductDetails();
}

/** 使用当前 Product code 和下一个版本生成最小物模型草稿。 */
function openPublishModel(product: IotDatabaseProductCatalogItem): void {
  const nextVersion =
    Math.max(
      0,
      ...store.iotProducts.thingModels.map((model) => model.version),
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
    await store.publishIotThingModel(product, {
      i18n: modelI18n(),
      model: parseModelJson(),
    });
    selectedVersion.value =
      store.iotProducts.selectedThingModel?.version ?? null;
    publishOpen.value = false;
    message.success(t('thingModelPublished'));
  } catch (error) {
    message.error(
      error instanceof Error
        ? error.message
        : errorText(store.iotProducts.error),
    );
  } finally {
    publishSaving.value = false;
  }
}

/** 读取数据库物模型的动态翻译并打开编辑窗口。 */
async function openThingModelI18n(): Promise<void> {
  const product = selectedDatabaseProduct.value;
  const model = selectedModel.value;
  if (!product || !model) return;
  try {
    const fields = collectThingModelTranslationFields(model);
    await store.loadIotThingModelI18n(product, model.version);
    translationFields.value = fields;
    translationDraft.value = translationValues(
      fields,
      store.iotProducts.thingModelI18n?.i18n ?? {},
      false,
    );
    modelI18nOpen.value = true;
  } catch {
    message.error(errorText(store.iotProducts.error));
  }
}

/** 完整替换当前数据库物模型版本的多语言字段。 */
async function saveThingModelI18n(): Promise<void> {
  const product = selectedDatabaseProduct.value;
  const model = selectedModel.value;
  if (!product || !model) return;
  modelI18nSaving.value = true;
  try {
    await store.replaceIotThingModelI18n(product, model.version, modelI18n());
    modelI18nOpen.value = false;
    message.success(t('updateSuccess'));
  } catch (error) {
    message.error(
      error instanceof Error
        ? error.message
        : errorText(store.iotProducts.error),
    );
  } finally {
    modelI18nSaving.value = false;
  }
}

function replaceProductNames(i18n: LocalizedFields): void {
  for (const name of Object.keys(productForm.names)) {
    delete productForm.names[name];
  }
  for (const locale of supportedLocales.value) {
    productForm.names[locale] = i18n[locale]?.displayName ?? '';
  }
}

function productI18n(): LocalizedFields {
  return Object.fromEntries(
    supportedLocales.value.flatMap((locale) => {
      const value = productForm.names[locale]?.trim();
      return value ? [[locale, { displayName: value }]] : [];
    }),
  );
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

function isDatabaseProduct(
  product: IotProductCatalogItem,
): product is IotDatabaseProductCatalogItem {
  return product.source === 'DATABASE';
}

function productSource(product: IotProductCatalogItem): string {
  return product.source === 'CODE' ? t('codeProduct') : t('databaseProduct');
}

function propertyAccess(access: string): string {
  if (access === 'READ_ONLY') return t('readOnly');
  if (access === 'READ_WRITE') return t('readWrite');
  return access;
}

/** 管理端语言变化后重新读取后端本地化结果，不复用旧语言文本。 */
watch(language, async () => {
  const productCode = selectedProduct.value?.code;
  const version = selectedVersion.value;
  await reload();
  if (!detailsOpen.value || !productCode) return;
  const product = products.value.find((item) => item.code === productCode);
  if (!product) return closeDetails();
  selectedProduct.value = product;
  try {
    await store.loadIotThingModels(productCode);
    if (
      version &&
      store.iotProducts.thingModels.some((item) => item.version === version)
    ) {
      await selectThingModel(version);
    }
  } catch {
    // ResourceState 显示 Controller 保存的失败结果。
  }
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2 text-base font-medium">
        <Boxes :size="19" aria-hidden="true" />
        <span>{{ t('iotProducts') }}</span>
      </div>
      <div class="flex items-center gap-2">
        <a-tooltip :title="t('refresh')">
          <a-button :aria-label="t('refresh')" @click="reload">
            <RefreshCw :size="17" aria-hidden="true" />
          </a-button>
        </a-tooltip>
        <a-button v-if="canManage" type="primary" @click="openCreateProduct">
          <Plus :size="17" aria-hidden="true" />{{ t('createIotProduct') }}
        </a-button>
      </div>
    </div>

    <ResourceState
      :error="store.iotProducts.error"
      :phase="store.iotProducts.phase"
      @retry="reload"
    >
      <a-table
        :columns="productColumns"
        :data-source="products"
        :pagination="{
          current: catalog?.page ?? 1,
          pageSize: catalog?.size ?? 20,
          showSizeChanger: true,
          total: catalog?.total ?? 0,
        }"
        :scroll="{ x: 980 }"
        row-key="code"
        size="middle"
        @change="changeProductPage"
      >
        <template #bodyCell="{ column, record }">
          <div
            v-if="column.key === 'identity'"
            class="flex min-w-0 flex-col gap-0.5"
          >
            <strong class="truncate">{{ record.displayName }}</strong>
            <code class="text-muted-foreground truncate text-xs">{{
              record.code
            }}</code>
          </div>
          <a-tag
            v-else-if="column.key === 'source'"
            :color="record.source === 'CODE' ? 'blue' : 'cyan'"
          >
            {{ productSource(record) }}
          </a-tag>
          <div
            v-else-if="column.key === 'accessMethods'"
            class="flex flex-wrap gap-1"
          >
            <a-tag v-for="value in record.accessMethods" :key="value">
              {{ value }}
            </a-tag>
            <span v-if="record.accessMethods.length === 0">-</span>
          </div>
          <div
            v-else-if="column.key === 'protocolCodes'"
            class="flex flex-wrap gap-1"
          >
            <a-tag
              v-for="value in record.protocolCodes"
              :key="value"
              color="geekblue"
            >
              {{ value }}
            </a-tag>
          </div>
          <template v-else-if="column.key === 'status'">
            <a-tag v-if="record.source === 'CODE'" color="blue">
              {{ t('readOnly') }}
            </a-tag>
            <a-switch
              v-else-if="canManage"
              :checked="record.status === 'ENABLED'"
              :checked-children="t('active')"
              :un-checked-children="t('disabled')"
              :loading="statusSavingCode === record.code"
              :disabled="statusSavingCode !== null"
              :aria-label="`${record.displayName} ${record.status}`"
              @change="changeStatus(record, $event as boolean)"
            />
            <a-tag
              v-else
              :color="record.status === 'ENABLED' ? 'green' : 'default'"
            >
              {{ record.status === 'ENABLED' ? t('active') : t('disabled') }}
            </a-tag>
          </template>
          <div
            v-else-if="column.key === 'actions'"
            class="flex justify-center gap-1"
          >
            <a-tooltip :title="t('viewThingModels')">
              <a-button
                type="text"
                :aria-label="t('viewThingModels')"
                @click="openThingModels(record)"
              >
                <ListTree :size="17" aria-hidden="true" />
              </a-button>
            </a-tooltip>
            <template v-if="canManage && isDatabaseProduct(record)">
              <a-tooltip :title="t('editProductNames')">
                <a-button
                  type="text"
                  :aria-label="t('editProductNames')"
                  @click="openProductI18n(record)"
                >
                  <Languages :size="17" aria-hidden="true" />
                </a-button>
              </a-tooltip>
              <a-tooltip :title="t('editProductDefinition')">
                <a-button
                  type="text"
                  :disabled="record.status !== 'DISABLED'"
                  :aria-label="t('editProductDefinition')"
                  @click="openProductDefinition(record)"
                >
                  <Pencil :size="17" aria-hidden="true" />
                </a-button>
              </a-tooltip>
            </template>
          </div>
        </template>
      </a-table>
    </ResourceState>

    <a-modal
      v-model:open="productModalOpen"
      :confirm-loading="productSaving"
      :title="productModalTitle"
      @ok="saveProduct"
    >
      <a-form ref="productFormRef" :model="productForm" layout="vertical">
        <a-form-item
          v-if="productModalMode === 'create'"
          :label="t('productCode')"
          name="code"
          :rules="[
            { required: true, message: t('required') },
            {
              pattern: /^[a-z][a-z0-9-]{1,127}$/,
              message: t('invalidProductCode'),
            },
          ]"
        >
          <a-input v-model:value="productForm.code" :maxlength="128" />
        </a-form-item>

        <template v-if="productModalMode !== 'technical'">
          <a-form-item
            v-for="locale in supportedLocales"
            :key="locale"
            :label="t('productNameForLocale', { locale })"
            :name="['names', locale]"
            :rules="[
              {
                required: locale === defaultLocale,
                message: t('required'),
              },
              { max: 128, message: t('productNameLength') },
            ]"
          >
            <a-input
              v-model:value="productForm.names[locale]"
              :maxlength="128"
            />
          </a-form-item>
        </template>

        <template v-if="productModalMode !== 'i18n'">
          <a-form-item :label="t('accessMethods')" name="accessMethods">
            <a-select
              v-model:value="productForm.accessMethods"
              :options="accessMethodOptions"
              mode="multiple"
            />
          </a-form-item>
          <a-form-item
            :label="t('protocolCodes')"
            name="protocolCodes"
            :rules="[
              { required: true, type: 'array', min: 1, message: t('required') },
            ]"
          >
            <a-select
              v-model:value="productForm.protocolCodes"
              :options="protocolOptions"
              mode="multiple"
            />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>

    <a-drawer
      :open="detailsOpen"
      :title="selectedProduct?.displayName"
      width="min(820px, 96vw)"
      @close="closeDetails"
    >
      <template #extra>
        <div class="flex items-center gap-2">
          <a-select
            v-if="store.iotProducts.thingModels.length > 0"
            :value="selectedVersion"
            :options="
              store.iotProducts.thingModels.map((model) => ({
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

      <ResourceState
        :error="store.iotProducts.error"
        :phase="store.iotProducts.detailsPhase"
        @retry="selectedProduct && openThingModels(selectedProduct)"
      >
        <a-empty
          v-if="store.iotProducts.thingModels.length === 0"
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

          <a-tabs>
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
                    <code class="text-muted-foreground text-xs">{{
                      property.code
                    }}</code>
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
                    <code class="text-muted-foreground text-xs">{{
                      event.code
                    }}</code>
                    <a-tag>{{ event.delivery }}</a-tag>
                  </div>
                  <div
                    v-for="output in event.outputs"
                    :key="output.code"
                    class="border-border space-y-2 border-l pl-3"
                  >
                    <div class="flex flex-wrap items-center gap-2">
                      <span class="font-medium">{{ output.displayName }}</span>
                      <code class="text-muted-foreground text-xs">{{
                        output.code
                      }}</code>
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
                    <code class="text-muted-foreground text-xs">{{
                      command.code
                    }}</code>
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
                      <code class="text-muted-foreground text-xs">{{
                        input.code
                      }}</code>
                    </div>
                    <ThingValueTypeView :value="input.valueType" />
                  </div>
                </section>
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>
      </ResourceState>
    </a-drawer>

    <a-modal
      v-model:open="publishOpen"
      :confirm-loading="publishSaving"
      :title="t('publishThingModel')"
      width="min(900px, 96vw)"
      @ok="publishThingModel"
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
        <a-tabs v-if="translationFields.length > 0">
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
                  <code class="text-muted-foreground break-all text-xs">{{
                    field.path
                  }}</code>
                </span>
                <a-input
                  v-model:value="translationDraft[locale]![field.path]"
                  :maxlength="128"
                  :placeholder="field.fallback"
                />
              </label>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-modal>

    <a-modal
      v-model:open="modelI18nOpen"
      :confirm-loading="modelI18nSaving"
      :title="t('editThingModelI18n')"
      width="min(820px, 96vw)"
      @ok="saveThingModelI18n"
    >
      <div class="max-h-[70vh] overflow-y-auto pr-1">
        <a-tabs>
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
                  <code class="text-muted-foreground break-all text-xs">{{
                    field.path
                  }}</code>
                </span>
                <a-input
                  v-model:value="translationDraft[locale]![field.path]"
                  :maxlength="128"
                  :placeholder="field.fallback"
                />
              </label>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-modal>
  </Page>
</template>
