<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const current = computed(() => {
  if (route.path.startsWith('/community')) return 'community'
  if (route.path.startsWith('/seniors')) return 'seniors'
  if (route.path.startsWith('/me')) return 'me'
  return 'home'
})
const dark = ref(false)

onMounted(() => {
  document.documentElement.lang = 'zh-CN'
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)')
  dark.value = prefersDark.matches
  prefersDark.addEventListener('change', event => { dark.value = event.matches })
})
</script>

<template>
  <div class="app-shell" :class="{ 'theme-dark': dark }">
    <aside class="desktop-rail" aria-label="主导航">
      <RouterLink to="/" class="rail-brand" aria-label="返回首页">
        <span class="brand-mark">S</span>
        <span class="rail-brand-copy">Skill<br /><em>共创场</em></span>
      </RouterLink>
      <div class="rail-rule" />
      <nav class="rail-links">
        <RouterLink to="/" class="rail-link" :class="{ active: current === 'home' }">
          <span class="nav-icon">⌂</span><span>对话</span>
        </RouterLink>
        <RouterLink to="/community" class="rail-link" :class="{ active: current === 'community' }">
          <span class="nav-icon">▤</span><span>社区</span>
        </RouterLink>
        <RouterLink to="/seniors" class="rail-link" :class="{ active: current === 'seniors' }">
          <span class="nav-icon">⌘</span><span>Skill 仓库</span>
        </RouterLink>
        <RouterLink to="/me" class="rail-link" :class="{ active: current === 'me' }">
          <span class="nav-icon">◎</span><span>我的</span>
        </RouterLink>
      </nav>
      <div class="rail-footer">
        <span class="rail-footer-dot" />
        <span>经验，换个方式留下来</span>
      </div>
    </aside>

    <main class="content-area">
      <RouterView />
    </main>

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
  </div>
</template>
