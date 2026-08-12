<template>
  <div class="page-shell seniors-page">
    <TopBar action-label="+ 分享经历" @action="goDistill" />
    <main class="content-width">
      <div class="page-intro">
        <div>
          <p>SKILL LIBRARY</p>
          <h1>Skill 仓库</h1>
          <span>学长们分享的真实经验，点击卡片查看详情。</span>
        </div>
        <RouterLink to="/community" class="upload-button">+ 分享经历</RouterLink>
      </div>

      <nav class="domain-switcher" aria-label="按方向筛选">
        <button
          v-for="d in filterDomains"
          :key="d.id"
          :class="{ active: store.selectedDomain === d.id }"
          :style="{ '--domain-color': d.color, '--domain-ink': d.ink }"
          @click="store.selectedDomain = d.id"
        ><span>{{ d.glyph }}</span>{{ d.name }}</button>
      </nav>

      <div v-if="store.loading" class="feed-skeleton"><span v-for="n in 4" :key="n" /></div>
      <div v-else-if="filteredItems.length === 0" class="feed-empty">这个方向还没有学长 Skill。</div>
      <div v-else class="senior-grid">
        <RouterLink
          v-for="s in filteredItems"
          :key="s.id"
          :to="`/seniors/${s.id}`"
          class="senior-card"
          :style="{ '--domain-color': domainColorOf(s.domain), '--domain-ink': domainInkOf(s.domain) }"
        >
          <span class="senior-glyph">{{ (s.name || '?').charAt(0) }}</span>
          <div class="senior-info">
            <h2>{{ s.name }}</h2>
            <p>{{ s.school }}<template v-if="s.major"> · {{ s.major }}</template></p>
          </div>
          <span v-if="s.source === 'distilled'" class="senior-distilled">社区分享</span>
        </RouterLink>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useSeniorStore } from '../stores/seniorStore';
import { skillDomains } from '../domain';
import TopBar from '../components/common/TopBar.vue';

const store = useSeniorStore();
const router = useRouter();

const filterDomains = computed(() => {
  return skillDomains.map(d => ({ id: d.name, name: d.name, glyph: d.glyph, color: d.color, ink: d.ink }));
});

const filteredItems = computed(() => {
  if (!store.selectedDomain) return store.items;
  const dom = skillDomains.find(d => d.name === store.selectedDomain);
  if (!dom) return store.items;
  return store.items.filter(s => s.domain && dom.aliases.some(a => s.domain.includes(a)));
});

function domainColorOf(domain: string | undefined): string {
  if (!domain) return 'var(--ink)';
  const hit = skillDomains.find(d => d.aliases.some(a => domain.includes(a)));
  return hit?.color ?? 'var(--ink)';
}
function domainInkOf(domain: string | undefined): string {
  if (!domain) return 'var(--ink)';
  const hit = skillDomains.find(d => d.aliases.some(a => domain.includes(a)));
  return hit?.ink ?? 'var(--ink)';
}

function goDistill() {
  router.push('/community');
}

onMounted(async () => {
  await store.load();
});
</script>

<style scoped>
.senior-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; margin-top: 22px; }
.senior-card { display: flex; align-items: center; gap: 13px; padding: 15px 17px; background: var(--surface); border: 1px solid var(--line); border-radius: 8px; text-decoration: none; transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease; }
.senior-card:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(16,45,59,.08); border-color: var(--domain-color); }
.senior-glyph { width: 40px; height: 40px; display: grid; place-items: center; flex: 0 0 auto; border: 1px solid var(--domain-ink); border-radius: 4px; background: color-mix(in srgb, var(--domain-color) 18%, white); color: var(--domain-ink); font-size: 17px; font-weight: 800; }
.senior-info { min-width: 0; flex: 1; }
.senior-info h2 { margin: 0; font-size: 13px; color: var(--ink); }
.senior-info p { margin: 3px 0 0; color: var(--ink-soft); font-size: 10px; }
.senior-distilled { flex: 0 0 auto; padding: 2px 7px; border-radius: 4px; background: color-mix(in srgb, var(--domain-color) 15%, white); color: var(--domain-ink); font-size: 9px; font-weight: 800; }
.feed-skeleton { display: flex; flex-direction: column; gap: 12px; margin-top: 22px; }
.feed-skeleton span { height: 72px; border-radius: 8px; background: linear-gradient(90deg, var(--surface-soft) 25%, var(--surface) 50%, var(--surface-soft) 75%); background-size: 200% 100%; animation: shimmer 1.4s infinite; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
@media (max-width: 720px) { .senior-grid { grid-template-columns: 1fr; } }
</style>
