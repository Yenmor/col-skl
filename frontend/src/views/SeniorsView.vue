<template>
  <section class="seniors">
    <header class="head">
      <div>
        <h1>学长 Skill 库</h1>
        <p class="hint">点击卡片查看完整 Skill</p>
      </div>
      <router-link to="/community#distill-user" class="btn-primary">+ 蒸馏新 Skill</router-link>
    </header>

    <div v-if="store.loading" class="loading">加载中…</div>
    <div v-else-if="store.items.length === 0" class="empty">还没有学长数据。上传一份七件套 zip 试试。</div>
    <div v-else class="grid">
      <article v-for="s in store.items" :key="s.id" class="card" :style="{ borderTop: `4px solid ${pickColor(s.id)}` }">
        <div class="card-head">
          <h3>{{ s.name }}</h3>
          <span v-if="s.source === 'distilled'" class="badge distilled">蒸馏</span>
        </div>
        <div class="meta">
          <span>{{ s.school }}</span>
          <span v-if="s.major">· {{ s.major }}</span>
          <span v-if="s.year">· {{ s.year }}</span>
        </div>
        <span v-if="s.domain" class="domain">{{ s.domain }}</span>
        <div class="actions">
          <router-link :to="`/seniors/${s.id}`" class="link">查看 Skill</router-link>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useSeniorStore } from '../stores/seniorStore';

const store = useSeniorStore();
const PALETTE = ['#fde0e6', '#dceafd', '#e5f4dc', '#f9eedc', '#ece4fa', '#fde6d4'];

function pickColor(id: string): string {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0;
  return PALETTE[Math.abs(h) % PALETTE.length];
}

onMounted(async () => {
  await store.load();
});
</script>

<style scoped>
.seniors { padding: 24px; max-width: 1000px; margin: 0 auto; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.head h1 { font-size: 26px; margin: 0 0 4px; color: var(--ink); }
.hint { color: var(--ink-2); margin: 0; }
.btn-primary { padding: 8px 16px; border-radius: 8px; background: var(--pink); color: white; text-decoration: none; font-size: 14px; white-space: nowrap; }
.btn-primary:hover { background: var(--pink-2, var(--pink)); filter: brightness(0.95); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.card { background: var(--surface); border-radius: 12px; padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-head h3 { margin: 0 0 4px; font-size: 16px; color: var(--ink); }
.badge { padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.badge.distilled { background: #fce4ec; color: #c2185b; }
.meta { color: var(--ink-2); font-size: 12px; margin-bottom: 6px; }
.domain { display: inline-block; padding: 2px 8px; background: var(--surface-2); border-radius: 4px; font-size: 12px; color: var(--ink-2); }
.actions { display: flex; margin-top: 12px; }
.link { color: var(--pink); text-decoration: none; font-size: 14px; }
.link:hover { text-decoration: underline; }
.loading, .empty { padding: 40px; text-align: center; color: var(--ink-2); }
@media (max-width: 720px) {
  .seniors { padding: 16px; }
  .head h1 { font-size: 20px; }
}
</style>
