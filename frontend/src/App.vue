<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Box, Library, MessageCircle, MessagesSquare, UserRound } from '@lucide/vue'
import AbilityCube from './components/cube/AbilityCube.vue'
import { useAbilitySpaceStore } from './stores/abilitySpace'

const route = useRoute()
const abilitySpace = useAbilitySpaceStore()
const current = computed(() => {
  if (route.path.startsWith('/community')) return 'community'
  if (route.path.startsWith('/seniors')) return 'seniors'
  if (route.path.startsWith('/me')) return 'me'
  return 'home'
})
onMounted(() => {
  document.documentElement.lang = 'zh-CN'
  // 预加载能力画像：用户点进 /me 前分数已就绪，立方体 morph 直接收缩到真实值
  void abilitySpace.preloadProfile()
})
</script>

<template>
  <div class="app-shell" :class="`app-${current}`">
    <AbilityCube />

    <header v-if="current === 'home'" class="home-chrome">
      <RouterLink to="/" class="home-brand" aria-label="SkillsLab 首页">
        <span class="brand-mark" aria-hidden="true"><i /><i /><i /><i /></span>
        <strong>SkillsLab</strong>
      </RouterLink>
      <nav class="home-primary-nav" aria-label="主导航">
        <button type="button" :class="{ active: abilitySpace.homeMode === 'chat' }" @click="abilitySpace.showChat()">
          <MessageCircle :size="16" /><span>问经验</span>
        </button>
        <RouterLink to="/seniors"><Library :size="16" /><span>Skills 库</span></RouterLink>
        <RouterLink to="/community"><MessagesSquare :size="16" /><span>社区</span></RouterLink>
        <button type="button" :class="{ active: abilitySpace.homeMode === 'cube' }" @click="abilitySpace.showCube()">
          <Box :size="16" /><span>能力立方体</span>
        </button>
        <RouterLink to="/me" class="home-avatar" title="能力画像" aria-label="打开能力画像" @click="abilitySpace.prepareProfile()"><span>我</span></RouterLink>
      </nav>
    </header>

    <aside class="desktop-rail" aria-label="主导航">
      <RouterLink to="/" class="rail-brand" aria-label="返回首页">
        <span class="brand-mark" aria-hidden="true"><i /><i /><i /><i /></span>
        <span class="rail-brand-copy"><strong>SkillsLab</strong><small>校园能力实验室</small></span>
      </RouterLink>
      <nav class="rail-links">
        <RouterLink to="/" class="rail-link" :class="{ active: current === 'home' }">
          <Box :size="18" /><span>能力空间</span>
        </RouterLink>
        <RouterLink to="/community" class="rail-link" :class="{ active: current === 'community' }">
          <MessageCircle :size="18" /><span>社区</span>
        </RouterLink>
        <RouterLink to="/seniors" class="rail-link" :class="{ active: current === 'seniors' }">
          <Library :size="18" /><span>Skill 仓库</span>
        </RouterLink>
        <RouterLink to="/me" class="rail-link" :class="{ active: current === 'me' }" @click="abilitySpace.prepareProfile()">
          <UserRound :size="18" /><span>能力画像</span>
        </RouterLink>
      </nav>
      <div class="rail-footer">
        <RouterLink to="/me" class="rail-profile" @click="abilitySpace.prepareProfile()">
          <span>我</span>
          <span><strong>我的主页</strong><small>能力画像</small></span>
        </RouterLink>
      </div>
    </aside>

    <main class="content-area">
      <RouterView v-slot="{ Component }">
        <Transition :name="current === 'me' || route.path === '/' ? 'route-soft' : 'route'" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <nav class="bottom-nav" aria-label="主导航">
      <RouterLink to="/" class="bottom-nav-item" :class="{ active: current === 'home' && abilitySpace.homeMode === 'chat' }" @click="abilitySpace.showChat()">
        <MessageCircle :size="20" /><span>问经验</span>
      </RouterLink>
      <button v-if="current === 'home'" type="button" class="bottom-nav-item" :class="{ active: abilitySpace.homeMode === 'cube' }" @click="abilitySpace.showCube()">
        <Box :size="20" /><span>立方体</span>
      </button>
      <RouterLink to="/community" class="bottom-nav-item" :class="{ active: current === 'community' }">
        <MessageCircle :size="20" /><span>社区</span>
      </RouterLink>
      <RouterLink to="/seniors" class="bottom-nav-item" :class="{ active: current === 'seniors' }">
        <Library :size="20" /><span>Skill</span>
      </RouterLink>
      <RouterLink to="/me" class="bottom-nav-item" :class="{ active: current === 'me' }" @click="abilitySpace.prepareProfile()">
        <UserRound :size="20" /><span>画像</span>
      </RouterLink>
    </nav>
  </div>
</template>
