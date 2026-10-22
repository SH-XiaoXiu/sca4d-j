<script setup lang="ts">
import { computed, ref, watch } from 'vue';

interface SjAvatarProps {
  /** 与头像并列展示的名称，用于生成固定占位内容。 */
  readonly name: string;
  /** 已授权的短期图片地址；没有头像或读取失败时为空。 */
  readonly src?: null | string;
  /** 头像的固定像素尺寸。 */
  readonly size?: number;
}

const props = withDefaults(defineProps<SjAvatarProps>(), {
  src: null,
  size: 40,
});
const emit = defineEmits<{ imageError: [] }>();
const failed = ref(false);

const initial = computed(
  () => props.name.trim().charAt(0).toLocaleUpperCase() || '?',
);
watch(
  () => props.src,
  () => (failed.value = false),
);

/** 隐藏加载失败的图片，并通知页面决定是否重新申请短期地址。 */
function handleError(): void {
  failed.value = true;
  emit('imageError');
}
</script>

<template>
  <a-avatar
    :size="size"
    :src="src && !failed ? src : undefined"
    @error="handleError"
  >
    {{ initial }}
  </a-avatar>
</template>
