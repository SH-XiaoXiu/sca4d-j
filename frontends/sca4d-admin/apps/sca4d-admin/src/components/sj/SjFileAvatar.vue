<script setup lang="ts">
import type { Int64String } from '@sca4d/admin';

import { watch } from 'vue';

import { useSjFileAccessUrl } from '#/features/sj-files';

import SjAvatar from './SjAvatar.vue';
/** 通过文件标识读取短期地址，图片失败时只换签一次。 */
const props = withDefaults(
  defineProps<{
    /** 需要读取的头像文件。 */ fileId?: Int64String | null;
    /** 占位名称。 */ name: string;
    /** 固定像素尺寸。 */ size?: number;
  }>(),
  { fileId: null, size: 40 },
);
const emit = defineEmits<{
  /** 当前文件的可用地址，供用户菜单同步。 */ resolved: [
    url: string | undefined,
  ];
}>();
const { url, retry } = useSjFileAccessUrl(() => props.fileId);
watch(url, (value) => emit('resolved', value));
</script>
<template>
  <SjAvatar :name="name" :size="size" :src="url" @image-error="retry" />
</template>
