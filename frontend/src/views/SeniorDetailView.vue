<template>
  <div class="page-shell">
    <!-- Back header -->
    <header class="topbar">
      <div class="content-width" style="display:flex;align-items:center;gap:12px;">
        <button @click="router.back()" style="font-size:20px;background:transparent;color:var(--ink);padding:4px 8px;" aria-label="返回">←</button>
        <span style="font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ meta?.name ?? '学长详情' }}</span>
      </div>
    </header>

    <div v-if="loading" class="empty-state"><p>加载中…</p></div>
    <div v-else-if="!detail" class="empty-state">
      <p class="empty-state-icon">🔍</p>
      <h2>未找到该 Skill</h2>
    </div>

    <main v-else class="content-width py-6">
      <!-- Hero card -->
      <div class="detail-hero" style="display:flex;align-items:center;gap:16px;margin-bottom:24px;">
        <div class="senior-avatar detail-avatar">
          <img v-if="avatarSrc" :src="avatarSrc" :alt="meta?.name" />
          <span v-else>{{ meta?.name?.slice(0, 1) }}</span>
        </div>
        <div>
          <h1 style="font-size:22px;font-weight:700;letter-spacing:-.03em;">{{ meta?.name }}</h1>
          <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;">
            <span class="domain-tag">{{ meta?.domain }}</span>
            <span class="school-tag">{{ meta?.school }}</span>
            <span class="school-tag">{{ meta?.year }}届</span>
          </div>
          <p style="font-size:12px;color:var(--ink-soft);margin-top:6px;">{{ meta?.major }}</p>
        </div>
      </div>

      <!-- SKILL.md sections -->
      <section class="skill-section">
        <h2 class="skill-section-title">Skill 概览</h2>
        <pre class="skill-body">{{ detail.skillMd }}</pre>
      </section>

      <section class="skill-section">
        <h2 class="skill-section-title">工作风格</h2>
        <pre class="skill-body">{{ detail.workMd }}</pre>
      </section>

      <section class="skill-section">
        <h2 class="skill-section-title">人设</h2>
        <pre class="skill-body">{{ detail.personaMd }}</pre>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { SeniorSkillDetail } from '../types'
import { fetchSenior, avatarUrl } from '../services/seniorService'

const route = useRoute()
const router = useRouter()
const detail = ref<SeniorSkillDetail>()
const loading = ref(true)

const id = computed(() => route.params.id as string)
const meta = computed(() => detail.value?.index)
const avatarSrc = computed(() => meta.value ? avatarUrl(meta.value) : '')

onMounted(async () => {
  detail.value = await fetchSenior(id.value)
  loading.value = false
})
</script>
