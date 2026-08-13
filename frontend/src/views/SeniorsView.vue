<template>
  <div class="page-shell catalog-page">
    <TopBar />

    <header class="catalog-hero content-width">
      <div class="catalog-hero-copy">
        <h1>Skill 仓库</h1>
        <p>按真实元数据查找校园经验，用任务召回结果判断匹配度，再单独核对七件套信任指标。</p>
      </div>
      <div class="catalog-cube-backdrop" aria-hidden="true">
        <span class="catalog-cube-aura" />
        <span v-for="index in 5" :key="index" class="catalog-cube-plane" :style="{ '--plane': index - 1 }" />
        <i class="catalog-cube-axis axis-x" />
        <i class="catalog-cube-axis axis-y" />
      </div>
      <div class="catalog-hero-actions">
        <RouterLink to="/seniors/studio"><Sparkles :size="16" />沉淀我的经验</RouterLink>
        <label class="upload-button" :class="{ disabled: uploading }">
          <LoaderCircle v-if="uploading" :size="17" class="spin" />
          <Upload v-else :size="17" />
          {{ uploading ? '正在校验' : '导入公开 Skill' }}
          <input type="file" accept=".zip,application/zip" :disabled="uploading" @change="upload" />
        </label>
      </div>
    </header>

    <div v-if="store.backendUnavailable" class="catalog-source-notice content-width" role="status">
      <WifiOff :size="17" />
      <span>
        <strong>{{ store.catalogSource === 'offline' ? '后端不可用，当前显示离线索引' : 'v1 仓库不可用，当前显示兼容索引' }}</strong>
        <small>只保留已有基础元数据；信任指标不会推算，详情、下载、导入和任务匹配可能不可用。</small>
      </span>
    </div>

    <section class="catalog-task-context content-width" aria-labelledby="task-context-title">
      <div class="catalog-task-copy">
        <span class="catalog-task-icon"><Target :size="20" /></span>
        <div>
          <h2 id="task-context-title">你现在要完成什么？</h2>
          <p>提交后调用真实召回接口。匹配度只说明任务相关性，不等于 Skill 信任度。</p>
        </div>
      </div>
      <form class="catalog-task-form" @submit.prevent="matchTask">
        <label class="catalog-task-input">
          <span class="sr-only">当前任务</span>
          <input
            ref="taskInput"
            v-model="store.taskContext"
            maxlength="500"
            placeholder="例如：第一次读论文，并准备十分钟组会汇报"
            @input="rememberTask"
          />
          <button type="button" title="清空当前任务" aria-label="清空当前任务" :disabled="!store.taskContext" @click="clearTask"><X :size="16" /></button>
        </label>
        <button class="catalog-match-button" type="submit" :disabled="store.matching || !store.taskContext.trim()">
          <LoaderCircle v-if="store.matching" :size="16" class="spin" />
          <SearchCheck v-else :size="16" />
          {{ store.matching ? '正在匹配' : '匹配 Skill' }}
        </button>
      </form>
      <p v-if="store.matchError" class="catalog-task-status is-error"><CircleAlert :size="14" />{{ store.matchError }}</p>
      <p v-else-if="store.matchedTask" class="catalog-task-status"><CheckCircle2 :size="14" />已按“{{ store.matchedTask }}”返回 {{ store.matches.size }} 条真实召回结果</p>
    </section>

    <section class="catalog-search content-width" aria-label="搜索 Skills">
      <Search :size="19" />
      <input v-model="store.query" placeholder="搜索名称、摘要、学校或后端标签" />
    </section>

    <div class="catalog-toolbar content-width">
      <div class="filter-row" role="tablist" aria-label="按成长层筛选">
        <button
          v-for="domain in domainFilters"
          :key="domain"
          class="filter-tab"
          :class="{ active: store.selectedDomain === domain }"
          type="button"
          @click="selectDomain(domain)"
        >{{ domain }}</button>
      </div>
      <div class="catalog-selects">
        <label v-if="store.tags.length"><Tag :size="14" /><span class="sr-only">标签筛选</span><select v-model="store.selectedTag"><option>全部</option><option v-for="tag in store.tags" :key="tag">{{ tag }}</option></select></label>
        <label v-if="store.schools.length"><MapPin :size="14" /><span class="sr-only">学校筛选</span><select v-model="store.selectedSchool"><option>全部</option><option v-for="school in store.schools" :key="school">{{ school }}</option></select></label>
        <label><ArrowDownWideNarrow :size="14" /><span class="sr-only">排序</span><select v-model="store.sort"><option value="updated">最近更新</option><option value="trust">按质量评分</option><option value="name">按名称</option></select></label>
      </div>
    </div>

    <main class="content-width catalog-content">
      <div class="catalog-summary">
        <span>{{ store.visibleItems.length }} 份可调用 Skill</span>
        <small>{{ store.hasTaskMatches ? '按真实任务匹配度排列；未召回项随后显示' : '按仓库真实元数据筛选' }}</small>
      </div>

      <div v-if="store.loading" class="catalog-skeleton" aria-label="正在加载"><span v-for="n in 3" :key="n" /></div>
      <div v-else-if="store.error" class="empty-state" role="alert"><CircleAlert :size="30" /><h2>Skill 仓库加载失败</h2><p>{{ store.error }}</p><button type="button" @click="store.load">重新加载</button></div>
      <div v-else-if="!store.visibleItems.length" class="empty-state"><Library :size="30" /><h2>没有匹配的 Skill</h2><p>调整搜索或筛选；也可以从自己的真实社区经历生成私有草稿。</p><RouterLink to="/seniors/studio">去沉淀我的经验</RouterLink></div>
      <div v-else class="skill-directory">
        <SeniorCard
          v-for="senior in store.visibleItems"
          :key="senior.id"
          :senior="senior"
          :match="store.matchedTask ? store.matches.get(senior.id) : undefined"
          @open="router.push(`/seniors/${senior.id}`)"
          @use="useSkill"
          @download="downloadSkill"
        />
      </div>
    </main>

    <Transition name="fade"><div v-if="toastMsg" class="toast" role="status">{{ toastMsg }}</div></Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDownWideNarrow,
  CheckCircle2,
  CircleAlert,
  Library,
  LoaderCircle,
  MapPin,
  Search,
  SearchCheck,
  Sparkles,
  Tag,
  Target,
  Upload,
  WifiOff,
  X,
} from '@lucide/vue'
import { skillDomains } from '../domain'
import { downloadSkillBundle, SkillApiError, uploadSeniorZip } from '../services/seniorService'
import { useSeniorStore } from '../stores/seniorStore'
import type { SeniorSkill } from '../skillsProfileTypes'
import SeniorCard from '../components/senior/SeniorCard.vue'
import TopBar from '../components/skillslab/SkillsProfileTopBar.vue'

