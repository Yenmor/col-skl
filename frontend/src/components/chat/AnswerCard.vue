<template>
  <article class="answer-card">
    <div class="answer-header">
      <div class="senior-avatar" :style="{ width: '38px', height: '38px' }">
        <img v-if="avatar" :src="avatar" :alt="answer.name" />
        <span v-else>{{ answer.name.slice(0, 1) }}</span>
      </div>
      <div class="min-w-0">
        <h3 class="truncate">{{ answer.name }}</h3>
        <p class="truncate">{{ answer.school }} · {{ answer.year }} 届</p>
      </div>
    </div>
    <div class="answer-content">{{ answer.content }}</div>
    <RouterLink :to="`/seniors/${answer.seniorId}`" class="answer-link">查看这位学长的 Skill →</RouterLink>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SeniorAnswer } from '../../types'
import { avatarUrl } from '../../services/seniorService'

const props = defineProps<{ answer: SeniorAnswer }>()
const avatar = computed(() => avatarUrl({
  id: props.answer.seniorId,
  name: props.answer.name,
  school: props.answer.school,
  major: props.answer.major,
  year: props.answer.year,
  domain: '',
  avatarFilename: '',
  source: '',
  createdAt: '',
}))
</script>
