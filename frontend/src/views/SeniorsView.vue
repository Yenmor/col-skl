<template>
  <div class="page-shell">
    <TopBar />

    <!-- Domain filter -->
    <div class="filter-row content-width">
      <button
        v-for="d in DOMAINS"
        :key="d"
        class="filter-tab"
        :class="{ active: store.selectedDomain === d }"
        @click="selectDomain(d)"
      >{{ d }}</button>
    </div>

    <!-- Upload strip -->
    <div class="content-width" style="padding:10px 0;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line);">
      <span class="tiny">{{ store.items.length }} 位学长学姐</span>
      <label style="color:var(--pink);font-size:13px;cursor:pointer;font-weight:600;">
        ＋ 上传 Skill
        <input type="file" accept=".zip" style="display:none;" @change="upload" />
      </label>
    </div>

    <main class="content-width py-5">
      <div v-if="store.loading" class="empty-state"><p>加载中…</p></div>
      <div v-else-if="!store.items.length" class="empty-state">
        <p class="empty-state-icon">📚</p>
        <h2>暂无 Skill</h2>
        <p>上传第一个学长.Skill zip 包试试看</p>
      </div>
      <div v-else class="waterfall">
        <SeniorCard
          v-for="(s, i) in store.items"
          :key="s.id"
          :senior="s"
          :index="i"
          @open="router.push(`/seniors/${s.id}`)"
        />
      </div>
    </main>

    <Transition name="fade">
      <div v-if="toastMsg" class="toast">{{ toastMsg }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSeniorStore } from '../stores/seniorStore'
import { DOMAINS } from '../types'
import { uploadSeniorZip } from '../services/seniorService'
import TopBar from '../components/common/TopBar.vue'
import SeniorCard from '../components/senior/SeniorCard.vue'

const store = useSeniorStore()
const router = useRouter()
const toastMsg = ref('')

onMounted(() => store.load())

function selectDomain(d: string) {
  store.selectedDomain = d
  store.load()
}

async function upload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  toastMsg.value = '上传中…'
  try {
    await uploadSeniorZip(file)
    toastMsg.value = '✓ 上传成功，已入库'
    store.load()
  } catch {
    toastMsg.value = '上传失败，请检查 zip 结构'
  }
  setTimeout(() => { toastMsg.value = '' }, 3000)
}
</script>
