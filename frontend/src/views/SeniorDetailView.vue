<template>
  <section class="detail" v-if="detail">
    <header class="hero" :style="{ background: `linear-gradient(135deg, ${heroColor} 0%, ${heroColorDark} 100%)` }">
      <button @click="$router.back()" class="back-btn" aria-label="返回">← 返回</button>
      <div class="hero-content">
        <span v-if="detail.index.source === 'distilled'" class="badge">蒸馏</span>
        <h1>{{ detail.index.name }}</h1>
        <div class="hero-meta">
          <span>{{ detail.index.school }}</span>
          <span v-if="detail.index.major">· {{ detail.index.major }}</span>
          <span v-if="detail.index.year">· {{ detail.index.year }}</span>
        </div>
        <span v-if="detail.index.domain" class="domain-pill">#{{ detail.index.domain }}</span>
      </div>
    </header>

    <div class="tabs">
      <button v-for="t in availableTabs" :key="t" :class="{ active: tab === t }" @click="tab = t">
        {{ tabLabel(t) }}
      </button>
    </div>

    <!-- SKILL tab -->
    <div v-if="tab === 'skill'" class="tab-content">
      <article v-if="detail.skillMd" class="md-block">
        <pre class="md">{{ detail.skillMd }}</pre>
      </article>
    </div>

    <!-- 分轨 tab -->
    <div v-else-if="tab === 'work'" class="tab-content">
      <article v-if="detail.workMd" class="md-block">
        <h3>work.md</h3>
        <pre class="md">{{ detail.workMd }}</pre>
      </article>
      <article v-if="detail.manifestJson" class="md-block">
        <h3>work_skill.md</h3>
        <pre class="md">---&#10;name: work&#10;description: 工作能力子 Skill。&#10;---&#10;&#10;{{ detail.workMd }}</pre>
      </article>
    </div>

    <div v-else-if="tab === 'persona'" class="tab-content">
      <article v-if="detail.personaMd" class="md-block">
        <h3>persona.md</h3>
        <pre class="md">{{ detail.personaMd }}</pre>
      </article>
    </div>

    <!-- 元数据 tab -->
    <div v-else-if="tab === 'meta'" class="tab-content">
      <article v-if="detail.manifestJson" class="md-block">
        <h3>manifest.json</h3>
        <pre class="md">{{ formatJson(detail.manifestJson) }}</pre>
      </article>
      <article v-if="detail.metaJson" class="md-block">
        <h3>meta.json</h3>
        <pre class="md">{{ formatJson(detail.metaJson) }}</pre>
      </article>
    </div>

    <!-- 片段 tab -->
    <div v-else-if="tab === 'fragments'" class="tab-content">
      <div v-if="fragments.length === 0" class="empty">还没有蒸馏片段。</div>
      <div v-else class="fragments-list">
        <div v-for="f in fragments" :key="f.id" class="frag-card">
          <div class="frag-head">
            <span class="kind" :class="`kind-${f.kind}`">{{ f.kind }}</span>
            <span class="frag-time">{{ relTime(f.createdAt) }}</span>
          </div>
          <pre class="frag-content">{{ f.content }}</pre>
          <div v-if="f.tags.length" class="frag-tags">
            <span v-for="t in f.tags" :key="t" class="tag">{{ t }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section v-else-if="loading" class="loading">加载中…</section>
  <section v-else class="empty">没有该学长。</section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue';
import { useRoute } from 'vue-router';
import axios from 'axios';
import { seniorsApi } from '../services/api-v1';
import type { SeniorSkillDetail } from '../types/index';
import type { SeniorFragmentDto } from '../types/api-v1';

const route = useRoute();
const detail = ref<SeniorSkillDetail | null>(null);
const loading = ref(true);
const tab = ref<'skill' | 'work' | 'persona' | 'meta' | 'fragments'>('skill');
const fragments = ref<SeniorFragmentDto[]>([]);

const availableTabs = computed(() => {
  if (!detail.value) return [] as Array<'skill' | 'work' | 'persona' | 'meta' | 'fragments'>;
  const ts: Array<'skill' | 'work' | 'persona' | 'meta' | 'fragments'> = [];
  if (detail.value.skillMd) ts.push('skill');
  if (detail.value.workMd) ts.push('work');
  if (detail.value.personaMd) ts.push('persona');
  if (detail.value.manifestJson || detail.value.metaJson) ts.push('meta');
  ts.push('fragments');
  return ts;
});

function tabLabel(t: string) {
  return ({ skill: 'SKILL', work: 'Work', persona: 'Persona', meta: '元数据', fragments: '片段' } as Record<string, string>)[t] || t;
}

const heroColor = computed(() => {
  if (!detail.value) return '#fde0e6';
  let h = 0;
  for (const c of detail.value.index.id) h = (h * 31 + c.charCodeAt(0)) | 0;
  const palette = ['#fde0e6', '#dceafd', '#e5f4dc', '#f9eedc', '#ece4fa', '#fde6d4', '#d4f0fa'];
  return palette[Math.abs(h) % palette.length];
});
const heroColorDark = computed(() => shade(heroColor.value, 0.75));

function shade(hex: string, factor: number): string {
  const m = hex.replace('#', '').match(/.{2}/g);
  if (!m) return hex;
  const [r, g, b] = m.map(x => Math.max(0, Math.min(255, Math.round(parseInt(x, 16) * factor))));
  return `rgb(${r}, ${g}, ${b})`;
}

async function loadDetail() {
  loading.value = true;
  fragments.value = [];
  tab.value = 'skill';
  const id = String(route.params.id);
  try {
    const res = await axios.get(`/api/seniors/${id}`);
    detail.value = res.data as SeniorSkillDetail;
  } catch {
    detail.value = null;
  }
  // 异步加载 fragments
  try {
    fragments.value = await seniorsApi.listFragments(id, { limit: 50 });
  } catch {
    fragments.value = [];
  }
  loading.value = false;
}

function formatJson(s: string): string {
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
}

function relTime(iso: string): string {
  const t = new Date(iso).getTime();
  const dt = Date.now() - t;
  if (dt < 60000) return '刚刚';
  if (dt < 3600000) return `${Math.floor(dt / 60000)} 分钟前`;
  if (dt < 86400000) return `${Math.floor(dt / 3600000)} 小时前`;
  return new Date(iso).toLocaleDateString('zh-CN');
}

onMounted(loadDetail);
watch(() => route.params.id, loadDetail);
</script>

<style scoped>
.detail { max-width: 800px; margin: 0 auto; padding-bottom: 80px; }

/* 沉浸式 hero */
.hero { padding: 20px 24px 40px; color: var(--ink); position: relative; }
.back-btn { background: rgba(255,255,255,0.7); border: 0; border-radius: 999px; padding: 6px 14px; cursor: pointer; font-size: 14px; color: var(--ink); margin-bottom: 24px; backdrop-filter: blur(4px); }
.back-btn:hover { background: rgba(255,255,255,0.95); }
.hero-content h1 { font-size: 32px; font-weight: 800; margin: 0 0 8px; line-height: 1.2; text-shadow: 0 2px 4px rgba(255,255,255,0.4); }
.hero-meta { font-size: 14px; opacity: 0.85; margin-bottom: 12px; }
.domain-pill { display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.6); border-radius: 999px; font-size: 13px; font-weight: 600; }
.badge { display: inline-block; padding: 3px 10px; background: #fce4ec; color: #c2185b; border-radius: 4px; font-size: 12px; font-weight: 600; margin-bottom: 12px; }

/* tab */
.tabs { display: flex; gap: 4px; padding: 12px 24px 0; background: var(--paper); position: sticky; top: 0; z-index: 5; border-bottom: 1px solid var(--border); }
.tabs button { padding: 8px 16px; border: 0; background: transparent; cursor: pointer; color: var(--ink-2); border-radius: 8px 8px 0 0; font-size: 14px; font-weight: 500; }
.tabs button.active { color: var(--pink); border-bottom: 2px solid var(--pink); }

.tab-content { padding: 20px 24px; }

/* md 块 */
.md-block { background: var(--surface); border-radius: 12px; padding: 16px 20px; margin-bottom: 14px; }
.md-block h3 { margin: 0 0 10px; font-size: 14px; color: var(--ink-2); font-weight: 600; }
.md { white-space: pre-wrap; word-break: break-word; color: var(--ink); font-family: ui-monospace, SFMono-Regular, "SF Mono", Consolas, monospace; font-size: 13px; line-height: 1.7; }

/* fragments */
.fragments-list { display: flex; flex-direction: column; gap: 10px; }
.frag-card { background: var(--surface); border-radius: 12px; padding: 12px 16px; border: 1px solid var(--border); }
.frag-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.kind { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.kind-PERSONA { background: #fce4ec; color: #c2185b; }
.kind-WORK { background: #e3f2fd; color: #1976d2; }
.kind-MEMORY { background: #fff3e0; color: #ef6c00; }
.kind-OTHER { background: #f5f5f5; color: #666; }
.frag-time { color: var(--ink-2); font-size: 11px; }
.frag-content { white-space: pre-wrap; word-break: break-word; color: var(--ink); font-family: inherit; font-size: 13px; line-height: 1.6; margin: 0 0 8px; }
.frag-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.tag { padding: 2px 6px; background: var(--surface-2); border-radius: 4px; font-size: 11px; color: var(--ink-2); }

.loading, .empty { padding: 60px 20px; text-align: center; color: var(--ink-2); }

@media (max-width: 720px) {
  .hero { padding: 16px 16px 32px; }
  .hero-content h1 { font-size: 24px; }
  .tabs { padding: 8px 12px 0; overflow-x: auto; }
  .tab-content { padding: 16px; }
}
</style>
