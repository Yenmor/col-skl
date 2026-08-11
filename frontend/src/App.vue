<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const current = computed(() => {
  if (route.path.startsWith('/community')) return 'community'
  if (route.path.startsWith('/seniors')) return 'seniors'
  if (route.path.startsWith('/me')) return 'me'
  return 'home'
})

onMounted(() => {
  document.documentElement.lang = 'zh-CN'
})
</script>

<template>
  <RouterView />

  <nav class="bottom-nav" aria-label="主导航">
    <RouterLink to="/" class="bottom-nav-item" :class="{ active: current === 'home' }">
      <span class="nav-icon">⌂</span><span>对话</span>
    </RouterLink>
    <RouterLink to="/community" class="bottom-nav-item" :class="{ active: current === 'community' }">
      <span class="nav-icon">▤</span><span>社区</span>
    </RouterLink>
    <RouterLink to="/seniors" class="floating-create" aria-label="Skill 仓库">S</RouterLink>
    <RouterLink to="/seniors" class="bottom-nav-item" :class="{ active: current === 'seniors' }">
      <span class="nav-icon">⌘</span><span>Skill</span>
    </RouterLink>
    <RouterLink to="/me" class="bottom-nav-item" :class="{ active: current === 'me' }">
      <span class="nav-icon">◎</span><span>我的</span>
    </RouterLink>
  </nav>
</template>
