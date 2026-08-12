<template>
  <div class="page-shell profile-page">
    <TopBar />
    <main class="profile-layout content-width" :class="{ focused: expandedId }" @keydown.esc="closeExpanded">
      <header class="profile-header">
        <div class="profile-person"><span>{{ displayName.slice(0, 1) }}</span><div><small>PERSONAL SKILLS / 2026</small><h1>{{ displayName }}的能力画像</h1><p>{{ profileMeta }}</p></div></div>
        <div class="profile-total"><strong>{{ totalScore }}</strong><span>/ 100<br />综合掌握度</span></div>
      </header>

      <section class="profile-axis-panel" aria-label="四个核心方向能力评分">
        <div class="axis-scale" aria-hidden="true"><span>0</span><i /><span>50</span><i /><span>100</span></div>
        <div class="axis-list">
          <article
            v-for="(domain, index) in skillDomains"
            :key="domain.id"
            class="profile-axis"
            :data-axis-index="index"
            :class="{ expanded: expandedId === domain.id, muted: expandedId && expandedId !== domain.id }"
            :style="{ '--domain-color': domain.color, '--domain-ink': domain.ink, '--domain-tint': domain.tint, '--delay': `${index * 45}ms` }"
          >
            <button class="axis-main" type="button" :data-profile-axis="index" :aria-expanded="expandedId === domain.id" @click="toggle(domain.id)">
              <span class="axis-glyph">{{ domain.glyph }}</span>
              <span class="axis-name"><strong>{{ domain.name }}</strong><small>{{ domain.code }}</small></span>
              <i class="axis-track"><span /><span /><span /><span /><span /></i>
              <span class="axis-score">{{ scoreOf(domain.id) }}<small>%</small></span>
              <ChevronDown :size="17" />
            </button>
            <div class="axis-detail">
              <div>
                <header><p>{{ domain.description }}</p><RouterLink :to="`/community?domain=${domain.name}`">进入社区 <ArrowUpRight :size="14" /></RouterLink></header>
                <ul>
                  <li v-for="branch in branchesOf(domain.id)" :key="branch.name">
                    <span><strong>{{ branch.name }}</strong><small>{{ branch.note }}</small></span>
                    <i><b :style="{ width: `${branch.score}%` }" /></i>
                    <em>{{ branch.score }}</em>
                  </li>
                </ul>
                <div v-if="metricsOf(domain.id).length" class="axis-metrics">
                  <span v-for="m in metricsOf(domain.id)" :key="m">{{ m }}</span>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>

      <footer class="profile-footer"><BadgeCheck :size="17" /><span>能力分数基于你的社区发帖与互动计算</span><RouterLink to="/seniors">查看我的 Skills <ArrowUpRight :size="15" /></RouterLink></footer>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ArrowUpRight, BadgeCheck, ChevronDown } from '@lucide/vue'
import { skillDomains, domainById, type DomainId } from '../domain'
import TopBar from '../components/common/TopBar.vue'
import { useAbilitySpaceStore } from '../stores/abilitySpace'
import { usersApi } from '../services/api-v1'
import { requestAbilityProfile } from '../services/abilityProfile'

const abilitySpace = useAbilitySpaceStore()
const { expandedId } = storeToRefs(abilitySpace)

const displayName = ref('我')
const profile = ref<AbilityProfile | null>(null)

const totalScore = computed(() => profile.value?.total ?? 0)
const profileMeta = computed(() => {
  if (!profile.value) return '正在加载…'
  const sum = profile.value.domains.reduce((a, d) => a + d.posts, 0)
  return `参与了 ${sum} 条讨论`
})

interface AbilityProfile {
  userId: string
  total: number
  domains: Array<{
    id: string
    name: string
    score: number
    posts: number
    likes: number
    comments: number
    sitePosts: number
    seniors: number
    branches: Array<{ name: string; note: string; score: number }>
  }>
}

function scoreOf(id: string) {
  return profile.value?.domains.find(d => d.id === id)?.score ?? domainById(id).score
}

function branchesOf(id: string) {
  const remote = profile.value?.domains.find(d => d.id === id)?.branches
  if (remote && remote.length) return remote
  return domainById(id).branches.map(b => ({ ...b }))
}

function metricsOf(id: string): string[] {
  const d = profile.value?.domains.find(x => x.id === id)
  if (!d) return []
  return [
    `${d.sitePosts} 条讨论`,
    `${d.seniors} 位学长`,
    `我发布 ${d.posts} 条 · 获 ${d.likes} 赞`,
  ]
}

function toggle(id: DomainId) {
  abilitySpace.toggleExpanded(id)
}
function closeExpanded() {
  abilitySpace.closeExpanded()
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeExpanded()
}

onMounted(async () => {
  abilitySpace.prepareProfile()
  window.addEventListener('keydown', onKeydown)
  try {
    const [me, p] = await Promise.all([usersApi.getMe(), requestAbilityProfile()])
    displayName.value = me.displayName ?? '我'
    profile.value = p
  } catch {
    // 后端不可用时保持静态域数据兜底
  }
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>
