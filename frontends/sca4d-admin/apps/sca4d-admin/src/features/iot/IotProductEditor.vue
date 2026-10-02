<script setup lang="ts">
import type {
  IotDatabaseProductCatalogItem,
  LocalizedFields,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { onScopeDispose as disposeEditor, watch as watchEditor } from 'vue';
import { computed, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { SjResourceEditor } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const { t, errorText } = useSjPresentation();

let editorGeneration = 0;
disposeEditor(() => {
  editorGeneration++;
});

type ProductModalMode = 'create' | 'i18n' | 'technical';

const iotProducts = useSjControllerState(businessApplication.iotProducts);
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
const catalog = computed(() => iotProducts.value.catalog);
const supportedLocales = computed(
  () => iotProducts.value.localeCatalog?.supportedLocales ?? [],
);
const defaultLocale = computed(
  () => iotProducts.value.localeCatalog?.defaultLocale ?? '',
);
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
  const read = ++editorGeneration;
  editingProduct.value = product;
  productModalMode.value = 'i18n';
  productForm.code = product.code;
  try {
    await businessApplication.iotProducts.loadProductI18n(product);
    if (read !== editorGeneration) return;
    replaceProductNames(iotProducts.value.productI18n?.i18n ?? {});
    productModalOpen.value = true;
  } catch (error) {
    if (read !== editorGeneration) return;
    message.error(
      error instanceof Error
        ? error.message
        : errorText(iotProducts.value.error),
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
      await businessApplication.iotProducts.create({
        accessMethods: productForm.accessMethods,
        code: productForm.code.trim(),
        i18n: productI18n(),
        protocolCodes: productForm.protocolCodes,
      });
      message.success(t('createSuccess'));
    } else if (mode === 'technical' && product) {
      await businessApplication.iotProducts.update(product, {
        accessMethods: productForm.accessMethods,
        protocolCodes: productForm.protocolCodes,
      });
      message.success(t('updateSuccess'));
    } else if (mode === 'i18n' && product) {
      await businessApplication.iotProducts.replaceProductI18n(
        product,
        productI18n(),
      );
      message.success(t('updateSuccess'));
    }
    productModalOpen.value = false;
  } catch {
    message.error(errorText(iotProducts.value.error));
  } finally {
    productSaving.value = false;
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
/** 提供 数据库产品的创建与编辑 的独立工作区入口。 */
defineExpose({
  create: openCreateProduct,
  editDefinition: openProductDefinition,
  editNames: openProductI18n,
});

watchEditor(productModalOpen, (open) => {
  if (!open) editorGeneration++;
});

import { SjSelect } from '#/components/sj';
const sjEmptyText = computed(() => $t('sj.noData'));
import { $t } from '#/locales';
</script>
<template>
  <SjResourceEditor
    v-model:open="productModalOpen"
    :saving="productSaving"
    :title="productModalTitle"
    @submit="saveProduct"
    :create="productModalMode === 'create'"
    size="xl"
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
          <a-input v-model:value="productForm.names[locale]" :maxlength="128" />
        </a-form-item>
      </template>

      <template v-if="productModalMode !== 'i18n'">
        <a-form-item :label="t('accessMethods')" name="accessMethods">
          <SjSelect
            :empty-text="sjEmptyText"
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
          <SjSelect
            :empty-text="sjEmptyText"
            v-model:value="productForm.protocolCodes"
            :options="protocolOptions"
            mode="multiple"
          />
        </a-form-item>
      </template>
    </a-form>
  </SjResourceEditor>
</template>
