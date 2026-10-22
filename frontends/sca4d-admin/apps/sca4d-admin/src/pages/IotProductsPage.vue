<script setup lang="ts">
import type {
  IotDatabaseProductCatalogItem,
  IotProductCatalogItem,
} from '@sca4d/admin';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { message } from 'ant-design-vue';
import {
  Boxes,
  Languages,
  ListTree,
  Pencil,
  Plus,
  RefreshCw,
} from 'lucide-vue-next';

import { SjDataGrid, SjPage, SjResourceState } from '#/components/sj';
import { SjRowActions } from '#/components/sj';
import { SjConfirmSwitch } from '#/components/sj';
import IotProductEditor from '#/features/iot/IotProductEditor.vue';
import IotThingModelsPanel from '#/features/iot/IotThingModelsPanel.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t as sjTranslate } from '#/locales';
import { businessApplication } from '#/platform/business-application';

const productEditor = ref<InstanceType<typeof IotProductEditor>>();
const modelsPanel = ref<InstanceType<typeof IotThingModelsPanel>>();

const { t, errorText, language } = useSjPresentation();

const iotProducts = useSjControllerState(businessApplication.iotProducts);
const session = useSjAccess();
const statusSavingCode = ref<null | string>(null);
const detailsOpen = ref(false);
const selectedProduct = ref<IotProductCatalogItem | null>(null);
const selectedVersion = ref<null | number>(null);

const canManage = computed(() => session.value.capabilities.iotProductsManage);
const catalog = computed(() => iotProducts.value.catalog);
const products = computed(() => catalog.value?.products ?? []);
const productColumns = computed(() => [
  { field: 'identity', title: t('iotProduct'), width: 240 },
  { field: 'source', title: t('productSource'), width: 120 },
  { field: 'accessMethods', title: t('accessMethods'), width: 220 },
  { field: 'protocolCodes', title: t('protocolCodes'), width: 220 },
  { field: 'status', title: t('status'), width: 120 },
  { field: 'actions', title: t('actions'), width: 160 },
]);

onMounted(() => void reload());
onBeforeUnmount(() => businessApplication.iotProducts.clearDetails());

