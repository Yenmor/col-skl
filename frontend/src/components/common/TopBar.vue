<template>
  <header class="topbar">
    <div class="content-width topbar-inner">
      <div class="topbar-title">
        <span class="topbar-kicker">{{ section }}</span>
        <span class="topbar-divider">/</span>
        <span>{{ title }}</span>
      </div>
      <div class="topbar-actions">
        <span class="hidden sm:inline tiny">大学经验，换个方式留下来</span>
        <button class="btn-text" type="button" @click="$emit('action')">＋ 发布</button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

defineEmits<{ (e: 'action'): void }>()
const route = useRoute()
const section = computed(() => route.path.startsWith('/community') ? '社区' : route.path.startsWith('/seniors') ? 'Skill 仓库' : route.path.startsWith('/me') ? '我的' : '对话')
const title = computed(() => route.path.startsWith('/seniors/') ? '学长 Skill' : route.path === '/' ? '向学长学姐提问' : '大学经验，换个方式留下来')
</script>