const store = useSeniorStore()
const route = useRoute()
const router = useRouter()
const taskInput = ref<HTMLInputElement>()
const toastMsg = ref('')
const uploading = ref(false)
const domainFilters = computed(() => ['全部', ...skillDomains.map(domain => domain.name)])

onMounted(async () => {
  const domain = String(route.query.domain ?? '')
  if (domainFilters.value.includes(domain)) store.selectedDomain = domain
  const rememberedTask = sessionStorage.getItem('skillslab:task-context')
  if (rememberedTask) store.taskContext = rememberedTask
  await store.load()
})

function selectDomain(domain: string) {
  store.selectedDomain = domain
  void router.replace({ query: domain === '全部' ? {} : { domain } })
}

function rememberTask() {
  sessionStorage.setItem('skillslab:task-context', store.taskContext.trim())
}

function clearTask() {
  store.clearTaskMatch()
  sessionStorage.removeItem('skillslab:task-context')
}

async function matchTask() {
  rememberTask()
  await store.matchTask()
}

function useSkill(skill: SeniorSkill) {
  const task = store.taskContext.trim()
  if (!task) {
    notify('先写下具体任务，再调用这份 Skill')
    taskInput.value?.focus()
    return
  }
  sessionStorage.setItem('skillslab:task-context', task)
  sessionStorage.setItem('skillslab:question-draft', task)
  sessionStorage.setItem('skillslab:senior-id', skill.id)
  void router.push('/')
}

async function downloadSkill(skill: SeniorSkill) {
  try {
    notify('正在准备完整 Skill 包…')
    await downloadSkillBundle(skill.id, skill.name)
    notify('完整七件套已下载')
  } catch (cause) {
    notify(messageForError(cause, '下载失败，请稍后重试'))
  }
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const item = await uploadSeniorZip(file)
    notify(`“${item.name}”已作为公开 Skill 导入`)
    await store.load()
  } catch (cause) {
    notify(messageForError(cause, '导入失败，请检查 zip 包'))
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function messageForError(cause: unknown, fallback: string) {
  if (!(cause instanceof SkillApiError)) return fallback
  if (cause.code === 'SKILL_IMPORT_INVALID') return `导入失败：${cause.message}`
  if (cause.code === 'SKILL_FORBIDDEN') return '你没有访问这份私有 Skill 的权限'
  return cause.message || fallback
}

let toastTimer = 0
function notify(message: string) {
  toastMsg.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastMsg.value = '' }, 3200)
}
</script>
