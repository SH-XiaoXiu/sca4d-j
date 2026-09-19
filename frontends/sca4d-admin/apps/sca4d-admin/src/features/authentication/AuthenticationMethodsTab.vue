<script setup lang="ts">
import PasskeyCredentialSection from './PasskeyCredentialSection.vue';
import TotpCredentialSection from './TotpCredentialSection.vue';

/** Passkey registration 使用的账号显示名称。 */
defineProps<{ displayName: string }>();
</script>

<template>
  <div class="w-full">
    <TotpCredentialSection />
    <PasskeyCredentialSection :display-name="displayName" />
  </div>
</template>
