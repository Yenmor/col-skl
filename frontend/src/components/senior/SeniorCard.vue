<template>
  <article class="senior-card cursor-pointer" @click="$emit('open', senior.id)">
    <div class="senior-card-cover" :style="{ background: coverColor }">
      <div class="senior-avatar">
        <img v-if="avatar" :src="avatar" :alt="senior.name" />
        <span v-else>{{ senior.name.slice(0, 1) }}</span>
      </div>
      <span class="domain-tag">{{ senior.domain }}</span>
    </div>
    <div class="senior-card-body">
      <div class="senior-name">{{ senior.name }}</div>
      <div class="senior-major">{{ senior.major }}</div>
      <div class="mt-2 flex flex-wrap gap-1.5">
        <span class="school-tag">{{ senior.school }}</span>
        <span class="school-tag">{{ senior.year }} 届</span>
      </div>
      <div class="senior-meta">
        <span>{{ senior.source === 'distilled' ? '蒸馏自公开内容' : '直接上传' }}</span>
        <span class="pink">查看 →</span>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SeniorSkill } from '../../types'
import { avatarUrl } from '../../services/seniorService'

const props = defineProps<{ senior: SeniorSkill; index?: number }>()
defineEmits<{ (e: 'open', id: string): void }>()
const palette = ['#fde0e6', '#dceafd', '#e5f4dc', '#f9eedc', '#ece4fa']
const coverColor = computed(() => palette[(props.index ?? 0) % palette.length])
const avatar = computed(() => avatarUrl(props.senior))
</script>
