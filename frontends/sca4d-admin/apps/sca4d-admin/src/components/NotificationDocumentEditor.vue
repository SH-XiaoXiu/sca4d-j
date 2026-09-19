<script setup lang="ts">
import type { NotificationRichTextNode } from '@sca4d/admin';
import type { Editor, JSONContent } from '@vben/plugins/tiptap';

import { computed, ref } from 'vue';

import {
  createDefaultTiptapExtensions,
  Node,
  VbenTiptap,
} from '@vben/plugins/tiptap';
import { Plus } from 'lucide-vue-next';

interface EditorHandle {
  editor: Editor | null;
}

interface Props {
  editable?: boolean;
  insertLabel: string;
  placeholder: string;
  variablePlaceholder: string;
  variables?: readonly string[];
}

const props = withDefaults(defineProps<Props>(), {
  editable: true,
  variables: () => [],
});
const model = defineModel<NotificationRichTextNode>({ required: true });
const editor = ref<EditorHandle>();
const variableName = ref('');
const content = computed<JSONContent>({
  get: () => model.value as JSONContent,
  set: (value) => {
    model.value = value as NotificationRichTextNode;
  },
});
const variableOptions = computed(() =>
  props.variables.map((value) => ({ label: value, value })),
);
const extensions = [
  ...createDefaultTiptapExtensions({ placeholder: props.placeholder }),
  notificationVariable(),
  migratedInlineTemplate(),
  migratedRichHtml(),
];

/** 在当前光标处插入一个由后端校验名称的通知变量节点。 */
function insertVariable(): void {
  const name = variableName.value.trim();
  if (!/^[a-z][a-zA-Z0-9_]{0,63}$/u.test(name)) return;
  editor.value?.editor
    ?.chain()
    .focus()
    .insertContent({ attrs: { name }, type: 'notificationVariable' })
    .run();
  variableName.value = '';
}

/** 创建不可直接编辑、可以整体删除的通知变量节点。 */
function notificationVariable() {
  return Node.create({
    addAttributes: () => ({ name: { default: '' } }),
    atom: true,
    group: 'inline',
    inline: true,
    name: 'notificationVariable',
    parseHTML: () => [{ tag: 'span[data-notification-variable]' }],
    renderHTML: ({ node }) => [
      'span',
      {
        class:
          'notification-template-token rounded bg-primary/10 px-1 font-mono text-primary',
        'data-notification-variable': node.attrs.name,
      },
      `{{ ${String(node.attrs.name)} }}`,
    ],
    selectable: true,
  });
}

/** 显示旧模板迁移产生的 Mustache 文本，保存时保持原节点。 */
function migratedInlineTemplate() {
  return Node.create({
    addAttributes: () => ({ source: { default: '' } }),
    atom: true,
    group: 'inline',
    inline: true,
    name: 'templateText',
    parseHTML: () => [{ tag: 'span[data-template-text]' }],
    renderHTML: ({ node }) => [
      'span',
      {
        class: 'rounded bg-muted px-1 font-mono text-muted-foreground',
        'data-template-text': node.attrs.source,
      },
      String(node.attrs.source),
    ],
    selectable: true,
  });
}

/** 显示旧模板迁移产生的 HTML 原文，保存时保持原节点。 */
function migratedRichHtml() {
  return Node.create({
    addAttributes: () => ({ source: { default: '' } }),
    atom: true,
    group: 'block',
    name: 'richHtml',
    parseHTML: () => [{ tag: 'pre[data-rich-html]' }],
    renderHTML: ({ node }) => [
      'pre',
      {
        class:
          'whitespace-pre-wrap rounded border border-dashed bg-muted p-3 font-mono text-xs text-muted-foreground',
        'data-rich-html': node.attrs.source,
      },
      String(node.attrs.source),
    ],
    selectable: true,
  });
}
</script>

<template>
  <div class="space-y-2">
    <div v-if="editable" class="flex gap-2">
      <a-auto-complete
        v-model:value="variableName"
        class="min-w-0 flex-1"
        :options="variableOptions"
        :placeholder="variablePlaceholder"
        @keyup.enter="insertVariable"
      />
      <a-button
        :disabled="!variableName.trim()"
        :aria-label="insertLabel"
        @click="insertVariable"
      >
        <Plus :size="16" aria-hidden="true" />{{ insertLabel }}
      </a-button>
    </div>
    <VbenTiptap
      ref="editor"
      v-model="content"
      :editable="editable"
      :extensions="extensions"
      output="json"
      :placeholder="placeholder"
      :previewable="false"
    />
  </div>
</template>