/** 重新读取当前语言和当前页的双来源 Product 目录。 */
async function reload(): Promise<void> {
  try {
    await businessApplication.iotProducts.load();
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
    await businessApplication.iotProducts.load(
      pagination.current ?? catalog.value?.page ?? 1,
      pagination.pageSize ?? catalog.value?.size ?? 20,
    );
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 启用或停用数据库 Product，并保持表格状态与后端一致。 */
async function changeStatus(
  product: IotDatabaseProductCatalogItem,
  checked: boolean,
): Promise<void> {
  statusSavingCode.value = product.code;
  try {
    await businessApplication.iotProducts.changeStatus(
      product,
      checked ? 'ENABLED' : 'DISABLED',
    );
    message.success(t('updateSuccess'));
  } catch {
    message.error(errorText(iotProducts.value.error));
  } finally {
    statusSavingCode.value = null;
  }
}

/** 读取当前 Product 指定版本的本地化物模型。 */
async function selectThingModel(version: number): Promise<void> {
  const product = selectedProduct.value;
  if (!product) return;
  selectedVersion.value = version;
  try {
    await businessApplication.iotProducts.loadThingModel(product.code, version);
  } catch {
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

function isDatabaseProduct(
  product: IotProductCatalogItem,
): product is IotDatabaseProductCatalogItem {
  return product.source === 'DATABASE';
}

function productSource(product: IotProductCatalogItem): string {
  return product.source === 'CODE' ? t('codeProduct') : t('databaseProduct');
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
    await businessApplication.iotProducts.loadThingModels(productCode);
    if (
      version &&
      iotProducts.value.thingModels.some((item) => item.version === version)
    ) {
      await selectThingModel(version);
    }
  } catch {
    // ResourceState 显示 Controller 保存的失败结果。
  }
});
</script>

<template>
  <SjPage auto-content-height>
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
        <a-button
          v-if="canManage"
          type="primary"
          @click="productEditor?.create"
        >
          <Plus :size="17" aria-hidden="true" />
          {{ t('createIotProduct') }}
        </a-button>
      </div>
    </div>

    <SjResourceState
      :has-data="Boolean(iotProducts.catalog)"
      :error="iotProducts.error"
      :phase="iotProducts.phase"
      @retry="reload"
    >
      <SjDataGrid
        :columns="productColumns"
        :items="products"
        :pagination="{
          currentPage: catalog?.page ?? 1,
          pageSize: catalog?.size ?? 20,
          total: catalog?.total ?? 0,
        }"
        key-field="code"
        @page-change="
          (page, size) => changeProductPage({ current: page, pageSize: size })
        "
      >
        <template #cell="{ column, row, value }">
          <div
            v-if="column.field === 'identity'"
            class="flex min-w-0 flex-col gap-0.5"
          >
            <strong class="truncate">{{ row.displayName }}</strong>
            <code class="text-muted-foreground truncate text-xs">
              {{ row.code }}
            </code>
          </div>
          <a-tag
            v-else-if="column.field === 'source'"
            :color="row.source === 'CODE' ? 'blue' : 'cyan'"
          >
            {{ productSource(row) }}
          </a-tag>
          <div
            v-else-if="column.field === 'accessMethods'"
            class="flex flex-wrap gap-1"
          >
            <a-tag v-for="method in row.accessMethods" :key="method">
              {{ method }}
            </a-tag>
            <span v-if="row.accessMethods.length === 0">-</span>
          </div>
          <div
            v-else-if="column.field === 'protocolCodes'"
            class="flex flex-wrap gap-1"
          >
            <a-tag
              v-for="protocol in row.protocolCodes"
              :key="protocol"
              color="geekblue"
            >
              {{ protocol }}
            </a-tag>
          </div>
          <template v-else-if="column.field === 'status'">
            <a-tag v-if="row.source === 'CODE'" color="blue">
              {{ t('readOnly') }}
            </a-tag>
            <SjConfirmSwitch
              v-else-if="canManage"
              :checked="row.status === 'ENABLED'"
              :checked-children="t('active')"
              :un-checked-children="t('disabled')"
              :loading="statusSavingCode === row.code"
              :disabled="statusSavingCode !== null"
              :aria-label="`${row.displayName} ${row.status}`"
              @confirm="changeStatus(row, $event as boolean)"
              :confirm-title="`${row.displayName} ${row.status}`"
              :ok-text="
                row.status === 'ENABLED'
                  ? sjTranslate('sj.disabled')
                  : sjTranslate('sj.active')
              "
              :cancel-text="sjTranslate('sj.cancel')"
            />
            <a-tag
              v-else
              :color="row.status === 'ENABLED' ? 'green' : 'default'"
            >
              {{ row.status === 'ENABLED' ? t('active') : t('disabled') }}
            </a-tag>
          </template>
          <SjRowActions
            v-else-if="column.field === 'actions'"
            :actions="[
              {
                key: 'action-0',
                label: t('viewThingModels'),
                icon: ListTree,
                run: async () => {
                  await modelsPanel?.open(row);
                },
              },
              {
                key: 'action-1',
                label: t('editProductNames'),
                icon: Languages,
                run: async () => {
                  if (isDatabaseProduct(row))
                    await productEditor?.editNames(row);
                },
                visible: canManage && isDatabaseProduct(row),
              },
              {
                key: 'action-2',
                label: t('editProductDefinition'),
                icon: Pencil,
                run: async () => {
                  if (isDatabaseProduct(row))
                    await productEditor?.editDefinition(row);
                },
                visible: canManage && isDatabaseProduct(row),
                disabled: row.status !== 'DISABLED',
                disabledReason: sjTranslate('sj.actionUnavailable'),
              },
            ]"
          />
          <span
            v-if="
              ![
                'accessMethods',
                'actions',
                'identity',
                'protocolCodes',
                'source',
                'status',
              ].includes(column.field)
            "
          >
            {{ value ?? '-' }}
          </span>
        </template>
      </SjDataGrid>
    </SjResourceState>
  </SjPage>

  <IotProductEditor ref="productEditor" />

  <IotThingModelsPanel ref="modelsPanel" />
</template>

<!-- 数据库产品的创建与编辑 -->

<!-- 产品物模型版本查看、发布与翻译 -->
