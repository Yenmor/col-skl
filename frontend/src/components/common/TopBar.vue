<template>
  <header class="topbar">
    <div class="content-width topbar-inner">
      <div class="topbar-title">
        <span class="topbar-mark" :style="{ background: markColor }" />
        <span>
          <strong>{{ section }}</strong>
          <small>{{ title }}</small>
        </span>
      </div>
      <div class="topbar-actions">
        <button v-if="actionLabel" class="btn-text" type="button" @click="$emit('action')">
          {{ actionLabel }}
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { skillDomains } from '../../domain'

defineProps<{ actionLabel?: string }>()
defineEmits<{ (e: 'action'): void }>()

const route = useRoute()

const section = computed(() => {
  if (route.path.startsWith('/community')) return '社区'
  if (route.path.startsWith('/seniors')) return 'Skill 仓库'
  if (route.path.startsWith('/me')) return '能力画像'
  return '问经验'
})

const title = computed(() => {
  if (route.path.startsWith('/seniors/')) return '学长 Skill'
  if (route.path === '/') return '向学长学姐提问'
  return '校园能力实验室'
})

const markColor = computed(() => {
  const domain = skillDomains.find(d => d.name === route.query.domain)
  return domain?.color ?? 'var(--ink)'
})
</script>
