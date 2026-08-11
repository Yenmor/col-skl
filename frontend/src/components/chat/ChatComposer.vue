<template>
  <form class="composer" @submit.prevent="submit">
    <div class="composer-inner">
      <textarea v-model="text" rows="1" :disabled="loading" :placeholder="placeholder" @keydown.enter.exact.prevent="submit" />
      <button class="composer-submit" type="submit" :disabled="loading || !text.trim()" aria-label="发送">{{ loading ? '…' : '↑' }}</button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const props = defineProps<{ loading?: boolean; placeholder?: string }>()
const emit = defineEmits<{ (e: 'submit', text: string): void }>()
const text = ref('')
function submit() {
  if (!text.value.trim() || props.loading) return
  emit('submit', text.value.trim())
  text.value = ''
}
</script>
