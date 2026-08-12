<template>
  <section class="me">
    <h1>我</h1>
    <div v-if="user" class="card">
      <div class="row">
        <span class="lbl">id</span>
        <code class="val">{{ user.id }}</code>
      </div>
      <div class="row">
        <span class="lbl">昵称</span>
        <input v-model="newName" class="inp" />
        <button @click="save" :disabled="saving" class="btn">保存</button>
      </div>
      <div class="row">
        <span class="lbl">角色</span>
        <span class="val">{{ user.role }}</span>
      </div>
      <div class="row">
        <span class="lbl">注册时间</span>
        <span class="val">{{ new Date(user.createdAt).toLocaleString('zh-CN') }}</span>
      </div>
    </div>
    <div v-else-if="loading" class="loading">加载中…</div>
    <div v-else class="empty">无法加载用户信息</div>

    <h2 style="margin-top: 24px">我的记忆</h2>
    <button @click="loadMemories" class="btn">刷新</button>
    <div v-if="memories.length === 0" class="empty">还没有记忆</div>
    <div v-else class="memories">
      <div v-for="m in memories" :key="m.memoryId" class="memory">
        <h4>{{ m.title || '未命名' }}</h4>
        <div class="tags">
          <span v-for="t in m.tags" :key="t" class="tag">{{ t }}</span>
        </div>
        <div class="time">{{ new Date(m.createdAt).toLocaleString('zh-CN') }}</div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { usersApi, memoriesApi } from '../services/api-v1';
import type { UserDto, ChatMemoryDto } from '../types/api-v1';

const user = ref<UserDto | null>(null);
const newName = ref('');
const saving = ref(false);
const loading = ref(true);
const memories = ref<ChatMemoryDto[]>([]);

async function load() {
  loading.value = true;
  try {
    const u = await usersApi.getMe();
    user.value = u;
    newName.value = u.displayName;
  } catch {
    user.value = null;
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!newName.value.trim()) return;
  saving.value = true;
  try {
    user.value = await usersApi.patchMe({ displayName: newName.value.trim() });
  } catch (e) {
    alert('保存失败：' + (e as Error).message);
  } finally {
    saving.value = false;
  }
}

async function loadMemories() {
  try {
    memories.value = await memoriesApi.listMine({ limit: 50 });
  } catch (e) {
    alert('加载失败：' + (e as Error).message);
  }
}

onMounted(async () => {
  await load();
  await loadMemories();
});
</script>

<style scoped>
.me { padding: 24px; max-width: 800px; margin: 0 auto; }
h1 { color: var(--ink); margin: 0 0 16px; }
.card { background: var(--surface); border-radius: 12px; padding: 16px; }
.row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--border); }
.row:last-child { border-bottom: none; }
.lbl { width: 80px; color: var(--ink-2); font-size: 13px; }
.val { color: var(--ink); }
code.val { font-family: ui-monospace, monospace; font-size: 12px; }
.inp { flex: 1; padding: 6px 10px; border-radius: 6px; border: 1px solid var(--border); background: var(--paper); color: var(--ink); }
.btn { padding: 6px 14px; border-radius: 6px; background: var(--pink); color: white; border: none; cursor: pointer; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.loading, .empty { padding: 20px; text-align: center; color: var(--ink-2); }
.memories { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.memory { background: var(--surface); border-radius: 8px; padding: 12px; }
.memory h4 { margin: 0 0 4px; color: var(--ink); }
.tags { display: flex; gap: 4px; flex-wrap: wrap; }
.tag { padding: 2px 6px; background: var(--surface-2); border-radius: 4px; font-size: 11px; color: var(--ink-2); }
.time { color: var(--ink-2); font-size: 12px; margin-top: 4px; }
@media (max-width: 720px) {
  .me { padding: 16px; }
}
</style>
