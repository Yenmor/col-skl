<template>
  <div class="page-shell senior-detail-page">
    <TopBar />
    <main class="content-width detail-layout">
      <div v-if="loading" class="feed-empty">加载中…</div>
      <div v-else-if="!detail" class="feed-empty">没有该学长。</div>

      <template v-else>
        <div class="page-intro">
          <div>
            <p>{{ detail.index.domain || 'SKILL' }}</p>
            <h1>{{ detail.index.name }}</h1>
            <span>{{ detail.index.school }}<template v-if="detail.index.major"> · {{ detail.index.major }}</template><template v-if="detail.index.year"> · {{ detail.index.year }}</template></span>
          </div>
          <span v-if="detail.index.source === 'distilled'" class="distilled-tag">社区发言整理</span>
        </div>

        <nav class="detail-tabs">
          <button v-for="t in availableTabs" :key="t" :class="{ active: tab === t }" @click="tab = t">{{ tabLabel(t) }}</button>
        </nav>

        <article v-if="tab === 'skill'" class="skill-document" :style="{ '--domain-color': heroColor, '--domain-ink': heroInk }">
          <pre class="skill-md">{{ detail.skillMd }}</pre>
        </article>
        <article v-else-if="tab === 'work'" class="skill-document" :style="{ '--domain-color': heroColor, '--domain-ink': heroInk }">
          <h3 class="doc-title">work.md</h3>
          <pre class="skill-md">{{ detail.workMd }}</pre>
        </article>
        <article v-else-if="tab === 'persona'" class="skill-document" :style="{ '--domain-color': heroColor, '--domain-ink': heroInk }">
          <h3 class="doc-title">persona.md</h3>
          <pre class="skill-md">{{ detail.personaMd }}</pre>
        </article>
        <article v-else-if="tab === 'meta'" class="skill-document" :style="{ '--domain-color': heroColor, '--domain-ink': heroInk }">
          <h3 class="doc-title">manifest.json</h3>
          <pre class="skill-md">{{ formatJson(detail.manifestJson) }}</pre>
          <h3 class="doc-title">meta.json</h3>
          <pre class="skill-md">{{ formatJson(detail.metaJson) }}</pre>
        </article>
        <div v-else-if="tab === 'fragments'" class="skill-document" :style="{ '--domain-color': heroColor, '--domain-ink': heroInk }">
          <div v-if="fragments.length === 0" class="feed-empty">还没有整理的经验片段。</div>
          <div v-else class="frag-list">
            <div v-for="f in fragments" :key="f.id" class="frag-card">
              <div class="frag-head">
                <span class="kind" :class="`kind-${f.kind}`">{{ f.kind }}</span>
                <span class="frag-time">{{ relTime(f.createdAt) }}</span>
              </div>
              <pre class="frag-content">{{ f.content }}</pre>
              <div v-if="f.tags.length" class="frag-tags"><span v-for="t in f.tags" :key="t">{{ t }}</span></div>
            </div>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import axios from 'axios';
import { seniorsApi } from '../services/api-v1';
import { skillDomains } from '../domain';
import type { SeniorSkillDetail } from '../types/index';
import type { SeniorFragmentDto } from '../types/api-v1';
import TopBar from '../components/common/TopBar.vue';

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

const domainHit = computed(() => {
  const d = detail.value?.index.domain ?? '';
  return skillDomains.find(sd => sd.aliases.some(a => d.includes(a)));
});
const heroColor = computed(() => domainHit.value?.color ?? 'var(--ink)');
const heroInk = computed(() => domainHit.value?.ink ?? 'var(--ink)');

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
.detail-layout { padding-top: 8px; }
.detail-tabs { display: flex; gap: 4px; margin-top: 4px; border-bottom: 1px solid var(--line); position: sticky; top: 60px; background: var(--bg); z-index: 5; }
.detail-tabs button { padding: 9px 16px; background: transparent; color: var(--ink-soft); font-size: 12px; font-weight: 700; border-radius: 6px 6px 0 0; }
.detail-tabs button.active { color: var(--focus); border-bottom: 2px solid var(--focus); }
.distilled-tag { align-self: flex-start; padding: 4px 10px; border-radius: 6px; background: var(--surface-soft); color: var(--ink-soft); font-size: 10px; font-weight: 800; }
.skill-document { margin-top: 20px; min-width: 0; padding: 24px clamp(22px,3vw,34px); background: var(--surface); border: 1px solid var(--line); border-radius: 8px; }
.doc-title { margin: 0 0 10px; color: var(--ink-soft); font-size: 12px; font-weight: 800; }
.skill-md { margin: 0; white-space: pre-wrap; word-break: break-word; color: #314e5b; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 13px; line-height: 1.7; }
.frag-list { display: flex; flex-direction: column; gap: 10px; }
.frag-card { padding: 12px 14px; border: 1px solid var(--line); border-radius: 8px; background: var(--bg); }
.frag-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.kind { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 9px; font-weight: 800; }
.kind-PERSONA { background: #fce4ec; color: #c2185b; }
.kind-WORK { background: #e3f2fd; color: #1976d2; }
.kind-MEMORY { background: #fff3e0; color: #ef6c00; }
.kind-OTHER { background: #f5f5f5; color: #666; }
.frag-time { color: var(--ink-faint); font-size: 10px; }
.frag-content { margin: 0 0 8px; white-space: pre-wrap; word-break: break-word; color: var(--ink); font-family: inherit; font-size: 12px; line-height: 1.7; }
.frag-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.frag-tags span { padding: 2px 6px; background: var(--surface-soft); border-radius: 4px; font-size: 9px; color: var(--ink-soft); }
@media (max-width: 720px) { .detail-tabs { top: 54px; overflow-x: auto; } }
</style>
